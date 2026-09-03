package com.zehrayt.hypercrypt.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Hiper-Diffie-Hellman anahtar değişimini yürüten hesaplama servisi.
 */
@Service
public class CryptoService {
    private final RuleParserService ruleParserService;

    @Autowired
    public CryptoService(RuleParserService ruleParserService) {
        this.ruleParserService = ruleParserService;
    }

    /**
     * Genel anahtar (public key) üretimi için kullanılır — protokolün ilk adımı.
     *
     * @warning Güvensiz ağ üzerinden iletilen değerlerde SHA-256 tabanlı türetim UYGULANMAZ.
     * Hashleme, (g ∘ a) ∘ b = (g ∘ b) ∘ a cebirsel bağıntısını bozarak ortak sır
     * (shared secret) uyumunu engeller.
     * @note Hiper-işlem küme döndürdüğünde, deterministik sonuç için rastgele
     * iterasyon yerine her zaman "sıralı en küçük eleman" seçilir (bkz: CryptoServiceTest).
     *
     * @param rule Kural metni
     * @param base İşlemin sol tarafındaki değer (üreteç g veya karşıdan gelen anahtar)
     * @param exponent İşlemin sağ tarafındaki değer (gizli anahtar)
     * @param modulus Mod değeri (n)
     * @return Ağa gönderilecek genel anahtar değeri.
     */
    public Integer calculatePublicValue(String rule, int base, int exponent, int modulus) {
        Set<Integer> resultSet = evaluate(rule, base, exponent, modulus);
        return Collections.min(resultSet);
    }

    /**
     * Ortak sır (shared secret) üretimi için kullanılır — protokolün son adımı.
     *
     * Bu değer ağa hiç gönderilmez, yalnızca yerel olarak hesaplanır. Eve için
     * tahmin edilemezliği artırmak amacıyla SHA-256 tabanlı türetim (bkz.
     * deriveSharedValue) yalnızca burada, nihai ortak sır üzerinde uygulanır.
     *
     * @param rule Kural metni
     * @param base İşlemin sol tarafındaki değer (karşıdan gelen genel anahtar)
     * @param exponent İşlemin sağ tarafındaki değer (gizli anahtar)
     * @param modulus Mod değeri (n)
     * @return Kriptografik amaçla kullanılan, tahmin edilmesi zorlaştırılmış tek bir tamsayı sonucu.
     */
    public Integer calculateSharedSecret(String rule, int base, int exponent, int modulus) {
        Set<Integer> resultSet = evaluate(rule, base, exponent, modulus);
        return deriveSharedValue(resultSet, modulus);
    }

    /**
     * Kuralı (a op b) verilen taban/üs/modül parametreleriyle çalıştırıp ham
     * (hash'lenmemiş) sonuç kümesini döndürür.
     *
     * RuleParserService zaten 'a' ve 'b'yi gerçek fonksiyon parametresi olarak
     * bağlıyor; bu yüzden kuralı hiç string olarak değiştirmeden, doğrudan
     * base/exponent değerleriyle çalıştırıyoruz.
     *
     * DÜZELTME (Hakem 2 uyarısı): Kural çalıştırılmadan önce, bu kuralın gerçekten
     * bir Diffie-Hellman tipi anahtar değişimi için matematiksel olarak uygun olup
     * olmadığı (bkz. validateKeyExchangeCompatibility) kontrol edilir. Aksi halde
     * Alice ve Bob, kuralın simetrik olmaması nedeniyle farklı "ortak sır" değerlerine
     * ulaşabilir ve bu sessizce, hiçbir hata vermeden gerçekleşirdi.
     */
    private Set<Integer> evaluate(String rule, int base, int exponent, int modulus) {
        Map<String, Object> constants = Map.of("n", modulus);
        BiFunction<Integer, Integer, Set<Integer>> parsedRule = ruleParserService.parseRule(rule, constants);

        validateKeyExchangeCompatibility(parsedRule, modulus);

        Set<Integer> resultSet = parsedRule.apply(base, exponent);

        if (resultSet.isEmpty()) {
            throw new IllegalStateException("Kriptografik işlem boş bir sonuç kümesi üretti.");
        }

        return resultSet;
    }

