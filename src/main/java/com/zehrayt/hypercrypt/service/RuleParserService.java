package com.zehrayt.hypercrypt.service;

import com.zehrayt.hypercrypt.exception.InvalidRuleException;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

@Service
public class RuleParserService {

    private static final Logger log = LoggerFactory.getLogger(RuleParserService.class);

    // DoS koruması: Standart Context.enter() ile çalışan bir Rhino scripti
    // süresiz bir döngüye girerse (örn. "(()=>{while(true){}})()" kara listedeki
    // "function" kelimesini içermez ama sonsuz döngüdür) sunucu thread'i sonsuza
    // kadar kilitlenir. Bu özel ContextFactory, belirli bir komut (instruction) sayısı
    // aşıldığında scripti zorla durdurur.
    private static final int MAX_INSTRUCTIONS = 1_000_000;

    private static final ContextFactory SAFE_CONTEXT_FACTORY = new ContextFactory() {
        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            throw new InvalidRuleException(
                "Kural çalışma süresi sınırını aştı (olası sonsuz döngü). Kuralı basitleştirip tekrar deneyin.");
        }
    };

    private Context enterSafeContext() {
        Context cx = SAFE_CONTEXT_FACTORY.enterContext();
        cx.setInstructionObserverThreshold(MAX_INSTRUCTIONS);
        // Rhino'nun modern JS söz dizimini (=>, let/const) desteklemesi için ES6 aktiftir.
        // Bu ayar, fuzz testlerindeki modern payload'ların parser hatasına düşmek yerine, 
        // asıl güvenlik katmanı olan instruction-count limitinde test edilmesini sağlar.
        cx.setLanguageVersion(Context.VERSION_ES6);
        return cx;
    }

    // Kural metni için makul bir üst sınır; hiper-işlem kuralları kısa
    // matematiksel ifadelerdir, bu boyutu aşan hiçbir meşru kural olmamalı.
    private static final int MAX_RULE_LENGTH = 500;

    /**
     * Kara liste tabanlı hızlı ön-kontrol (Fast-Fail).
     * UYARI: Bu bir güvenlik sınırı DEĞİLDİR (string birleştirme vb. ile kolayca atlatılabilir).
     * Asıl güvenlik 'initSafeStandardObjects()' ve instruction limitleridir. 
     * Amaç: Bariz saldırıları JS motorunu hiç yormadan hızlıca reddetmek.
     */
    private boolean isObviouslyMalicious(String rule) {
        String lowerRule = rule.toLowerCase();
        String[] suspiciousKeywords = {
            "java", "system", "eval", "function", "process", "require", "import", "exec", "class",
            "constructor", "prototype", "__proto__"
        };
        for (String keyword : suspiciousKeywords) {
            if (lowerRule.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // BELLEK BOMBASI (Memory-Bomb) KORUMASI
    // Rhino'nun instruction limiti, "x".repeat() gibi devasa bellek tüketen native 
    // metodları tek adım sayar. Kurallar sadece sayısal işlemlere ihtiyaç duyduğundan, 
    // bu riskli string/array metodları scope'tan kaldırılarak zafiyet kapatılmıştır.
    private void hardenScopeAgainstMemoryAmplification(Scriptable scope) {
        removePrototypeMember(scope, "String", "repeat");
        removePrototypeMember(scope, "String", "padStart");
        removePrototypeMember(scope, "String", "padEnd");
        removePrototypeMember(scope, "Array", "join");
        removePrototypeMember(scope, "Array", "fill");
    }

    private void removePrototypeMember(Scriptable scope, String constructorName, String memberName) {
        Object constructor = scope.get(constructorName, scope);
        if (constructor instanceof Scriptable) {
            Object prototype = ((Scriptable) constructor).get("prototype", (Scriptable) constructor);
            if (prototype instanceof Scriptable) {
                ((Scriptable) prototype).delete(memberName);
            }
        }
    }

    public BiFunction<Integer, Integer, Set<Integer>> parseRule(String ruleString, Map<String, Object> constants) {
        if (ruleString == null || ruleString.isBlank()) {
            throw new InvalidRuleException("Kural metni boş olamaz.");
        }

        if (ruleString.length() > MAX_RULE_LENGTH) {
            throw new InvalidRuleException(
                "Kural metni çok uzun (maksimum " + MAX_RULE_LENGTH + " karakter).");
        }

        // Hızlı ön-kontrol: bariz saldırı denemelerini JS motorunu hiç
        // çalıştırmadan reddet. Asıl güvenlik initSafeStandardObjects() ve
        // instruction-count limitiyle sağlanıyor (bkz. isObviouslyMalicious).
        if (isObviouslyMalicious(ruleString)) {
            log.warn("Reddedilen kural (kara liste ön-kontrolü): {}", ruleString);
            throw new InvalidRuleException("Güvenlik ihlali: Kural metninde izin verilmeyen zararlı komutlar tespit edildi.");
        }

        final String functionWrapper = String.format("function(a, b) { return %s; }", ruleString);

        // Bu, daha sonra kullanılacak olan ana scope (çalışma alanı).
        final Scriptable mainScope;

        // Derleme işlemini dışarı alıyoruz
        Context rhinoContext = enterSafeContext();
        try {
            rhinoContext.setOptimizationLevel(-1);

            // GÜVENLİK ADIMI 2: initStandardObjects YERİNE "initSafeStandardObjects" KULLANIMI
            // Bu metot, Rhino motorunun Java sınıflarına (Packages, java.lang vb.) erişimini tamamen kapatır.
            mainScope = rhinoContext.initSafeStandardObjects();

            // GÜVENLİK ADIMI 3: Bellek tüketimi (memory-bomb) koruması.
            hardenScopeAgainstMemoryAmplification(mainScope);

             // Kuralda kullanılabilecek sabitleri scope'a ekleyelim.
             // Örneğin, 'n' veya 'p' gibi sabitler kuralda kullanılabilir.
             // Bu sabitler, frontend tarafından sağlanabilir ve burada güvenli bir şekilde tanımlanır.
             // Böylece kullanıcı, kuralında 'n' veya 'p' gibi değişkenler kullanabilir ve bunların değerlerini de belirleyebilir.

            // Sabitleri scope'a burada ekliyoruz.
            if (constants != null) {
                for (Map.Entry<String, Object> entry : constants.entrySet()) {
                    Object jsValue = Context.javaToJS(entry.getValue(), mainScope);
                    mainScope.put(entry.getKey(), mainScope, jsValue);
                }
            }

            // Kuralın sözdizimini burada, en başta kontrol ediyoruz.
            // Eğer "a +* b" gibi bir hata varsa, Exception burada fırlatılacak.
            rhinoContext.compileFunction(mainScope, functionWrapper, "rule", 1, null);

        } catch (InvalidRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRuleException("Kuralda sözdizimi hatası var: " + e.getMessage());
        } finally {
            Context.exit();
        }

        return (a, b) -> {
            Context executionContext = enterSafeContext();
            try {
                // Her çalıştırmada, daha önce oluşturduğumuz ve sabitleri içeren scope'u kullanıyoruz.
                // Ve fonksiyonu yeniden derleyip çalıştırıyoruz.
                Function jsFunction = executionContext.compileFunction(mainScope, functionWrapper, "rule", 1, null);

                Object result = jsFunction.call(executionContext, mainScope, mainScope, new Object[]{a, b});
                Set<Integer> resultSet = new HashSet<>();

                if (result instanceof Number) {
                    resultSet.add(((Number) result).intValue());
                } else if (result instanceof NativeArray) {
                    NativeArray nativeArray = (NativeArray) result;
                    for (Object item : nativeArray) {
                        if (item instanceof Number) {
                            resultSet.add(((Number) item).intValue());
                        }
                    }
                } else if (result != null && "undefined".equals(Context.toString(result))) {
                    return resultSet;
                } else {
                     throw new InvalidRuleException(
                        String.format("Kural, beklenmedik bir sonuç tipi döndürdü. Sayı veya dizi bekleniyordu, ancak '%s' geldi.", result != null ? result.getClass().getSimpleName() : "null")
                    );
                }
                return resultSet;
            } catch (InvalidRuleException e) {
                throw e;
            } catch (Exception e) {
                throw new InvalidRuleException("Kural çalıştırılırken hata oluştu: " + e.getMessage());
            } finally {
                Context.exit();
            }
        };
    }
}
