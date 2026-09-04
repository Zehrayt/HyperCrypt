package com.zehrayt.hypercrypt.controller;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import com.zehrayt.hypercrypt.exception.InvalidRuleException;
import com.zehrayt.hypercrypt.service.RuleParserService;
import com.zehrayt.hypercrypt.service.RuleSuggestionEngine;
import com.zehrayt.hypercrypt.verification.AxiomVerifier;
import com.zehrayt.hypercrypt.verification.SymbolicVerifierService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class VerificationController {

    private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

    private final RuleParserService ruleParserService;
    private final SymbolicVerifierService symbolicVerifierService;
    private final RuleSuggestionEngine ruleSuggestionEngine;
    private final MessageSource messageSource;

    private String noSuggestionFoundMessage() {
    return messageSource.getMessage("verify.noSuggestionFound", null, LocaleContextHolder.getLocale());
}

    @Autowired
    public VerificationController(RuleParserService ruleParserService,
                                SymbolicVerifierService symbolicVerifierService,
                                RuleSuggestionEngine ruleSuggestionEngine,
                            MessageSource messageSource) {
        this.ruleParserService = ruleParserService;
        this.symbolicVerifierService = symbolicVerifierService;
        this.ruleSuggestionEngine = ruleSuggestionEngine;
        this.messageSource = messageSource;
    }

    private String formatVerifiedSuggestions(List<RuleSuggestionEngine.Suggestion> verified) {
        if (verified.isEmpty()) {
            return noSuggestionFoundMessage();
        }
        return verified.stream().map(s -> s.explanation).collect(Collectors.joining(" "));
    }

    // DoS koruması: AxiomVerifier'ın birleşme/dağılma testleri O(n^3)
    // çalışır. Sınırsız büyüklükte bir baseSet kabul etmek, sunucu CPU'sunu
    // kilitleyebilecek bir hizmet engelleme (DoS) vektörü oluşturur.
    private static final int MAX_BASE_SET_SIZE = 100;

    // NOT: "(R,+) is an abelian group" varsayımı artık burada bir 400 hatasıyla
    // KISITLANMIYOR. Bunun yerine AxiomVerifier.verifyAdditiveGroupAxioms() bu koşulu
    // verilen baseSet için GERÇEKTEN doğruluyor ve sonucu VerificationResult üzerinden
    // (highestStructure alanında) şeffafça raporluyor. Böylece kullanıcı istediği
    // herhangi bir tam sayı kümesini deneyebilir; küme gerçekten geçerli bir abelyen
    // grup oluşturmuyorsa (örn. {2,5,9}), sonuç bunu açıkça ve doğru şekilde belirtir.

    public static class VerificationRequest {
        public Set<Integer> baseSet; // Sonlu küme
        public String domain;        // Sonsuz küme
        public String rule;
    }

    @PostMapping("/verify")
    public ResponseEntity<Object> verifyStructure(@RequestBody VerificationRequest request) {
        try {
            if (request.domain != null && !request.domain.isBlank()) {
                VerificationResult result = symbolicVerifierService.verifySymbolically(request.rule, request.domain);
                return ResponseEntity.ok(result);
            } 
            else if (request.baseSet != null && !request.baseSet.isEmpty()) {

                // 0. Adım: Boyut sınırı kontrolü (DoS koruması).
                if (request.baseSet.size() > MAX_BASE_SET_SIZE) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", messageSource.getMessage("verify.error.baseSetTooLarge",
                            new Object[]{request.baseSet.size(), MAX_BASE_SET_SIZE},
                            LocaleContextHolder.getLocale())
                    ));
                }

                // 1. Kuralın içinde standart çarpma (*) içerip içermediğini kontrol et.
                if (request.rule == null || !request.rule.contains("*")) {
                    String suggestionText = noSuggestionFoundMessage();
                    if (request.rule != null) {
                        suggestionText = formatVerifiedSuggestions(
                            ruleSuggestionEngine.suggest(request.rule, request.baseSet));
                    }

                    return ResponseEntity.badRequest().body(Map.of(
                        "error", messageSource.getMessage("verify.error.missingMultiplication", null, LocaleContextHolder.getLocale()),
                        "suggestion", suggestionText
                    ));
                }

                // 2. Adım: Kuralda kullanılacak 'n' gibi sabitleri tanımla
                Map<String, Object> ruleConstants = Map.of("n", request.baseSet.size());
                
                // 3. Adım: RuleParser'ı çağırarak kuralı çalıştırılabilir bir fonksiyona çevir
                BiFunction<Integer, Integer, Set<Integer>> operation = 
                    ruleParserService.parseRule(request.rule, ruleConstants);
                
                // 4. Adım: Aksiyom motorunu bu fonksiyonla çalıştır
                // (verifyAll() artık içeride (R,+) abelyen grup aksiyomunu da gerçekten
                // doğruluyor; bkz. AxiomVerifier.verifyAdditiveGroupAxioms())
                AxiomVerifier verifier = new AxiomVerifier(request.baseSet, operation, messageSource);
                VerificationResult result = verifier.verifyAll();

                Map<String, Map<String, String>> tableData = new LinkedHashMap<>();

                for (Integer rowElement : request.baseSet) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (Integer colElement : request.baseSet) {
                        Set<Integer> operationResult = operation.apply(rowElement, colElement);
                        String setResultString = operationResult.stream()
                                                                .map(String::valueOf)
                                                                .collect(Collectors.joining(", "));
                        row.put(String.valueOf(colElement), "{" + setResultString + "}");
                    }
                    tableData.put(String.valueOf(rowElement), row);
                }
                result.setCayleyTable(tableData);

                if (!result.isHypergroup()) {
                    String suggestionText = formatVerifiedSuggestions(
                        ruleSuggestionEngine.suggest(request.rule, request.baseSet));

                    result.setSuggestion(suggestionText);
                }
            
                return ResponseEntity.ok(result);
            } 
            else {
                throw new InvalidRuleException(messageSource.getMessage("verify.error.missingBaseSetOrDomain", null, LocaleContextHolder.getLocale()));
            }

        } catch (InvalidRuleException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("verify sırasında beklenmedik hata", e);
            return ResponseEntity.status(500).body(Map.of("error",
                messageSource.getMessage("common.error.unexpected", null, LocaleContextHolder.getLocale())));
        }
    }
}