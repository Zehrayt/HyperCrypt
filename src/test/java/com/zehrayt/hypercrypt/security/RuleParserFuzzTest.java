package com.zehrayt.hypercrypt.security;

import com.zehrayt.hypercrypt.exception.InvalidRuleException;
import com.zehrayt.hypercrypt.service.RuleParserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GÜVENLİK FUZZ TEST HARNESS: RuleParserService'in Rhino sandbox'ını doğrular.
 *
 * Savunma katmanları:
 * 1. isObviouslyMalicious(): Kara liste tabanlı (java, eval vb.) filtreleme.
 *  2. initSafeStandardObjects(): Java paketlerine erişimi engelleyen ana katman.
 *  3. Instruction-count: CPU ve sonsuz döngü (DoS) koruması.
 *  4. hardenScopeAgainstMemoryAmplification(): Bellek-amplifikasyonu (repeat/join/fill vb.) koruması.
 *
 * Hedef: payload'lar her zaman InvalidRuleException ile reddedilsin; JVM çökmesin,
 * Java tarafına sızıntı olmasın.
 */
class RuleParserFuzzTest {

    private RuleParserService ruleParserService;

    @BeforeEach
    void setUp() {
        ruleParserService = new RuleParserService();
    }

    // 1) KARA LİSTE BYPASS TESTLERİ
    // "ja"+"va" gibi string birleştirmelerle kara liste atlatılabilir.

    @Test
    void test_blacklistBypass_stringConcatenation_isNotCaughtBySourceFilter() {
        // "java" kelimesi iki ayrı string literaline bölündüğü için kaynak
        // metinde bitişik geçmiyor; isObviouslyMalicious() bunu yakalayamıyor. Burada
        // JS motoru sadece string birleştirip uzunluk alıyor, gerçek bir erişim yok.
        String payload = "(\"ja\" + \"va\").length + a + b";

        assertDoesNotThrow(() -> ruleParserService.parseRule(payload, null),
            "Kara liste, string birleştirmeyle parçalanmış 'java' kelimesini yakalamalı ama yakalamıyor " +
            "(bilinen bir zayıflık -- derleme zamanında değil, çalışma zamanında string oluşuyor).");
    }

    @Test
    void test_functionConstructorEscape_isRejectedByBlacklist() {
        // "constructor" artık kara listede; klasik Rhino escape denemesi ön-kontrolde reddedilir.
        String payload = "this.constructor.constructor(\"return this\")() + a + b";

        assertThrows(InvalidRuleException.class, () -> ruleParserService.parseRule(payload, null));
    }

    // 2) DoS / SONSUZ DÖNGÜ TESTİ
    // Ok fonksiyonunda (=>) "function" kelimesi geçmez, kara liste bunu kaçırır.

    @Test
    void test_arrowFunctionInfiniteLoop_bypassesKeywordBlacklist_butIsStoppedByInstructionLimit() {
        String payload = "(() => { let x = 0; while (true) { x++; } return x; })() + a + b";

        // Kara liste bunu engellemiyor.
        assertDoesNotThrow(() -> ruleParserService.parseRule(payload, null),
            "Ok fonksiyonu ile sonsuz döngü kara liste tarafından engellenmemeli (bilinen zayıflık).");

        BiFunction<Integer, Integer, Set<Integer>> operation = ruleParserService.parseRule(payload, null);

        // Asıl koruma: instruction-count limiti döngüyü durdurmalı (10 sn'yi aşarsa test zaten "hang" olarak başarısız olur).
        InvalidRuleException ex = assertThrows(InvalidRuleException.class,
            () -> assertTimeoutPreemptively(Duration.ofSeconds(10), () -> operation.apply(1, 2)),
            "Sonsuz döngü, instruction-count limiti tarafından InvalidRuleException ile durdurulmalı.");

        assertTrue(ex.getMessage().contains("çalışma süresi sınırını aştı"),
            "Hata mesajı, sonsuz döngünün zaman/instruction limiti tarafından yakalandığını belirtmeli.");
    }