    /**
     * DÜZELTME (Hakem 2 uyarısı): Genelleştirilmiş bir Diffie-Hellman protokolünün
     * çalışabilmesi için kuralın rho(x,k) = "x ile k'yi birleştiren fonksiyon" olarak
     * bir "değişmeli aile" (commuting family) oluşturması gerekir:
     *
     *      rho(rho(g,k2),k1) == rho(rho(g,k1),k2)      (her g, k1, k2 için)
     *
     * Bu koşul, klasik üstel alma (g^k) veya (a*b), (a+b) gibi hem birleşmeli hem
     * değişmeli işlemlerde otomatik sağlanır; ama makalenin ilk düzeltme denemesinde
     * kullanılan (3*a + 5*b) % n gibi ASİMETRİK doğrusal kurallarda SAĞLANMAZ — yani
     * Alice ve Bob protokolün sonunda aynı ortak sırra bile ulaşamaz (bkz. proje
     * testi: CryptoServiceTest.test_hyperDiffieHellman_...multiplicativeRule, ve
     * yorum satırındaki "a*3+b gibi asimetrik kurallar Alice ve Bob'u farklı
     * sonuçlara götürür" notu). Bu metot, birkaç rastgele örnekle bu koşulu deneysel
     * olarak doğrular ve koşulu sağlamayan bir kuralı, herhangi bir anahtar üretilmeden
     * ÖNCE reddeder.
     *
     * NOT: Bu, sonlu-örnekli (probabilistic) bir kontroldür, kesin bir ispat değildir;
     * yine de kuralın gerçekten iki tarafı da kapsayıp kapsamadığını ve simetrisini
     * çalışma zamanında güvenli biçimde elemeye yeter.
     */
    private void validateKeyExchangeCompatibility(BiFunction<Integer, Integer, Set<Integer>> rule, int modulus) {
        if (modulus <= 1) {
            return; // Anlamlı bir örnekleme yapılamaz; kural zaten başka yerde reddedilecektir.
        }

        // Örnekleme aralığı, gerçek modulus ile değil küçük bir üst sınırla (<=12) tutulur:
        // Math.pow(a,b) gibi üstel kurallarda büyük üsler double hassasiyetini bozarak bu
        // testi YANLIŞ NEGATİF üretebilir (gerçekte uyumlu bir kuralı hatalı biçimde
        // reddedebilir). Küçük değerlerle bile bir kuralın simetri/uyumluluk yapısı
        // güvenilir biçimde ortaya çıkar.
        int sampleBound = Math.max(2, Math.min(modulus, 12));
        Random rnd = new Random(42); // Sabit seed: her çağrıda aynı örnekler, deterministik davranış.
        for (int trial = 0; trial < 8; trial++) {
            int g = rnd.nextInt(sampleBound);
            int k1 = rnd.nextInt(sampleBound);
            int k2 = rnd.nextInt(sampleBound);

            try {
                int pub1 = Collections.min(rule.apply(g, k1));
                int pub2 = Collections.min(rule.apply(g, k2));
                int sharedViaK1First = Collections.min(rule.apply(pub2, k1));
                int sharedViaK2First = Collections.min(rule.apply(pub1, k2));

                if (sharedViaK1First != sharedViaK2First) {
                    throw new IllegalStateException(
                        "Bu kural Diffie-Hellman anahtar değişimi için uygun değil: "
                        + "rho(rho(g,k2),k1) = rho(rho(g,k1),k2) eşitliğini sağlamıyor, "
                        + "bu yüzden Alice ve Bob farklı ortak sırlara ulaşır. Kuralın a ve b "
                        + "üzerinde aynı (değişmeli/birleşmeli) yapıda olması gerekir "
                        + "(örn. a*b, a+b, ya da klasik üstel alma).");
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                // Bu örnekleme noktası kuralı hata verdirdiyse (örn. sıfıra bölme), atla ve devam et.
            }
        }
    }

    /**
     * Hiper-işlemin sonuç kümesinden tek bir paylaşılan değer türetir.
     *
     * kümenin tüm elemanlarını SHA-256 ile karıştırıp modulus'a indirgeyerek
     * daha az öngörülebilir, deterministik bir değer üretiyoruz.
     */
    private int deriveSharedValue(Set<Integer> resultSet, int modulus) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Kümeyi sıralayarak hash girdisinin eleman sırasından bağımsız,
            // her zaman aynı şekilde üretilmesini garanti ediyoruz.
            List<Integer> sortedValues = new ArrayList<>(resultSet);
            Collections.sort(sortedValues);
            for (Integer value : sortedValues) {
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
            }

            byte[] hashBytes = digest.digest();
            BigInteger hashValue = new BigInteger(1, hashBytes);

            int safeModulus = modulus > 0 ? modulus : 1;
            return hashValue.mod(BigInteger.valueOf(safeModulus)).intValue();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her standart JVM'de garanti olarak bulunur; pratikte buraya düşülmez.
            throw new IllegalStateException("SHA-256 algoritması bu ortamda bulunamadı.", e);
        }
    }
}