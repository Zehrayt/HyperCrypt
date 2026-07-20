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
    void test_calculateSharedSecret_isDeterministic_sameInputsProduceSameOutput() {
        Integer first = cryptoService.calculateSharedSecret("(a + b) % n", 7, 3, 11);
        Integer second = cryptoService.calculateSharedSecret("(a + b) % n", 7, 3, 11);

        assertEquals(first, second, "Same rule/base/exponent/modulus should always derive the same shared value.");
    }

    @Test
    void test_calculateSharedSecret_resultIsWithinModulusRange() {
        Integer result = cryptoService.calculateSharedSecret("(a + b) % n", 7, 3, 11);

        assertNotNull(result);
        assertTrue(result >= 0 && result < 11, "Derived value must fall within [0, modulus).");
    }

    @Test
    void test_calculateSharedSecret_ruleContainingMathDotPow_isNotCorrupted() {
        assertDoesNotThrow(() -> cryptoService.calculateSharedSecret("Math.pow(a, b) % n", 2, 3, 100));
    }

    @Test
    void test_calculateSharedSecret_emptyResultSet_throwsIllegalStateException() {
        // "undefined" döndüren bir kural, RuleParserService tarafından boş bir
        // sonuç kümesine çevrilir; CryptoService bu durumda IllegalStateException fırlatmalıdır.
        assertThrows(IllegalStateException.class, () -> {
            cryptoService.calculateSharedSecret("undefined", 5, 3, 11);
        });
    }

    @Test
    void test_calculateSharedSecret_differentInputs_canProduceDifferentOutputs() {
        Integer resultA = cryptoService.calculateSharedSecret("(a + b) % n", 7, 3, 97);
        Integer resultB = cryptoService.calculateSharedSecret("(a + b) % n", 8, 3, 97);

        // Farklı taban (base) değerleri farklı sonuç kümelerine ve dolayısıyla
        // (çok yüksek olasılıkla) farklı türetilmiş değerlere yol açmalıdır.
        assertNotEquals(resultA, resultB);
    }

    @Test
    void test_calculatePublicValue_isDeterministic_sameInputsProduceSameOutput() {
        Integer first = cryptoService.calculatePublicValue("(a + b) % n", 7, 3, 11);
        Integer second = cryptoService.calculatePublicValue("(a + b) % n", 7, 3, 11);

        assertEquals(first, second);
    }

    // REGRESYON TESTİ: Alice ve Bob'un gerçekten aynı ortak sırra ulaştığını uçtan uca doğrular. 

    @Test
    void test_hyperDiffieHellman_aliceAndBobDeriveTheSameSharedSecret() {
        String rule = "(a + b) % n";
        int n = 23;
        int g = 5;
        int aliceSecret = 6;
        int bobSecret = 15;

        // 1. Adım: Genel anahtarlar (ham, hash'lenmemiş değerler) üretilir ve "ağ üzerinden" takas edilir.
        Integer alicePublic = cryptoService.calculatePublicValue(rule, g, aliceSecret, n);
        Integer bobPublic = cryptoService.calculatePublicValue(rule, g, bobSecret, n);

        // 2. Adım: Her taraf, karşıdan gelen genel anahtar ve kendi gizli anahtarıyla ortak sırrı hesaplar.
        Integer sharedSecretFromAlice = cryptoService.calculateSharedSecret(rule, bobPublic, aliceSecret, n);
        Integer sharedSecretFromBob = cryptoService.calculateSharedSecret(rule, alicePublic, bobSecret, n);

        assertEquals(sharedSecretFromAlice, sharedSecretFromBob,
            "Alice ve Bob, protokolün sonunda aynı ortak sırra ulaşmalıdır.");
    }

    @Test
    void test_hyperDiffieHellman_aliceAndBobDeriveTheSameSharedSecret_multiplicativeRule() {
        // Farklı (çarpımsal) bir kural ile de aynı uçtan uca anlaşmanın sağlandığını
        // doğrular. NOT: bu özellik yalnızca a ve b'ye göre SİMETRİK kurallar
        // (örn. a+b, a*b) için garantidir; a*3+b gibi asimetrik kurallar Alice
        // ve Bob'u farklı sonuçlara götürür (protokolün matematiksel önkoşulu budur).
        String rule = "(a * b) % n";
        int n = 97;
        int g = 11;
        int aliceSecret = 9;
        int bobSecret = 40;

        Integer alicePublic = cryptoService.calculatePublicValue(rule, g, aliceSecret, n);
        Integer bobPublic = cryptoService.calculatePublicValue(rule, g, bobSecret, n);

        Integer sharedSecretFromAlice = cryptoService.calculateSharedSecret(rule, bobPublic, aliceSecret, n);
        Integer sharedSecretFromBob = cryptoService.calculateSharedSecret(rule, alicePublic, bobSecret, n);

        assertEquals(sharedSecretFromAlice, sharedSecretFromBob);
    }
}