    // 3) BELLEK TÜKETİMİ (MEMORY-BOMB) — KAPATILAN AÇIK
    // String.repeat() vb. metotlar scope'tan kaldırıldı (hardenScopeAgainstMemoryAmplification).
    // Bu test artık payload'ın reddedildiğini doğruluyor.

    @Test
    void test_largeStringAllocation_isRejected_afterMemoryAmplificationGuard() {
        String payload = "\"x\".repeat(2000000).length + a + b";

        BiFunction<Integer, Integer, Set<Integer>> operation = ruleParserService.parseRule(payload, null);

        assertThrows(InvalidRuleException.class, () -> operation.apply(1, 2),
            "String.repeat() scope'tan kaldırıldığı için bu payload artık InvalidRuleException ile reddedilmeli.");
    }

    // 4) MUTASYON TABANLI FUZZING
    // Tohum payload'ları rastgele mutasyonlarla (bölme, boşluk, unicode
    // kaçış) çoğaltır; her sonucun güvenli değer ya da InvalidRuleException
    // olmasını (hang veya başka istisna türü olmamasını) doğrular.

    private static final List<String> SEED_PAYLOADS = List.of(
        "java.lang.Runtime",
        "Packages.java.lang.System",
        "eval(\"1+1\")",
        "this.constructor.constructor(\"return 1\")()",
        "new Function(\"return 1\")()",
        "process.exit()",
        "require(\"fs\")"
    );

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void test_mutatedSeedPayloads_neverHangOrLeakUnexpectedExceptionType(int seedIndex) {
        Random random = new Random(seedIndex); // deterministik, tekrarlanabilir fuzzing
        String seed = SEED_PAYLOADS.get(seedIndex % SEED_PAYLOADS.size());

        for (int mutation = 0; mutation < 20; mutation++) {
            String mutated = mutate(seed, random);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                try {
                    BiFunction<Integer, Integer, Set<Integer>> operation =
                        ruleParserService.parseRule(mutated, null);
                    operation.apply(1, 2);
                    // Buraya ulaşıldıysa kural güvenli bir sayı/dizi üretmiş demektir
                    // (parseRule zaten Number/NativeArray dışını reddeder).
                } catch (InvalidRuleException expected) {
                    // Beklenen, güvenli sonuç.
                } catch (Exception unexpected) {
                    fail("Mutasyona uğramış payload (\"" + mutated + "\") beklenmeyen bir istisna türü " +
                         "fırlattı: " + unexpected.getClass().getName() + " — bu, sandbox'ın kontrolsüz " +
                         "bir şekilde sızdırdığı/çöktüğü anlamına gelebilir.", unexpected);
                }
            }, "Payload zaman aşımına uğradı (muhtemel sonsuz döngü/instruction limiti çalışmıyor): " + mutated);
        }
    }

    /**
     * Basit mutasyon stratejileri: anahtar kelimeleri string birleştirmeyle
     * parçalama, rastgele boşluk ekleme, bir karakteri unicode kaçış dizisine çevirme. 
     */
    private String mutate(String seed, Random random) {
        String result = seed;
        int strategy = random.nextInt(3);
        switch (strategy) {
            case 0 -> {
                // Bir kelimeyi ortadan ikiye bölüp string birleştirmeyle yeniden kur.
                if (result.length() > 4) {
                    int splitPoint = 2 + random.nextInt(result.length() - 2);
                    result = "(\"" + result.substring(0, splitPoint) + "\" + \""
                        + result.substring(splitPoint) + "\")";
                }
            }
            case 1 -> {
                // Rastgele konuma fazladan boşluk ekle.
                int pos = random.nextInt(result.length() + 1);
                result = result.substring(0, pos) + "  " + result.substring(pos);
            }
            default -> {
                // Bir karakteri unicode kaçış dizisine çevir.
                if (!result.isEmpty()) {
                    int pos = random.nextInt(result.length());
                    char c = result.charAt(pos);
                    String escaped = String.format("\\u%04x", (int) c);
                    result = result.substring(0, pos) + escaped + result.substring(pos + 1);
                }
            }
        }
        return result + " + a + b";
    }
}
