package com.zehrayt.hypercrypt.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.zehrayt.hypercrypt.dtos.KeyExchangeRequest;
import com.zehrayt.hypercrypt.dtos.KeyExchangeResult;
import com.zehrayt.hypercrypt.exception.InvalidRuleException;
import com.zehrayt.hypercrypt.service.CryptoService;


@RestController
@RequestMapping("/api/crypto")
public class CryptoController {

    private static final Logger log = LoggerFactory.getLogger(CryptoController.class);

    // kaba kuvvet aramasını hem deneme sayısı hem de süre olarak sınırlıyoruz (DoS koruması).
    private static final int MAX_BRUTEFORCE_ATTEMPTS = 100_000;
    private static final long MAX_BRUTEFORCE_MILLIS = 5_000;

    private final CryptoService cryptoService;

    @Autowired
    public CryptoController(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @PostMapping("/calculate-key") // Hem Public Key hem de Shared Secret'ı hesaplayacak
    public ResponseEntity<KeyExchangeResult> calculateKey(@RequestBody KeyExchangeRequest request) {
        try {
            // Hangi işlemi yapacağımıza karar veriyoruz.
            // Eğer karşıdan gelen anahtar null ise, genel anahtarı hesaplıyoruz.
            // Değilse, ortak sırrı hesaplıyoruz.

            int base = request.generator;
            Integer result;
            String explanation;

            // Eğer karşıdan gelen genel anahtar varsa, ortak sır hesaplanır
            // (SHA-256 ile türetilir); değilse genel anahtar üretilir (ham
            // değer, hash'lenmez — bkz. CryptoService.calculatePublicValue).
            if (request.theirPublicKey != null) {
                base = request.theirPublicKey; // İşlemin tabanı karşıdan gelen anahtar olur.
                explanation = "Ortak gizli anahtarınız hesaplandı.";
                result = cryptoService.calculateSharedSecret(
                    request.rule, base, request.privateKey, request.modulus);
            } else {
                explanation = "Genel anahtarınız hesaplandı.";
                result = cryptoService.calculatePublicValue(
                    request.rule, base, request.privateKey, request.modulus);
            }

            return ResponseEntity.ok(new KeyExchangeResult(result, explanation));

        } catch (InvalidRuleException | IllegalStateException e) {
            // Kontrollü hatalar (geçersiz kural, boş sonuç kümesi vb.) için 400 Bad Request.
            return ResponseEntity.badRequest().body(new KeyExchangeResult(null, "Hata: " + e.getMessage()));
        } catch (Exception e) {
            // Beklenmedik diğer tüm hatalar için 500 Internal Server Error.
            log.error("calculate-key sırasında beklenmedik hata", e);
            return ResponseEntity.status(500).body(
                new KeyExchangeResult(null, "Sunucuda beklenmedik bir hata oluştu."));
        }
    }

    // Eve'in ortak sırrı kırma denemesini simüle eden DTO
    public static class EveAttackRequest {
        public String rule;
        public Integer generator;
        public Integer modulus;
        public Integer publicKeyA;
        public Integer publicKeyB;
    }

    /**
     * Eve, Alice'in genel anahtarını (publicKeyA) üreten özel anahtarı
     * bulmak için gerçek bir kaba kuvvet (brute-force) araması yapıyor; bulursa
     * bu özel anahtarla ortak sırrı da hesaplayıp döndürüyor. Bu, küçük modulus
     * değerlerinin neden güvensiz olduğunu koddan da somut biçimde gösterir.
     */
    @PostMapping("/eve-attack")
    public KeyExchangeResult eveAttack(@RequestBody EveAttackRequest request) {
        if (request.rule == null || request.generator == null || request.modulus == null
                || request.publicKeyA == null || request.publicKeyB == null) {
            return new KeyExchangeResult(null,
                "Eve saldırısı için 'rule', 'generator', 'modulus', 'publicKeyA' ve 'publicKeyB' alanları gereklidir.");
        }

        int searchLimit = Math.min(Math.max(request.modulus, 0), MAX_BRUTEFORCE_ATTEMPTS);
        long startTime = System.currentTimeMillis();

        for (int candidatePrivateKey = 0; candidatePrivateKey < searchLimit; candidatePrivateKey++) {
            if (System.currentTimeMillis() - startTime > MAX_BRUTEFORCE_MILLIS) {
                return new KeyExchangeResult(null, String.format(
                    "Eve, %d denemeden sonra (zaman sınırına ulaşıldı) ortak sırrı bulamadı. " +
                    "Modulus büyüdükçe kaba kuvvet saldırısının maliyeti katlanarak artar.",
                    candidatePrivateKey));
            }

            try {
                Integer candidatePublicKey = cryptoService.calculatePublicValue(
                    request.rule, request.generator, candidatePrivateKey, request.modulus);

                if (candidatePublicKey != null && candidatePublicKey.equals(request.publicKeyA)) {
                    Integer recoveredSecret = cryptoService.calculateSharedSecret(
                        request.rule, request.publicKeyB, candidatePrivateKey, request.modulus);
                    long elapsedMillis = System.currentTimeMillis() - startTime;

                    return new KeyExchangeResult(recoveredSecret, String.format(
                        "GÜVENLİK UYARISI: Eve, %d. denemede (%d ms içinde) özel anahtarı (%d) buldu ve " +
                        "ortak sırrı hesapladı! Bu modulus (%d) değeri kaba kuvvet saldırısına karşı güvensizdir; " +
                        "gerçek bir protokolde çok daha büyük bir anahtar uzayı (modulus) gerekir.",
                        candidatePrivateKey + 1, elapsedMillis, candidatePrivateKey, request.modulus));
                }
            } catch (Exception e) {
                // Bu aday değer kuralı hata verdirdiyse (örn. sıfıra bölme), aramaya devam et.
            }
        }

        long elapsedMillis = System.currentTimeMillis() - startTime;
        return new KeyExchangeResult(null, String.format(
            "Eve, denenen %d olası özel anahtarın hiçbiriyle (%d ms içinde) ortak sırrı bulamadı.",
            searchLimit, elapsedMillis));
    }
}
