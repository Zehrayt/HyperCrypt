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

@Service
public class CryptoService {
    private final RuleParserService ruleParserService;

    @Autowired
    public CryptoService(RuleParserService ruleParserService) {
        this.ruleParserService = ruleParserService;
    }

    /**
     * Kriptografik işlemi (a op b) hiperhalka kuralıyla hesaplar.
     * @param rule Kural metni
     * @param base İşlemin sol tarafındaki değer (g veya karşıdan gelen anahtar)
     * @param exponent İşlemin sağ tarafındaki değer (gizli anahtar)
     * @param modulus Mod değeri (n)
     * @return Kriptografik amaçla kullanılan tek bir tamsayı sonucu.
     */
    public Integer calculate(String rule, int base, int exponent, int modulus) {
        // RuleParser'a gönderilecek sabitler.
        Map<String, Object> constants = Map.of("n", modulus);

        
        // RuleParserService zaten 'a' ve 'b'yi
        // gerçek fonksiyon parametresi olarak bağlıyor; bu yüzden kuralı hiç string olarak
        // değiştirmeden, doğrudan base/exponent değerleriyle çalıştırıyoruz.
        Set<Integer> resultSet = ruleParserService.parseRule(rule, constants).apply(base, exponent);

        if (resultSet.isEmpty()) {
            throw new IllegalStateException("Kriptografik işlem boş bir sonuç kümesi üretti.");
        }

        return deriveSharedValue(resultSet, modulus);
    }

    /**
     * Hiper-işlemin sonuç kümesinden tek bir paylaşılan değer türetir.
     *
     * DÜZELTME: Önceki yaklaşım Collections.max(resultSet) ile en büyük elemanı
     * seçiyordu. Bu seçim deterministik ama kriptografik olarak öngörülebilir ve
     * yanlıydı (küçük kümelerde sonuç kolayca tahmin edilebilir). Bunun yerine,
     * kümenin tüm elemanlarını SHA-256 ile karıştırıp modulus'a indirgeyerek
     * daha az öngörülebilir, hâlâ deterministik bir değer üretiyoruz.
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
