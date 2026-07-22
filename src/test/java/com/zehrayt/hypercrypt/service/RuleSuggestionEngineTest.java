package com.zehrayt.hypercrypt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleSuggestionEngineTest {

    private RuleSuggestionEngine engine;

    @BeforeEach
    void setUp() {
        // Gerçek SuggestionModelScorer kullanılıyor: model classpath'te bulunursa önceliklendirme aktif olur,
        // bulunamazsa nötr skorla (üretim sırasıyla) aynı doğru sonuca ulaşılmalı.
        // iki durumda da AxiomVerifier garantisi geçerli.
        engine = new RuleSuggestionEngine(new RuleParserService(), new SuggestionModelScorer());
    }

    @Test
    void test_suggest_findsVerifiedFix_forMultiplicativeRuleFailingReproduction() {
        // (a*b)%n, Z_3 üzerinde üretim aksiyomunu sağlamıyor (AxiomVerifierTest).
        // Operatör değişimi mutasyonu bunu (a+b)%n'e çevirmeli, bu da bilinen bir hipergrup.
        Set<Integer> z3 = Set.of(0, 1, 2);

        List<RuleSuggestionEngine.Suggestion> suggestions = engine.suggest("(a*b)%n", z3);

        assertFalse(suggestions.isEmpty(), "En az bir doğrulanmış öneri bulunmalı.");
        assertTrue(suggestions.stream().anyMatch(s -> "(a+b)%n".equals(s.rule)),
            "Operatör değişimiyle üretilen '(a+b)%n' önerisi listede olmalı.");

        // Her öneri, gösterilmeden önce fiilen doğrulanmış olmalı: aynı kuralı
        // yeniden çalıştırıp gerçekten hipergrup olduğunu teyit ediyoruz.
        for (RuleSuggestionEngine.Suggestion s : suggestions) {
            var operation = new RuleParserService().parseRule(s.rule, java.util.Map.of("n", z3.size()));
            var result = new com.zehrayt.hypercrypt.verification.AxiomVerifier(z3, operation).verifyAll();
            assertTrue(result.isHypergroup(), "Önerilen kural (" + s.rule + ") gerçekten hipergrup olmalı.");
        }
    }

    @Test
    void test_suggest_skipsSearch_whenBaseSetTooLarge() {
        Set<Integer> bigSet = new HashSet<>();
        for (int i = 0; i < 31; i++) {
            bigSet.add(i);
        }

        List<RuleSuggestionEngine.Suggestion> suggestions = assertTimeoutPreemptively(
            Duration.ofMillis(500),
            () -> engine.suggest("(a*b)%n", bigSet),
            "Küme boyut sınırını aşınca arama hemen atlanmalı, uzun sürmemeli.");

        assertTrue(suggestions.isEmpty(), "Boyut sınırı aşıldığında öneri araması yapılmamalı.");
    }

    @Test
    void test_suggest_returnsEmpty_forNullOrBlankInputs() {
        Set<Integer> z3 = Set.of(0, 1, 2);

        assertTrue(engine.suggest(null, z3).isEmpty());
        assertTrue(engine.suggest("", z3).isEmpty());
        assertTrue(engine.suggest("(a*b)%n", null).isEmpty());
        assertTrue(engine.suggest("(a*b)%n", Set.of()).isEmpty());
    }

    @Test
    void test_suggest_handlesNonsenseRule_withoutThrowing() {
        Set<Integer> z3 = Set.of(0, 1, 2);

        List<RuleSuggestionEngine.Suggestion> suggestions = assertDoesNotThrow(
            () -> engine.suggest("this is not * a + valid - rule 1 2 3", z3),
            "Saçma bir kural, mutasyon denemeleri sırasında istisna fırlatmamalı.");

        // Anlamsız bir kuraldan hipergrup üretmesi beklenmez, önemli olan çökmemesi.
        assertNotNull(suggestions);
    }

    @Test
    void test_suggest_findsSameVerifiedFix_whenModelUnavailable() {
        // Model hiç yüklenemese bile (ör. dosya eksik/hatalı), arama üretim
        // sırasına düşmeli ve doğru öneriyi yine bulmalı.
        // AxiomVerifier garantisi modelden bağımsızdır.
        RuleSuggestionEngine engineWithoutModel =
            new RuleSuggestionEngine(new RuleParserService(), new AlwaysUnavailableScorer());
        Set<Integer> z3 = Set.of(0, 1, 2);

        List<RuleSuggestionEngine.Suggestion> suggestions = engineWithoutModel.suggest("(a*b)%n", z3);

        assertFalse(suggestions.isEmpty(), "Model devre dışıyken de en az bir doğrulanmış öneri bulunmalı.");
        assertTrue(suggestions.stream().anyMatch(s -> "(a+b)%n".equals(s.rule)),
            "Model devre dışıyken de '(a+b)%n' önerisi bulunmalı.");
    }

    // Model her zaman devre dışıymış gibi davranan test yardımcı sınıfı.
    private static class AlwaysUnavailableScorer extends SuggestionModelScorer {
        @Override
        public boolean isAvailable() {
            return false;
        }
    }
}
