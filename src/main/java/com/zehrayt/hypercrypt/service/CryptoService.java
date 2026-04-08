package com.zehrayt.hypercrypt.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
        // RuleParser'a gönderilecek sabitler: 'n' ve 'b' yerine 'exponent' değeri
        Map<String, Object> constants = Map.of("n", modulus); 
        
        // Kuralı parse et ve 'a' ve 'b' yerine değerleri koyarak hesapla.
        // NOT: Frontend'deki kod 'Math.pow(a, b) % p' gibi klasik formüller kullanacak.
        // Bizim motorumuz bunu 'a' ve 'b' değişkenleriyle çalıştıracak.
        
        // Buradaki 'b' değeri kuralın içinde 'exponent' olarak kullanıldığı için,
        // bizim kuralı manipüle etmemiz gerekiyor: rule.replace("b", String.valueOf(exponent))

        // * BASİT AMA GÜVENİLİR YAKLAŞIM *
        // Kural metnini, a yerine base, b yerine exponent gelecek şekilde değiştirip RuleParser'a gönderelim.
        String finalRule = rule.replace("a", String.valueOf(base)).replace("b", String.valueOf(exponent));
        
        // Şimdi RuleParser'a sadece tek bir sayı hesaplamasını söyleyelim.
        // Bu, kuralın kendisi (Math.pow(a, b)%p) tek bir sonuç döndüreceği için güvenlidir.
        Set<Integer> resultSet = ruleParserService.parseRule(finalRule, constants).apply(1, 1); 

        if (resultSet.isEmpty()) {
            throw new IllegalStateException("Kriptografik işlem boş bir sonuç kümesi üretti.");
        }
        
        //return resultSet.iterator().next();

        // (Not: Bu bir eğitim simülasyonu olduğu için hash fonksiyonu yerine en büyük eleman seçilmiştir.)
        return Collections.max(resultSet);
    }
}