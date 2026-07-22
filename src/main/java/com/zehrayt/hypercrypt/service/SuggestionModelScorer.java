package com.zehrayt.hypercrypt.service;

import ai.onnxruntime.OnnxMap;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mutasyonun hipergrup oluşturma olasılığını tahmin eden ONNX modelini çalıştırır.
 * Üretilen skor sadece RuleSuggestionEngine için öncelik sırası belirler, kullanıcıya gösterilmez.
 * Model hata verirse sistem çökmez, nötr (0.5) döner. Nihai doğrulama daima AxiomVerifier'a aittir.
 */

@Service
public class SuggestionModelScorer {

    private static final Logger log = LoggerFactory.getLogger(SuggestionModelScorer.class);

    private static final String MODEL_RESOURCE = "models/hypercrypt_suggestion_model.onnx";
    private static final String FEATURES_RESOURCE = "models/hypercrypt_suggestion_features.json";
    private static final float NEUTRAL_SCORE = 0.5f;
    private static final Pattern INT_LITERAL = Pattern.compile("(?<![\\w.])\\d+(?![\\w.])");

    private OrtEnvironment environment;
    private OrtSession session;
    private List<String> featureOrder;
    private volatile boolean available;

    public SuggestionModelScorer() {
        try {
            loadModel();
            available = true;
            log.info("Kural önerisi modeli yüklendi ({} özellik).", featureOrder.size());
        } catch (Exception e) {
            available = false;
            log.warn("Kural önerisi modeli yüklenemedi, modelsiz devam ediliyor: {}", e.getMessage());
        }
    }

