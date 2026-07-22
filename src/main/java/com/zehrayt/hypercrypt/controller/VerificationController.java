package com.zehrayt.hypercrypt.controller;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import com.zehrayt.hypercrypt.exception.InvalidRuleException;
import com.zehrayt.hypercrypt.service.GeminiSuggestionService;
import com.zehrayt.hypercrypt.service.RuleParserService;
import com.zehrayt.hypercrypt.service.RuleSuggestionEngine;
import com.zehrayt.hypercrypt.verification.AxiomVerifier;
import com.zehrayt.hypercrypt.verification.SymbolicVerifierService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

// İzin verilen origin'ler merkezi olarak config/WebConfig.java üzerinden (app.cors.allowed-origins property'si
// ile) yönetiliyor; bu sayede her endpoint'te ayrı ayrı "*" yazma riski ortadan kalkıyor.
@RestController
@RequestMapping("/api")
public class VerificationController {

    private static final Logger log = LoggerFactory.getLogger(VerificationController.class);

    private final GeminiSuggestionService suggestionService;
    private final RuleParserService ruleParserService;
    private final SymbolicVerifierService symbolicVerifierService;
    private final RuleSuggestionEngine ruleSuggestionEngine;

    @Autowired
    public VerificationController(GeminiSuggestionService suggestionService,
                                RuleParserService ruleParserService,
                                SymbolicVerifierService symbolicVerifierService,
                                RuleSuggestionEngine ruleSuggestionEngine) {
        this.suggestionService = suggestionService;
        this.ruleParserService = ruleParserService;
        this.symbolicVerifierService = symbolicVerifierService;
        this.ruleSuggestionEngine = ruleSuggestionEngine;
    }

    // Doğrulanmış (AxiomVerifier'dan geçmiş) önerileri tek bir açıklama metnine indirger;
    // hiçbiri bulunamazsa null döner ki çağıran taraf Gemini'ye düşebilsin.
    private String formatVerifiedSuggestions(List<RuleSuggestionEngine.Suggestion> verified) {
        if (verified.isEmpty()) {
            return null;
        }
        return verified.stream().map(s -> s.explanation).collect(Collectors.joining(" "));
    }

    // DoS koruması: AxiomVerifier'ın birleşme/dağılma testleri O(n^3)
    // çalışır. Sınırsız büyüklükte bir baseSet kabul etmek, sunucu CPU'sunu
    // kilitleyebilecek bir hizmet engelleme (DoS) vektörü oluşturur.
    private static final int MAX_BASE_SET_SIZE = 100;

    public static class VerificationRequest {
        public Set<Integer> baseSet; // Sonlu küme
        public String domain;        // Sonsuz küme
        public String rule;
    }

    @PostMapping("/verify")
    public ResponseEntity<Object> verifyStructure(@RequestBody VerificationRequest request) {
        try {
            // --- DEĞİŞİKLİK 3: Analiz tipine göre yönlendirme yapıyoruz. ---
            if (request.domain != null && !request.domain.isBlank()) {
                
                // --- SEMBOLİK ANALİZ YOLU (Sonsuz Küme) ---
                VerificationResult result = symbolicVerifierService.verifySymbolically(request.rule, request.domain);
                return ResponseEntity.ok(result);
            } 
            else if (request.baseSet != null && !request.baseSet.isEmpty()) {

                // 0. Adım: Boyut sınırı kontrolü (DoS koruması).
                if (request.baseSet.size() > MAX_BASE_SET_SIZE) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "error", String.format(
                            "Sonlu küme boyutu çok büyük (%d). En fazla %d eleman desteklenmektedir.",
                            request.baseSet.size(), MAX_BASE_SET_SIZE)
                    ));
                }

                // 1. Kuralın içinde standart çarpma (*) içerip içermediğini kontrol et.
                if (request.rule == null || !request.rule.contains("*")) {

                    // Önce doğrulanmış (AxiomVerifier'dan geçmiş) bir alternatif ara;
                    // bulunamazsa Gemini'ye düş.
                    String suggestionText = null;
                    if (request.rule != null) {
                        suggestionText = formatVerifiedSuggestions(
                            ruleSuggestionEngine.suggest(request.rule, request.baseSet));
                    }
                    if (suggestionText == null) {
                        suggestionText = suggestionService.getSuggestion(
                            request.baseSet.toString(),
                            request.rule,
                            "Kuralın çarpma içermemesi"
                        );
                    }

                    return ResponseEntity.badRequest().body(Map.of(
                        "error", "Girilen kural standart çarpma (*) işlemi içermelidir.",
                        "suggestion", suggestionText
                    ));
                }

                // 2. Adım: Kuralda kullanılacak 'n' gibi sabitleri tanımla
                Map<String, Object> ruleConstants = Map.of("n", request.baseSet.size());
                
                // 3. Adım: RuleParser'ı çağırarak kuralı çalıştırılabilir bir fonksiyona çevir
                BiFunction<Integer, Integer, Set<Integer>> operation = 
                    ruleParserService.parseRule(request.rule, ruleConstants);
                
                // 4. Adım: Aksiyom motorunu bu fonksiyonla çalıştır
                AxiomVerifier verifier = new AxiomVerifier(request.baseSet, operation);
                VerificationResult result = verifier.verifyAll();

                Map<String, Map<String, String>> tableData = new LinkedHashMap<>(); // Sırayı korumak için LinkedHashMap

                for (Integer rowElement : request.baseSet) {
                    Map<String, String> row = new LinkedHashMap<>();
                    // Sütunlar için döngü
                    for (Integer colElement : request.baseSet) {
                        // a ο b işlemini yap
                        Set<Integer> operationResult = operation.apply(rowElement, colElement);
                        // Sonucu "{b, c}" gibi bir string'e çevir
                        String setResultString = operationResult.stream()
                                                                .map(String::valueOf)
                                                                .collect(Collectors.joining(", "));
                        // İç map'e ekle
                        row.put(String.valueOf(colElement), "{" + setResultString + "}");
                    }
                    // Dış map'e ekle
                    tableData.put(String.valueOf(rowElement), row);
                }
                // Oluşturulan tabloyu sonuç nesnesine ekle
                result.setCayleyTable(tableData);

                // 5. Adım: Gerekirse öneri üret — önce doğrulanmış aramayı dene, yoksa Gemini'ye düş.
                if (!result.isHypergroup()) {
                    String failingAxiom = result.getFailingAxiom() != null ? result.getFailingAxiom() : "belirtilen aksiyomları";

                    String suggestionText = formatVerifiedSuggestions(
                        ruleSuggestionEngine.suggest(request.rule, request.baseSet));

                    if (suggestionText == null) {
                        suggestionText = suggestionService.getSuggestion(
                                request.baseSet.toString(), request.rule, failingAxiom);
                    }

                    result.setSuggestion(suggestionText);
                }
            
                // 6. Adım: Başarılı sonucu döndür
                return ResponseEntity.ok(result);
            } 
            else {
                // Eğer ne sonlu ne de sonsuz küme bilgisi verilmediyse hata döndür.
                throw new InvalidRuleException("İstek için 'baseSet' (sonlu küme) veya 'domain' (sonsuz küme) belirtilmelidir.");
            }

        } catch (InvalidRuleException e) {
            // Kontrollü hatalar için 400 Bad Request döndür
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // Beklenmedik diğer tüm hatalar için 500 Internal Server Error döndür
            log.error("verify sırasında beklenmedik hata", e);
            return ResponseEntity.status(500).body(Map.of("error", "Sunucuda beklenmedik bir hata oluştu."));
        }
    }
}