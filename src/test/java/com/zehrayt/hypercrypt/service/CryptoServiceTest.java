package com.zehrayt.hypercrypt.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService(new RuleParserService());
    }

    @Test
    void test_calculate_isDeterministic_sameInputsProduceSameOutput() {
        Integer first = cryptoService.calculate("(a + b) % n", 7, 3, 11);
        Integer second = cryptoService.calculate("(a + b) % n", 7, 3, 11);

        assertEquals(first, second, "Same rule/base/exponent/modulus should always derive the same shared value.");
    }

    @Test
    void test_calculate_resultIsWithinModulusRange() {
        Integer result = cryptoService.calculate("(a + b) % n", 7, 3, 11);

        assertNotNull(result);
        assertTrue(result >= 0 && result < 11, "Derived value must fall within [0, modulus).");
    }

    @Test
    void test_calculate_ruleContainingMathDotPow_isNotCorrupted() {
        assertDoesNotThrow(() -> cryptoService.calculate("Math.pow(a, b) % n", 2, 3, 100));
    }

    @Test
    void test_calculate_emptyResultSet_throwsIllegalStateException() {
        // "undefined" döndüren bir kural, RuleParserService tarafından boş bir
        // sonuç kümesine çevrilir; CryptoService bu durumda IllegalStateException fırlatmalıdır.
        assertThrows(IllegalStateException.class, () -> {
            cryptoService.calculate("undefined", 5, 3, 11);
        });
    }

    @Test
    void test_calculate_differentInputs_canProduceDifferentOutputs() {
        Integer resultA = cryptoService.calculate("(a + b) % n", 7, 3, 97);
        Integer resultB = cryptoService.calculate("(a + b) % n", 8, 3, 97);

        // Farklı taban (base) değerleri farklı sonuç kümelerine ve dolayısıyla
        // (çok yüksek olasılıkla) farklı türetilmiş değerlere yol açmalıdır.
        assertNotEquals(resultA, resultB);
    }
}
