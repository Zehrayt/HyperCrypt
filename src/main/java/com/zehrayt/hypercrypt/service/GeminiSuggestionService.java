package com.zehrayt.hypercrypt.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode; 

@Service // Bu sınıfın bir Spring servisi olduğunu belirtiyoruz.
public class GeminiSuggestionService {

    // --- ÇOK ÖNEMLİ: API ANAHTARINI GÜVENLİ YÖNETME ---
    private final String apiKey = System.getenv("GEMINI_API_KEY");
    
    private static final String GEMINI_API_URL = "";

    private final RestTemplate restTemplate;

    public GeminiSuggestionService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Başarısız olan bir hiperyapı için Gemini'den düzeltme önerisi ister.
     * @param baseSet Kullanıcının girdiği temel küme.
     * @param rule Kullanıcının girdiği kural.
     * @param failingAxiom Başarısız olan aksiyomun adı.
     * @return Gemini tarafından üretilen öneri metni.
     */
    public String getSuggestion(String baseSet, String rule, String failingAxiom) {
        
        // Gemini'ye göndereceğimiz prompt'u (istem metnini) tasarlıyoruz.
        String prompt = String.format(
            """
            You are a helpful and encouraging expert in abstract algebra, specifically hyperstructure theory.
            A user has defined a structure that fails to be a hypergroup.
            Your task is to provide a simple, constructive suggestion to the user on how they might fix this.
            Suggest a small, concrete change to the rule or the set. Keep your answer short and direct (1-2 sentences).

            Here is the user's data:
            - Base Set (H): %s
            - Hyper-operation rule (a ο b): %s
            - Verification failed for: %s

            Example Response: "Your structure is almost there! To satisfy the '%s', you could try changing your rule to 'a+b and a' to ensure every element is included in the results."
            """,
            baseSet, rule, failingAxiom, failingAxiom
        );

        // Gemini API'sine gönderilecek JSON gövdesini hazırlıyoruz.
        // Gemini'nin beklediği format OpenAI'den biraz farklıdır.
        String requestBody = """
        {
            "contents": [{
                "parts": [{
                    "text": "%s"
                }]
            }]
        }
        """.formatted(prompt.replace("\"", "\\\"")); // Prompt içindeki tırnak işaretlerinden kaçınıyoruz.

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        
        String apiUrl = GEMINI_API_URL + apiKey;

        try {
            // API'ye POST isteği gönderiyoruz.
            JsonNode response = restTemplate.postForObject(apiUrl, entity, JsonNode.class);
            
            // Gelen JSON cevabının içinden metni çekiyoruz.
            // Cevap formatı: { "candidates": [ { "content": { "parts": [ { "text": "öneri metni" } ] } } ] }
            if (response != null && response.has("candidates")) {
                return response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            }
            return "AI suggestion could not be parsed.";
        } catch (Exception e) {
            System.err.println("Error while calling Gemini API: " + e.getMessage());
            // Hata durumunda standart bir mesaj döndürüyoruz.
            return "AI suggestion could not be retrieved at this time.";
        }
    }
}