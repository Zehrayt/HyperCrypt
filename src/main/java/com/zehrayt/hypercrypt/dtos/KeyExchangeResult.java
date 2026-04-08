package com.zehrayt.hypercrypt.dtos;

public class KeyExchangeResult {
    public Integer result;
    public String explanation;

    /**
     * Yeni bir KeyExchangeResult nesnesi oluşturur.
     * @param result Hesaplanan kriptografik sonuç (genel anahtar veya ortak sır).
     * @param explanation İşlemin ne olduğunu açıklayan metin.
     */
    public KeyExchangeResult(Integer result, String explanation) {
        this.result = result;

        this.explanation = explanation;
    }

    // Boş bir constructor da eklemek iyi bir pratiktir (bazı kütüphaneler için gerekli olabilir).
    public KeyExchangeResult() {
    }
}