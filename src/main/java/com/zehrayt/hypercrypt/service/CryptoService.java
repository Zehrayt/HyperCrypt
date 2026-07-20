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
import java.util.Set;

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
     */
    private Set<Integer> evaluate(String rule, int base, int exponent, int modulus) {
        Map<String, Object> constants = Map.of("n", modulus);
        Set<Integer> resultSet = ruleParserService.parseRule(rule, constants).apply(base, exponent);

        if (resultSet.isEmpty()) {
            throw new IllegalStateException("Kriptografik işlem boş bir sonuç kümesi üretti.");
        }

        return resultSet;
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
