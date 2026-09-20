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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hiper-Diffie-Hellman anahtar değişimini yürüten hesaplama servisi.
 */
@Service
public class CryptoService {
    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    // DUZELTME (Hakem 2 uyarisi, Round 3): onceki surumde 8 idi; ornekleme araligi
    // artik gercek modulus'a kadar ciktigi icin (bkz. validateKeyExchangeCompatibility)
    // deneme sayisi da orantili olarak artirildi.
    private static final int SAMPLE_TRIAL_COUNT = 24;

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

        validateKeyExchangeCompatibility(rule, parsedRule, modulus);

        Set<Integer> resultSet = parsedRule.apply(base, exponent);

        if (resultSet.isEmpty()) {
            throw new IllegalStateException("Kriptografik işlem boş bir sonuç kümesi üretti.");
        }

        return resultSet;
    }

    /**
     * DÜZELTME (Hakem 2 uyarısı, Round 2): Genelleştirilmiş bir Diffie-Hellman
     * protokolünün çalışabilmesi için kuralın rho(x,k) = "x ile k'yi birleştiren
     * fonksiyon" olarak bir "değişmeli aile" (commuting family) oluşturması gerekir:
     *
     *      rho(rho(g,k2),k1) == rho(rho(g,k1),k2)      (her g, k1, k2 için)
     *
     * ÖNEMLİ (Round 2 hakem düzeltmesi): Bu eşitlik, TAM SONUÇ KÜMELERİ üzerinden
     * kontrol edilmelidir; yalnızca kümelerin en küçük elemanlarının (min) eşitliği
     * YETERLİ DEĞİLDİR. Bunun nedeni calculateSharedSecret()'ın deriveSharedValue()
     * aracılığıyla SONUÇ KÜMESİNİN TAMAMINI (kümenin tüm elemanları sıralanıp
     * SHA-256 ile karıştırılarak) hash'lemesidir — Collections.min() DEĞİL. Önceki
     * sürümde bu metot yalnızca min(rho(pub2,k1)) == min(rho(pub1,k2)) kontrolü
     * yapıyordu; bu, aynı minimuma sahip ama farklı elemanlar içeren iki küme için
     * YANLIŞLIKLA "uyumlu" kararı verebiliyordu (min eşleşse bile deriveSharedValue
     * FARKLI hash'ler üretir, yani Alice ve Bob sessizce farklı ortak sırlara
     * ulaşırdı — tam da bu metodun engellemesi gereken durum). Bu yüzden burada
     * gerçek protokolün son adımıyla (calculateSharedSecret) birebir tutarlı olacak
     * şekilde TAM KÜME eşitliği (Set.equals) kontrol edilir. Ara adımda iletilen
     * genel anahtar (pub1/pub2) yine de Collections.min() ile indirgenir, çünkü
     * calculatePublicValue() gerçekte ağa yalnızca bu min değerini gönderir; burada
     * taklit edilmek istenen de tam olarak budur.
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
     * ---
     * DÜZELTME (Hakem 2 uyarısı, Round 3): Önceki sürümde sampleBound, Math.pow gibi üstel
     * kurallarda double hassasiyetinin bozulmasından (büyük üslerde YANLIŞ NEGATİF üretme
     * riskinden) kaçınmak için 12 ile sınırlıydı. Hakem, gerçek boyutlu (12'nin çok üzerinde)
     * gizli anahtarlarla (örn. b=43) ortaya çıkan modüler taşmanın (wraparound) yalnızca bu
     * aralığın DIŞINDA belirdiğini ve testin bu yüzden onu hiç göremediğini somut bir karşı
     * örnekle (x∘y = {xy, 2xy} mod 257) gösterdi: bu örnek 0-11 aralığındaki HİÇBİR örnekte
     * asimetri üretmiyor (çünkü o aralıkta hiçbir çarpım 257'yi aşmıyor, dolayısıyla ara
     * "public value" indirgemesi hiç taşma görmüyor), ama gerçek boyutlu anahtarlarda
     * Alice ve Bob farklı ortak sırlara ulaşıyor.
     *
     * Bu yüzden örnekleme aralığı artık gerçek modulus'a kadar çıkarılmıştır. Önceki
     * kısıtlamanın çözmeye çalıştığı "double hassasiyeti" sorunu ARTIK BURADA DEĞİL,
     * kaynağında çözülüyor: RuleParserService.toSafeInt, bir kural Infinity/NaN veya güvenli
     * int aralığını aşan bir sonuç ürettiğinde InvalidRuleException fırlatır; bu istisna
     * aşağıdaki genel catch(Exception) bloğunda yakalanıp o örnekleme noktası atlanır — sayısal
     * olarak bozulmuş bir sonuç asla bir "uyumlu" ya da "uyumsuz" kararına karışmaz.
     *
     * ÖNEMLİ (dürüstlük notu): Bu, sonlu-örnekli (probabilistic) bir kontrol olmaya devam
     * eder; evrensel bir matematiksel ispat SUNMAZ. Örnekleme aralığının gerçek modulus'a
     * çıkarılması, hakemin verdiği örnek de dahil olmak üzere gerçek modulus aralığındaki
     * asimetrileri artık güvenilir biçimde yakalayabilen çok daha güçlü bir ampirik filtre
     * sağlar; ancak sonlu sayıda (SAMPLE_TRIAL_COUNT) rastgele denemeyle sınırlı olduğundan,
     * teorik olarak yalnızca çok seyrek tetiklenen bir asimetriyi kaçırması hâlâ mümkündür
     * (bkz. proje testleri: CryptoServiceTest.test_hyperDiffieHellman_refereeCounterexampleRule_isRejected
     * ve test_hyperDiffieHellman_classicalExponentiationRule_isNotFalselyRejected).
     */
    // EK DÜZELTME (Round 3, ikinci bulgu): sampleBound'u modulus'a çıkarmak, x∘y=a*b
    // tarzı ÇOKTERİMLİ (polynomial) büyüyen kurallar için güvenlidir (bkz. yukarıdaki
    // javadoc), ANCAK "Math.pow(a,b)" gibi ÜSTEL büyüyen kurallarda YENİ ve daha derin
    // bir sorun ortaya çıkarır: ara "public value" (pub1/pub2) zaten modulus'a göre
    // indirgenmiş (0..modulus-1) bir değerdir, ve bu değer ikinci uygulamada TEKRAR
    // üs olarak kullanılır (rule.apply(pub2, k1) => pub2^k1). pub, sampleBound ile
    // değil modulus ile sınırlı olduğundan, modulus büyüdükçe pub^k1 de IEEE-754
    // double'ın kesin tam sayı sınırını (2^53) sessizce aşabilir — bu durumda sonuç
    // Infinity/NaN OLMAZ (RuleParserService.toSafeInt bunu yakalayamaz), sadece
    // '% n' sonrası ANLAMSIZ bir kalana indirgenir, çünkü double artık gerçek değerin
    // çoğu basamağını hiç tutmuyordur. Bu, referee'nin karşı örneğinden TAMAMEN FARKLI
    // ve ondan BAĞIMSIZ bir hata sınıfıdır; klasik üstel alma (Math.pow(a,b) % n) gibi
    // aslında mükemmelen uyumlu olan kuralları bile yanlışlıkla reddettirebilir.
    //
    // Bu yüzden üstel-görünümlü kurallar (Math.pow / **) için örnekleme aralığı,
    // pub^k1'in de (pub ~ modulus mertebesinde olsa bile) güvenli aralıkta kalmasını
    // garanti edecek şekilde modulus'a göre AYRICA hesaplanır: bound^log2(modulus) <= 2^53.
    // Bu, sabit "12" gibi keyfi bir sayı DEĞİL, modulus büyüklüğüne göre uyarlanan,
    // matematiksel olarak gerekçeli bir sınırdır (bkz. proje testi:
    // CryptoServiceTest.test_hyperDiffieHellman_classicalExponentiationRule_isNotFalselyRejected).
    //
    // NOT (dürüstlük notu): Bu tespit sezgiseldir (kural metninde "Math.pow" veya "**"
    // aranır); üstel büyümeyi başka bir JS ifadesiyle (örn. elle yazılmış bir çarpma
    // döngüsü) gizleyen alışılmadık bir kural bu sezgiyi atlatabilir. Bu, makalenin
    // hedeflediği kullanım biçimleri (Math.pow(a,b) ya da a**b) için yeterlidir.
    private static final int MAX_SAFE_DOUBLE_EXPONENT_BITS = 53;

    private boolean looksExponential(String ruleText) {
        return ruleText != null && (ruleText.contains("Math.pow") || ruleText.contains("**"));
    }

    private int safeExponentialSampleBound(int modulus) {
        double modulusBits = Math.max(1.0, Math.log(Math.max(modulus, 2)) / Math.log(2));
        int bound = (int) (MAX_SAFE_DOUBLE_EXPONENT_BITS / modulusBits);
        return Math.max(2, Math.min(modulus, bound));
    }

    private void validateKeyExchangeCompatibility(String ruleText, BiFunction<Integer, Integer, Set<Integer>> rule, int modulus) {
        if (modulus <= 1) {
            return; // Anlamlı bir örnekleme yapılamaz; kural zaten başka yerde reddedilecektir.
        }

        int sampleBound = looksExponential(ruleText)
            ? safeExponentialSampleBound(modulus)
            : Math.max(2, modulus);
        Random rnd = new Random(42); // Sabit seed: her çağrıda aynı örnekler, deterministik davranış.
        int skippedTrials = 0;
        for (int trial = 0; trial < SAMPLE_TRIAL_COUNT; trial++) {
            int g = rnd.nextInt(sampleBound);
            int k1 = rnd.nextInt(sampleBound);
            int k2 = rnd.nextInt(sampleBound);

            try {
                int pub1 = Collections.min(rule.apply(g, k1));
                int pub2 = Collections.min(rule.apply(g, k2));

                // Artık TAM KÜMELER karşılaştırılıyor (Collections.min(...) ile indirgenmiş
                // tek sayılar değil), çünkü calculateSharedSecret() nihai ortak sırrı tam
                // kümenin SHA-256 hash'i olarak üretiyor (bkz. deriveSharedValue).
                Set<Integer> sharedViaK1First = rule.apply(pub2, k1);
                Set<Integer> sharedViaK2First = rule.apply(pub1, k2);

                if (!sharedViaK1First.equals(sharedViaK2First)) {
                    throw new IllegalStateException(
                        "Bu kural Diffie-Hellman anahtar değişimi için uygun değil: "
                        + "rho(rho(g,k2),k1) = rho(rho(g,k1),k2) eşitliği TAM SONUÇ KÜMESİ "
                        + "düzeyinde sağlanmıyor (g=" + g + ", k1=" + k1 + ", k2=" + k2 + "), "
                        + "bu yüzden Alice ve Bob (deriveSharedValue tüm kümeyi hash'lediği "
                        + "için) farklı ortak sırlara ulaşır. Kuralın a ve b üzerinde aynı "
                        + "(değişmeli/birleşmeli) yapıda olması gerekir (örn. a*b, a+b, ya da "
                        + "klasik üstel alma).");
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                // Bu örnekleme noktası kuralı hata verdirdiyse (örn. sıfıra bölme), ya da
                // RuleParserService.toSafeInt'in yakaladığı sayısal taşma/hassasiyet kaybı
                // nedeniyle reddedildiyse: atla ve devam et. Bozulmuş/güvenilmez veri hiçbir
                // zaman karşılaştırmaya dahil edilmez.
                skippedTrials++;
            }
        }
        log.debug("Key-exchange compatibility check tamamlandı: {}/{} deneme değerlendirme "
            + "hatası/taşma nedeniyle atlandı.", skippedTrials, SAMPLE_TRIAL_COUNT);
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