    private void loadModel() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream featuresStream = new ClassPathResource(FEATURES_RESOURCE).getInputStream()) {
            JsonNode root = mapper.readTree(featuresStream);
            featureOrder = new ArrayList<>();
            for (JsonNode node : root.get("feature_order")) {
                featureOrder.add(node.asText());
            }
        }

        byte[] modelBytes;
        try (InputStream modelStream = new ClassPathResource(MODEL_RESOURCE).getInputStream()) {
            modelBytes = modelStream.readAllBytes();
        }

        environment = OrtEnvironment.getEnvironment();
        session = environment.createSession(modelBytes, new OrtSession.SessionOptions());
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * candidateRule'un (mutationType mutasyonuyla üretilmiş) hipergrup
     * aksiyomlarını sağlama ihtimalini [0,1] aralığında tahmin eder. Model
     * kullanılamıyorsa ya da bir hata oluşursa NEUTRAL_SCORE döner.
     */
    public float scoreCandidate(String candidateRule,
                                 BiFunction<Integer, Integer, Set<Integer>> operation,
                                 int n,
                                 String mutationType) {
        if (!isAvailable()) {
            return NEUTRAL_SCORE;
        }
        try {
            float[] featureVector = buildFeatureVector(candidateRule, operation, n, mutationType);
            return runInference(featureVector);
        } catch (Exception e) {
            log.warn("Model skorlaması sırasında hata oluştu, model devre dışı bırakılıyor: {}", e.getMessage());
            available = false;
            return NEUTRAL_SCORE;
        }
    }

    private float runInference(float[] featureVector) throws Exception {
        long[] shape = {1, featureVector.length};
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(environment, java.nio.FloatBuffer.wrap(featureVector), shape)) {
            String inputName = session.getInputNames().iterator().next();
            try (OrtSession.Result result = session.run(Map.of(inputName, inputTensor))) {
                return extractPositiveProbability(result);
            }
        }
    }

    // skl2onnx'in ürettiği çıktı şekli sürüme göre değişebilir (liste-içinde-map, düz map, veya düz tensor).
    // Bilinen şekillerin hepsini sırayla dener; hiçbiri uymazsa gerçek türü loglayıp öğrenmek için hata fırlatır.
    private float extractPositiveProbability(OrtSession.Result result) throws Exception {
        for (Map.Entry<String, OnnxValue> entry : result) {
            if (!entry.getKey().toLowerCase().contains("probab")) {
                continue;
            }
            Object value = entry.getValue().getValue();
            Float extracted = tryExtractPositive(value);
            if (extracted != null) {
                return extracted;
            }
            log.warn("output_probability beklenmeyen bir türde geldi: {}", describe(value));
        }
        for (Map.Entry<String, OnnxValue> entry : result) {
            if (!entry.getKey().toLowerCase().contains("label")) {
                continue;
            }
            Float fromLabel = tryExtractLabelAsScore(entry.getValue().getValue());
            if (fromLabel != null) {
                return fromLabel;
            }
        }
        throw new IllegalStateException("Modelden beklenen olasılık çıktısı okunamadı.");
    }

    @SuppressWarnings("unchecked")
    private Float tryExtractPositive(Object value) throws Exception {
        // Şekil 1: her satır için {sınıf: olasılık} eşlemesi içeren bir liste (ZipMap, batch).
        // onnxruntime-java bu diziyi List<OnnxMap> olarak döner; OnnxMap kendi getValue()'su çağrılmadan düz bir Map'e dönüşmez.
        if (value instanceof List<?> rows && !rows.isEmpty()) {
            Object firstRow = rows.get(0);
            if (firstRow instanceof OnnxMap onnxMap) {
                Object rowValue = onnxMap.getValue();
                if (rowValue instanceof Map<?, ?> rowMap) {
                    return lookupPositiveClass(rowMap);
                }
            }
            if (firstRow instanceof Map<?, ?> rowMap) {
                return lookupPositiveClass(rowMap);
            }
            if (firstRow instanceof Number number) {
                return number.floatValue();
            }
        }
        // Şekil 2: tek satırlık batch için doğrudan bir map (ZipMap, sarmalanmamış).
        if (value instanceof Map<?, ?> rowMap) {
            return lookupPositiveClass(rowMap);
        }
        // Şekil 3: düz tensor, [1,2] şeklinde (negatif, pozitif olasılık).
        if (value instanceof float[][] matrix && matrix.length > 0 && matrix[0].length > 1) {
            return matrix[0][1];
        }
        // Şekil 4: düz tensor, [2] şeklinde.
        if (value instanceof float[] vector && vector.length > 1) {
            return vector[1];
        }
        return null;
    }

    private Float lookupPositiveClass(Map<?, ?> rowMap) {
        for (Object key : new Object[]{1L, 1, 1.0, "1"}) {
            Object positive = rowMap.get(key);
            if (positive instanceof Number number) {
                return number.floatValue();
            }
        }
        return null;
    }

    // output_probability hiç okunamazsa, en azından output_label'ı kaba bir skor olarak kullan.
    private Float tryExtractLabelAsScore(Object value) {
        Object labelValue = value;
        if (value instanceof long[] arr && arr.length > 0) {
            labelValue = arr[0];
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            labelValue = list.get(0);
        }
        if (labelValue instanceof Number number) {
            return number.longValue() == 1L ? 0.75f : 0.25f;
        }
        return null;
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        String className = value.getClass().getName();
        if (value instanceof List<?> list && !list.isEmpty()) {
            return className + " (ilk eleman: " + list.get(0).getClass().getName() + ")";
        }
        return className;
    }

    private float[] buildFeatureVector(String rule,
                                        BiFunction<Integer, Integer, Set<Integer>> operation,
                                        int n,
                                        String mutationType) {
        int sampleSize = Math.min(n, 5);
        int symmetricMatches = 0;
        int totalPairs = 0;
        for (int a = 0; a < sampleSize; a++) {
            for (int b = 0; b < sampleSize; b++) {
                if (a == b) {
                    continue;
                }
                totalPairs++;
                if (safeApply(operation, a, b).equals(safeApply(operation, b, a))) {
                    symmetricMatches++;
                }
            }
        }
        double symmetryRatio = totalPairs > 0 ? (double) symmetricMatches / totalPairs : 1.0;

        Map<String, Float> values = new HashMap<>();
        values.put("n", (float) n);
        values.put("rule_length", (float) rule.length());
        values.put("has_multiplication", rule.contains("*") ? 1f : 0f);
        values.put("has_cross_term_ab", (rule.contains("a*b") || rule.contains("a * b")) ? 1f : 0f);
        values.put("num_int_literals", (float) countIntLiterals(rule));
        values.put("has_variable_a", rule.contains("a") ? 1f : 0f);
        values.put("has_variable_b", rule.contains("b") ? 1f : 0f);
        values.put("symmetry_ratio", (float) (Math.round(symmetryRatio * 1000.0) / 1000.0));
        values.put("mut_coefficient_sweep", "coefficient_sweep".equals(mutationType) ? 1f : 0f);
        values.put("mut_missing_variable_fix", "missing_variable_fix".equals(mutationType) ? 1f : 0f);
        values.put("mut_operator_swap", "operator_swap".equals(mutationType) ? 1f : 0f);
        values.put("mut_term_drop", "term_drop".equals(mutationType) ? 1f : 0f);

        float[] vector = new float[featureOrder.size()];
        for (int i = 0; i < featureOrder.size(); i++) {
            Float v = values.get(featureOrder.get(i));
            vector[i] = v != null ? v : 0f;
        }
        return vector;
    }

    private Set<Integer> safeApply(BiFunction<Integer, Integer, Set<Integer>> operation, int a, int b) {
        try {
            Set<Integer> result = operation.apply(a, b);
            return result != null ? result : Set.of();
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    private int countIntLiterals(String rule) {
        Matcher matcher = INT_LITERAL.matcher(rule);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
