package com.zehrayt.hypercrypt.verification;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SymbolicVerifierServiceTest {

    private SymbolicVerifierService symbolicVerifierService;

    @BeforeEach
    void setUp() {
        symbolicVerifierService = new SymbolicVerifierService();
    }

    @Test
    void test_linearRule_overIntegers_isAssociativeAndSatisfiesReproduction() {
        // (a*1 + b*1) hem birleşmeli (associative) hem de derece-1 (lineer) olduğu için
        // hem yarı hipergrup hem de üretim aksiyomunu sağlamalıdır. ("*" gerektiren
        // ön kontrolü geçmesi için kural kasıtlı olarak çarpma içeriyor.)
        VerificationResult result = symbolicVerifierService.verifySymbolically("a*1 + b*1", "INTEGERS");

        assertTrue(result.isSemihypergroup(), "a*1 + b*1 should be associative.");
        assertTrue(result.isQuasihypergroup(), "Linear rule should satisfy the reproduction axiom.");
        assertTrue(result.isHypergroup());
    }

    @Test
    void test_multiplicationRule_failsReproductionAxiom_dueToDegreeGreaterThanOne() {
        // a * b ikinci derecedendir (a ve b'nin çarpımı), bu yüzden üretim aksiyomu
        // evrensel olarak çözülemez ve quasihypergroup olamaz.
        VerificationResult result = symbolicVerifierService.verifySymbolically("a * b", "INTEGERS");

        assertFalse(result.isQuasihypergroup(), "a * b has total degree 2 and should fail the reproduction axiom.");
        assertFalse(result.isHypergroup());
    }

    @Test
    void test_ruleWithoutMultiplication_isRejected() {
        // "a + b" standart çarpma (*) içermediği için, domain'den bağımsız olarak
        // sembolik analiz tarafından doğrudan reddedilmelidir.
        VerificationResult result = symbolicVerifierService.verifySymbolically("a + b", "INTEGERS");

        assertEquals("Çarpma İçermeyen Kural", result.getFailingAxiom());
        assertFalse(result.isHypergroup());
    }

    @Test
    void test_invalidRuleFormat_isRejected() {
        // 'a' ve 'b' dışında bir harf (örn. 'x') veya izin verilmeyen bir karakter
        // içeren kurallar sembolik analiz tarafından reddedilmelidir.
        VerificationResult result = symbolicVerifierService.verifySymbolically("a * x", "INTEGERS");

        assertEquals("Geçersiz Kural Formatı", result.getFailingAxiom());
        assertFalse(result.isHypergroup());
    }

    @Test
    void test_emptyRule_isRejected() {
        VerificationResult result = symbolicVerifierService.verifySymbolically("", "INTEGERS");

        assertEquals("Geçersiz Kural Formatı", result.getFailingAxiom());
    }

    @Test
    void test_unsupportedDomain_resultsInFailedSuggestion() {
        VerificationResult result = symbolicVerifierService.verifySymbolically("a * b + 1", "COMPLEX_NUMBERS");

        // getCoefficientFactory desteklenmeyen bir domain için IllegalArgumentException fırlatır;
        // bu, verifyAssociativity/verifyGenerationAxiom içindeki catch bloklarında yakalanmalı
        // ve semihypergroup false olarak işaretlenmelidir.
        assertFalse(result.isSemihypergroup());
    }
}
