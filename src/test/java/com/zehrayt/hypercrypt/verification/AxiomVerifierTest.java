package com.zehrayt.hypercrypt.verification;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class AxiomVerifierTest {

    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        // verifyAll() içindeki locale, LocaleContextHolder'dan okunuyor;
        // testin de aynı locale'i kullanması gerekiyor, aksi halde
        // messageSource.getMessage() farklı bir dile düşebilir.
        LocaleContextHolder.setLocale(Locale.forLanguageTag("tr"));

        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages"); // messages_tr.properties / messages_en.properties bekleniyor
        ms.setDefaultEncoding("UTF-8");
        this.messageSource = ms;
    }

    @AfterEach
    void tearDown() {
        // Diğer testleri etkilememesi için locale'i sıfırla.
        LocaleContextHolder.resetLocaleContext();
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    @Test
    void test_Z3_with_addition_is_a_hypergroup() {
        Set<Integer> z3 = Set.of(0, 1, 2);
        BiFunction<Integer, Integer, Set<Integer>> additionMod3 = (a, b) -> Set.of((a + b) % 3);

        AxiomVerifier verifier = new AxiomVerifier(z3, additionMod3, messageSource);
        VerificationResult result = verifier.verifyAll();

        assertTrue(result.isHypergroup(), "Z_3 with addition mod 3 should be a hypergroup.");
        assertNull(result.getFailingAxiom());
    }

    @Test
    void test_Z3_with_multiplication_fails_reproduction() {
        Set<Integer> z3 = Set.of(0, 1, 2);
        BiFunction<Integer, Integer, Set<Integer>> multiplicationMod3 = (a, b) -> Set.of((a * b) % 3);

        AxiomVerifier verifier = new AxiomVerifier(z3, multiplicationMod3, messageSource);
        VerificationResult result = verifier.verifyAll();

        assertFalse(result.isHypergroup());
        assertFalse(result.isQuasihypergroup());
        // Hardcoded Türkçe metin yerine, properties dosyasındaki gerçek değeri
        // messageSource üzerinden okuyoruz — böylece test, metin değişse bile
        // (anahtar aynı kaldığı sürece) doğru kalır.
        assertEquals(msg("axiom.name.reproduction"), result.getFailingAxiom());
    }
    
    @Test
    void test_modularSubtraction_preservesClosure_butFailsAssociativity() {
        // Modüler çıkarma kullanımıyla kapanıklık (closure) her zaman sağlanır. 
        // Bu sayede, testin asıl amacı olan birleşme (associativity) başarısızlıklarının 
        // doğru raporlanması hatasız şekilde sınanabilir.
        Set<Integer> z3 = Set.of(0, 1, 2);
        BiFunction<Integer, Integer, Set<Integer>> modularSubtraction =
            (a, b) -> Set.of(((a - b) % 3 + 3) % 3);

        AxiomVerifier verifier = new AxiomVerifier(z3, modularSubtraction, messageSource);
        VerificationResult result = verifier.verifyAll();

        assertFalse(result.isHypergroup());
        assertFalse(result.isSemihypergroup());
        // NOT: AxiomVerifier.verifyAll() çıktısı "Birleşme Özelliği (Madde 2)" etiketini kullandığından, 
        // test bu ifadeyle eşleşecek şekilde ayarlanmıştır.
        assertEquals(msg("axiom.name.associativity"), result.getFailingAxiom());
    }
}