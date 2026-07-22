package com.zehrayt.hypercrypt.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SuggestionModelScorerTest {

    @Test
    void test_model_loadsFromClasspath_andScoresWithinValidRange() {
        // src/main/resources/models altına konan .onnx ve .json test classpath'inde de bulunur.
        SuggestionModelScorer scorer = new SuggestionModelScorer();
        assertTrue(scorer.isAvailable(), "Classpath'teki model başarıyla yüklenmeli.");

        float score = scorer.scoreCandidate("(a+b)%n", (a, b) -> Set.of((a + b) % 3), 3, "operator_swap");
        assertTrue(score >= 0f && score <= 1f, "Skor [0,1] aralığında olmalı, ama " + score + " döndü.");
    }

    @Test
    void test_scoreCandidate_neverThrows_evenWhenOperationThrows() {
        SuggestionModelScorer scorer = new SuggestionModelScorer();

        // Kasıtlı olarak her çağrıda istisna fırlatan bir işlem veriyoruz;
        // özellik çıkarımı (symmetry_ratio hesaplaması) bunu örnekleme sırasında tetikleyecek.
        float score = assertDoesNotThrow(() -> scorer.scoreCandidate(
            "(a*b)%n",
            (a, b) -> { throw new RuntimeException("kasıtlı test hatası"); },
            3,
            "term_drop"));

        assertTrue(score >= 0f && score <= 1f, "Hata durumunda bile geçerli bir skor dönmeli.");
    }

    @Test
    void test_scoreCandidate_returnsNeutral_whenModelUnavailable() {
        SuggestionModelScorer scorer = new SuggestionModelScorer() {
            @Override
            public boolean isAvailable() {
                return false;
            }
        };

        float score = scorer.scoreCandidate("(a+b)%n", (a, b) -> Set.of((a + b) % 3), 3, "operator_swap");
        assertEquals(0.5f, score, 0.0001f, "Model devre dışıyken nötr skor (0.5) dönmeli.");
    }
}
