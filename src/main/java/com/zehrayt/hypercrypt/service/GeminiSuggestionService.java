package com.zehrayt.hypercrypt.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class GeminiSuggestionService {

    private final String apiKey = System.getenv("GEMINI_API_KEY");
    
    // Model adını 2.0-flash olarak güncelledim
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final RestTemplate restTemplate;

    public GeminiSuggestionService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); 
        factory.setReadTimeout(5000);   
        
        this.restTemplate = new RestTemplate(factory);
    }

    public String getSuggestion(String baseSet, String rule, String failingAxiom) {
        String prompt = String.format(
            """
            You are a helpful and encouraging expert in abstract algebra, specifically hyperstructure theory.
            A user has defined a structure that fails to be a hypergroup.
            Your task is to provide a simple, constructive suggestion on how they might fix this.
            Suggest a small change to the rule or the set. Keep it short (1-2 sentences).

            User data:
            - Base Set (H): %s
            - Hyper-operation rule: %s
            - Verification failed for: %s

            Example: "Your structure is almost there! To satisfy '%s', try ensuring every result is within the base set."
            """,
            baseSet, rule, failingAxiom, failingAxiom
        );

        String requestBody = """
        {
            "contents": [{
                "parts": [{
                    "text": "%s"
                }]
            }]
        }
        """.formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        String apiUrl = GEMINI_API_URL + apiKey;

        try {
            JsonNode response = restTemplate.postForObject(apiUrl, entity, JsonNode.class);
            if (response != null && response.has("candidates")) {
                return response.get("candidates").get(0).get("content").get("parts").get(0).get("text").asText();
            }
            return "AI suggestion could not be parsed.";
        } catch (Exception e) {
            System.err.println("Error while calling Gemini API: " + e.getMessage());
            return "AI suggestion could not be retrieved at this time.";
        }
    }
}
