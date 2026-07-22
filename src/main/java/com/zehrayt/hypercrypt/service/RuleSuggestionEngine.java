package com.zehrayt.hypercrypt.service;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import com.zehrayt.hypercrypt.verification.AxiomVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hatalı kuralları mutasyona uğratıp AxiomVerifier ile doğrulayarak kesin çalışan öneriler sunar.
 * SuggestionModelScorer sadece test sırasını belirler; hatalı tahminler AxiomVerifier tarafından engellenir.
 */
@Service
public class RuleSuggestionEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleSuggestionEngine.class);

    // Arama bütçesi: DoS koruması (CryptoController.eveAttack ile aynı desen).
    private static final int MAX_CANDIDATES = 200;
    private static final long MAX_SEARCH_MILLIS = 3000;
    private static final int MAX_BASE_SET_SIZE_FOR_SEARCH = 30;
    private static final int MAX_SUGGESTIONS = 3;

    private static final Pattern INT_LITERAL = Pattern.compile("(?<![\\w.])\\d+(?![\\w.])");
    private static final char[] ARITHMETIC_OPS = {'+', '-', '*'};

    private final RuleParserService ruleParserService;
    private final SuggestionModelScorer modelScorer;

    public RuleSuggestionEngine(RuleParserService ruleParserService, SuggestionModelScorer modelScorer) {
        this.ruleParserService = ruleParserService;
        this.modelScorer = modelScorer;
    }

    public static class Suggestion {
        public final String rule;
        public final String explanation;

        public Suggestion(String rule, String explanation) {
            this.rule = rule;
            this.explanation = explanation;
        }
    }

    // Bir mutasyon adayını, onu üreten stratejinin adıyla birlikte tutar
    // (model, hangi mutasyon tipinin işe yarama ihtimalini bu etiketle tahmin eder).
    private static class MutationCandidate {
        final String rule;
        final String mutationType;

        MutationCandidate(String rule, String mutationType) {
            this.rule = rule;
            this.mutationType = mutationType;
        }
    }

    private static class ScoredCandidate {
        final MutationCandidate candidate;
        final BiFunction<Integer, Integer, Set<Integer>> operation;
        final float score;

        ScoredCandidate(MutationCandidate candidate, BiFunction<Integer, Integer, Set<Integer>> operation, float score) {
            this.candidate = candidate;
            this.operation = operation;
            this.score = score;
        }
    }

    /**
     * originalRule, baseSet üzerinde hipergrup aksiyomlarını sağlamıyorsa,
     * doğrulanmış (gerçekten AxiomVerifier'dan geçmiş) alternatif kurallar döner.
     * Küme çok büyükse veya bütçe dolarsa boş liste döner (brute-force'a düşülmez).
     */
    public List<Suggestion> suggest(String originalRule, Set<Integer> baseSet) {
        List<Suggestion> results = new ArrayList<>();

        if (originalRule == null || originalRule.isBlank() || baseSet == null || baseSet.isEmpty()) {
            return results;
        }
        if (baseSet.size() > MAX_BASE_SET_SIZE_FOR_SEARCH) {
            log.debug("Küme çok büyük ({}), otomatik öneri araması atlanıyor.", baseSet.size());
            return results;
        }

        Map<String, Object> constants = Map.of("n", baseSet.size());
        long deadline = System.currentTimeMillis() + MAX_SEARCH_MILLIS;
        int n = baseSet.size();

        Set<String> seen = new HashSet<>();
        seen.add(normalize(originalRule));

        // 1. Aşama: adayları üret, ayrıştır (bir kez) ve varsa model skoruyla etiketle.
        List<ScoredCandidate> scoredCandidates = new ArrayList<>();
        int prepared = 0;
        for (MutationCandidate candidate : generateMutations(originalRule)) {
            if (prepared >= MAX_CANDIDATES || System.currentTimeMillis() > deadline) {
                break;
            }
            String normalized = normalize(candidate.rule);
            if (!seen.add(normalized)) {
                continue;
            }
            prepared++;

            BiFunction<Integer, Integer, Set<Integer>> operation = tryParse(candidate.rule, constants);
            if (operation == null) {
                continue;
            }

            float score = modelScorer.isAvailable()
                ? modelScorer.scoreCandidate(candidate.rule, operation, n, candidate.mutationType)
                : 0.5f;

            scoredCandidates.add(new ScoredCandidate(candidate, operation, score));
        }

        // 2. Aşama: model varsa yüksek skordan düşüğe sırala (model yoksa üretim sırası korunur).
        scoredCandidates.sort(Comparator.comparingDouble((ScoredCandidate c) -> c.score).reversed());

        // 3. Aşama: sırayla AxiomVerifier ile fiilen doğrula, ilk MAX_SUGGESTIONS başarılıyı topla.
        for (ScoredCandidate scored : scoredCandidates) {
            if (results.size() >= MAX_SUGGESTIONS || System.currentTimeMillis() > deadline) {
                break;
            }
            if (verifies(scored.operation, baseSet)) {
                results.add(new Suggestion(scored.candidate.rule, String.format(
                    "\"%s\" yerine \"%s\" denenirse hiper-yapı aksiyomları sağlanıyor (AxiomVerifier ile doğrulandı).",
                    originalRule, scored.candidate.rule)));
            }
        }

        log.debug("Kural önerisi araması: {} aday hazırlandı, {} doğrulanmış öneri bulundu (model: {}).",
            prepared, results.size(), modelScorer.isAvailable() ? "aktif" : "devre dışı");
        return results;
    }

    private BiFunction<Integer, Integer, Set<Integer>> tryParse(String candidateRule, Map<String, Object> constants) {
        try {
            return ruleParserService.parseRule(candidateRule, constants);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean verifies(BiFunction<Integer, Integer, Set<Integer>> operation, Set<Integer> baseSet) {
        try {
            VerificationResult result = new AxiomVerifier(baseSet, operation).verifyAll();
            return result.isHypergroup();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String normalize(String rule) {
        return rule.replaceAll("\\s+", "");
    }

    // --- Mutasyon üretimi: dört bağımsız strateji ---

    private List<MutationCandidate> generateMutations(String rule) {
        List<MutationCandidate> mutations = new ArrayList<>();
        tag(mutations, coefficientSweep(rule), "coefficient_sweep");
        tag(mutations, operatorSwap(rule), "operator_swap");
        tag(mutations, termDrop(rule), "term_drop");
        tag(mutations, missingVariableFix(rule), "missing_variable_fix");
        return mutations;
    }

    private void tag(List<MutationCandidate> out, List<String> candidates, String mutationType) {
        for (String candidate : candidates) {
            out.add(new MutationCandidate(candidate, mutationType));
        }
    }

    // Sayısal sabitleri küçük bir aralıkta kaydırır (örn. +1 -> +2, +1 -> -1).
    private List<String> coefficientSweep(String rule) {
        List<String> out = new ArrayList<>();
        Matcher matcher = INT_LITERAL.matcher(rule);
        int[] deltas = {-2, -1, 1, 2};

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            int value;
            try {
                value = Integer.parseInt(rule.substring(start, end));
            } catch (NumberFormatException e) {
                continue;
            }
            for (int delta : deltas) {
                int newValue = value + delta;
                if (newValue < 0) {
                    continue;
                }
                out.add(rule.substring(0, start) + newValue + rule.substring(end));
            }
        }
        return out;
    }

    // Aritmetik operatörleri (+, -, *) tek tek diğer ikisiyle değiştirir.
    private List<String> operatorSwap(String rule) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rule.length(); i++) {
            char current = rule.charAt(i);
            if (current != '+' && current != '-' && current != '*') {
                continue;
            }
            for (char op : ARITHMETIC_OPS) {
                if (op != current) {
                    out.add(rule.substring(0, i) + op + rule.substring(i + 1));
                }
            }
        }
        return out;
    }

    // Doğrusal olmayan çarpım terimini (a*b) sıfırlayarak dereceyi düşürür.
    private List<String> termDrop(String rule) {
        List<String> out = new ArrayList<>();
        if (rule.contains("a*b")) {
            out.add(rule.replace("a*b", "0"));
        }
        if (rule.contains("a * b")) {
            out.add(rule.replace("a * b", "0"));
        }
        return out;
    }

    // Kuralda eksik olan değişkeni (a veya b) toplamsal olarak ekler.
    private List<String> missingVariableFix(String rule) {
        List<String> out = new ArrayList<>();
        boolean hasA = rule.contains("a");
        boolean hasB = rule.contains("b");
        if (hasA && !hasB) {
            out.add("(" + rule + " + b)");
        }
        if (hasB && !hasA) {
            out.add("(" + rule + " + a)");
        }
        return out;
    }
}
