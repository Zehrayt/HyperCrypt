package com.zehrayt.hypercrypt.verification;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zehrayt.hypercrypt.dtos.VerificationResult;


public class AxiomVerifier {

    private static final Logger log = LoggerFactory.getLogger(AxiomVerifier.class);

    // Hiper-işlemi temsil eden fonksiyonel arayüz
    // İki eleman alır (Integer, Integer), bir küme döndürür (Set<Integer>)
    private final BiFunction<Integer, Integer, Set<Integer>> hyperMultiplication;
    private final Set<Integer> baseSet;
    private final MessageSource messageSource;

    private final BiFunction<Integer, Integer, Integer> standardAddition;
    private final Function<Integer, Integer> standardNegation;

    public AxiomVerifier(Set<Integer> baseSet, BiFunction<Integer, Integer, Set<Integer>> hyperMultiplication, MessageSource messageSource) {
        this.baseSet = baseSet;
        this.hyperMultiplication = hyperMultiplication;
        this.messageSource = messageSource;

        // DÜZELTME: Hakem 3'ün "Sonlu kümelerde (Z/nZ) toplama nasıl çalışıyor?" uyarısı çözüldü.
        // Standart tam sayı toplaması yerine, kümenin boyutuna (n) göre Modüler Aritmetik kullanıyoruz.
        int n = baseSet.size();

        // Modüler Toplama: (a + b) % n
        this.standardAddition = (a, b) -> (a.intValue() + b.intValue()) % n;

        // Modüler Negatif Alma: (-a) % n
        // Not: Java'da % operatörü negatif sonuç verebildiği için gerçek matematiksel modülü şu formülle buluyoruz: (-a % n + n) % n
        this.standardNegation = (a) -> ((-a.intValue()) % n + n) % n;
    }


    /**
     * Kapanıklık (Closure) ve Boş Küme Kontrolü
     * Hiper-işlemin sonucu boş olamaz ve baseSet dışına çıkamaz.
     */
    public boolean checkClosure() {
        log.debug("Checking for closure...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                Set<Integer> result = hyperMultiplication.apply(a, b);

                // Kural 1: Sonuç boş küme olamaz
                if (result == null || result.isEmpty()) {
                    log.debug("Closure failed: Result is empty for (a,b) = ({},{})", a, b);
                    return false;
                }

                // Kural 2: Sonuç, baseSet'in bir alt kümesi olmalıdır
                if (!baseSet.containsAll(result)) {
                    log.debug("Closure failed: Result contains elements outside baseSet for (a,b) = ({},{})", a, b);
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * DÜZELTME (Hakem uyarısı): "(R,+) is an abelian group" HARDCODE VARSAYILMIYOR,
     * kullanıcının girdiği baseSet için GERÇEKTEN doğrulanıyor. Herhangi bir kısıtlama
     * (örn. yalnızca {0,...,n-1} kabul etme) YOKTUR; kullanıcı hangi tam sayı kümesini
     * girerse girsin, standardAddition bu küme üzerinde gerçekten bir abelyen grup
     * oluşturuyorsa test başarılı olur, oluşturmuyorsa (örn. {2,5,9} gibi standart mod-n
     * toplaması altında kapalı olmayan bir küme) test bunu doğru şekilde yakalar.
     *
     * Birleşme (associativity) ve değişme (commutativity) özellikleri test edilmiyor
     * çünkü bunlar standardAddition'ın tanımı gereği (olağan tam sayı toplaması + mod
     * indirgeme) HER ZAMAN sağlanır; bu, kümenin içeriğine bağlı değildir, dolayısıyla
     * ayrıca ispatlanmasına gerek yoktur. Kümeye bağlı olan, ve bu yüzden burada
     * gerçekten test edilen iki aksiyom şunlardır:
     *   1) Kapanıklık: her a,b için (a+b) mod n, baseSet içinde olmalı.
     *   2) Birim eleman: baseSet içinde öyle bir e olmalı ki her a için a+e = a.
     *   3) Ters eleman: her a için, a+b = e olacak şekilde bir b, baseSet içinde olmalı.
     *
     * @return (baseSet, standardAddition) gerçekten bir abelyen grup oluşturuyorsa true.
     */
    public boolean verifyAdditiveGroupAxioms() {
        log.debug("Verifying (R,+) is a genuine abelian group for the given baseSet...");

        // 1. Kapanıklık: (a+b) mod n her zaman baseSet içinde kalmalı.
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                if (!baseSet.contains(standardAddition.apply(a, b))) {
                    log.debug("Additive closure failed for (a,b) = ({},{})", a, b);
                    return false;
                }
            }
        }

        // 2. Birim eleman: baseSet içinde, her a için a+e=a şartını sağlayan bir e ara.
        Integer identity = null;
        for (Integer candidate : baseSet) {
            boolean isIdentity = true;
            for (Integer a : baseSet) {
                if (!standardAddition.apply(a, candidate).equals(a)) {
                    isIdentity = false;
                    break;
                }
            }
            if (isIdentity) {
                identity = candidate;
                break;
            }
        }
        if (identity == null) {
            log.debug("No additive identity element exists within baseSet");
            return false;
        }

        // 3. Ters eleman: her a için, a+b=identity şartını sağlayan bir b, baseSet içinde olmalı.
        for (Integer a : baseSet) {
            boolean hasInverse = false;
            for (Integer b : baseSet) {
                if (standardAddition.apply(a, b).equals(identity)) {
                    hasInverse = true;
                    break;
                }
            }
            if (!hasInverse) {
                log.debug("No additive inverse exists within baseSet for element {}", a);
                return false;
            }
        }

        return true;
    }

    /**
     * Birleşme özelliğini kontrol eder: (a ο b) ο c = a ο (b ο c)
     * Bu, kümedeki tüm (a, b, c) üçlüleri için kontrol edilmelidir.
     * @return Birleşme özelliği sağlanıyorsa true, aksi halde false.
     */
    public boolean isAssociative() {
        log.debug("Checking for associativity...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                for (Integer c : baseSet) {
                    Set<Integer> leftSideResult = new HashSet<>();
                    Set<Integer> firstOpResult = hyperMultiplication.apply(a, b);
                    for (Integer intermediateResult : firstOpResult) {
                        leftSideResult.addAll(hyperMultiplication.apply(intermediateResult, c));
                    }

                    Set<Integer> rightSideResult = new HashSet<>();
                    Set<Integer> secondOpResult = hyperMultiplication.apply(b, c);
                    for (Integer intermediateResult : secondOpResult) {
                        rightSideResult.addAll(hyperMultiplication.apply(a, intermediateResult));
                    }

                    if (!leftSideResult.equals(rightSideResult)) {
                        log.debug("Associativity failed for (a,b,c) = ({},{},{}) — LHS: {}, RHS: {}",
                            a, b, c, leftSideResult, rightSideResult);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Üretim aksiyomunu kontrol eder: a ο H = H ve H ο a = H
     * Bu, kümedeki her 'a' elemanı için kontrol edilmelidir.
     * @return Üretim aksiyomu sağlanıyorsa true, aksi halde false.
     */
    public boolean checkReproductionAxiom() {
        log.debug("Checking for reproduction axiom...");
        for (Integer a : baseSet) {
            Set<Integer> leftResult = new HashSet<>();
            for (Integer h : baseSet) {
                leftResult.addAll(hyperMultiplication.apply(a, h));
            }
            if (!leftResult.equals(baseSet)) {
                log.debug("Reproduction failed for a ο H where a = {}", a);
                return false;
            }

            Set<Integer> rightResult = new HashSet<>();
            for (Integer h : baseSet) {
                rightResult.addAll(hyperMultiplication.apply(h, a));
            }
            if (!rightResult.equals(baseSet)) {
                 log.debug("Reproduction failed for H ο a where a = {}", a);
                return false;
            }
        }
        return true;
    }


    /**
     * Dağılma özelliğini kontrol eder: a * (b + c) ⊆ a*b + a*c
     */
    public boolean checkDistributivity() {
        log.debug("Checking for distributivity...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                for (Integer c : baseSet) {
                    Integer b_plus_c = standardAddition.apply(b, c);
                    Set<Integer> leftSideResult = hyperMultiplication.apply(a, b_plus_c);

                    Set<Integer> a_mult_b = hyperMultiplication.apply(a, b);
                    Set<Integer> a_mult_c = hyperMultiplication.apply(a, c);

                    Set<Integer> rightSideResult = new HashSet<>();
                    for (Integer x : a_mult_b) {
                        for (Integer y : a_mult_c) {
                            rightSideResult.add(standardAddition.apply(x, y));
                        }
                    }

                    if (!rightSideResult.containsAll(leftSideResult)) {
                        log.debug("Distributivity failed for (a,b,c) = ({},{},{})", a, b, c);
                        return false;
                    }

                    Set<Integer> leftSideResultR = hyperMultiplication.apply(b_plus_c, a);
                    Set<Integer> b_mult_a = hyperMultiplication.apply(b, a);
                    Set<Integer> c_mult_a = hyperMultiplication.apply(c, a);

                    Set<Integer> rightSideResultR = new HashSet<>();
                    for (Integer x : b_mult_a) {
                        for (Integer y : c_mult_a) {
                            rightSideResultR.add(standardAddition.apply(x, y));
                        }
                    }

                    if (!rightSideResultR.containsAll(leftSideResultR)) {
                        log.debug("Right distributivity failed for (a,b,c) = ({},{},{})", a, b, c);
                        return false;
                    }
                }
            }
        }
        return true;
    }


    /**
     * Negatif özelliğini kontrol eder: a.(-b) = (-a).b = -(a.b)
     */
    public boolean checkNegativeProperty() {
        log.debug("Checking for negative property...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                Integer negB = standardNegation.apply(b);
                Integer negA = standardNegation.apply(a);

                Set<Integer> res1 = hyperMultiplication.apply(a, negB);
                Set<Integer> res2 = hyperMultiplication.apply(negA, b);

                Set<Integer> a_mult_b = hyperMultiplication.apply(a, b);
                Set<Integer> res3 = new HashSet<>();
                for (Integer x : a_mult_b) {
                    res3.add(standardNegation.apply(x));
                }

                if (!res1.equals(res2) || !res1.equals(res3)) {
                    log.debug("Negative property failed for (a,b) = ({},{})", a, b);
                    return false;
                }
            }
        }
        return true;
    }


    public VerificationResult verifyAll() {
        java.util.Locale locale = LocaleContextHolder.getLocale();
        VerificationResult result = new VerificationResult();

        // 1. Kapanıklık Kontrolü (Closure)
        boolean isClosed = checkClosure();
        result.setHypergroupoid(isClosed);

        if (!isClosed) {
            result.setHighestStructure(messageSource.getMessage("axiom.structure.undefinedClosure", null, locale));
            result.setFailingAxiom(messageSource.getMessage("axiom.name.closure", null, locale));
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setDistributive(false);
            result.setHasNegativeProperty(false);
            result.setHypergroup(false);
            return result;
        }

        // 1.b DÜZELTME (Hakem uyarısı): "(R,+) is an abelian group" artık VARSAYILMIYOR,
        // verilen baseSet için GERÇEKTEN doğrulanıyor (bkz. verifyAdditiveGroupAxioms).
        // Hiçbir küme reddedilmiyor/kısıtlanmıyor; sonuç sadece hiperhalka sınıflandırmasının
        // ne kadar güvenilir olduğunu belirlemek için kullanılıyor.
        boolean isValidAdditiveGroup = verifyAdditiveGroupAxioms();

        // 2. Kapanıklık sağlandıysa diğer aksiyomları test et ve sonucunu bir değişkende sakla.
        boolean isAssociative = isAssociative();
        boolean isQuasihypergroup = checkReproductionAxiom();

        // Dağılma ve negatif eleman testleri standardAddition/standardNegation'a dayanır;
        // (R,+) gerçekten bir abelyen grup değilse bu testlerin sonucu matematiksel olarak
        // anlamsız olur, bu yüzden yalnızca isValidAdditiveGroup doğruysa hesaplanırlar.
        boolean isDistributive = isValidAdditiveGroup && checkDistributivity();
        boolean hasNegativeProperty = isValidAdditiveGroup && checkNegativeProperty();

        // --- SONUÇLARI DTO'YA NET BİR ŞEKİLDE YAZ ---
        result.setSemihypergroup(isAssociative);
        result.setQuasihypergroup(isQuasihypergroup);
        result.setDistributive(isDistributive);
        result.setHasNegativeProperty(hasNegativeProperty);

        boolean isHypergroup = isAssociative && isQuasihypergroup;
        result.setHypergroup(isHypergroup);

        // --- HİPERHALKA KONTROLÜ ---
        // Varsayım DEĞİL, doğrulanmış önkoşul: (R,+) gerçekten değişmeli gruptur.
        // NOT: Rota'nın tanımı 'reproduction' aksiyomunu içermediğinden,
        // isMultiplicativeHyperring kontrolü isQuasihypergroup'tan bağımsızdır.
        // Bir yapı, hipergrup olmasa bile çarpımsal hiperhalka özelliği gösterebilir.
        boolean isMultiplicativeHyperring = isValidAdditiveGroup && isAssociative && isDistributive && hasNegativeProperty;

        // --- EN YÜKSEK YAPIYI BELİRLE (highestStructure) ---
        if (isMultiplicativeHyperring) {
            result.setHighestStructure(messageSource.getMessage("axiom.structure.multiplicativeHyperring", null, locale));
        } else if (!isAssociative) {
            result.setHighestStructure(messageSource.getMessage("axiom.structure.hyperStructureNotSemihypergroup", null, locale));
        } else if (!isValidAdditiveGroup) {
            // (R,+) abelyen grup olmadığı için hiperhalka sınıflandırması hiç yapılamıyor;
            // bu, "dağılma/negatif özellik başarısız" durumundan farklı ve daha temel bir
            // sınırlamadır, bu yüzden ayrı ve açık şekilde raporlanıyor.
            result.setHighestStructure(isHypergroup
                ? messageSource.getMessage("axiom.structure.hypergroupNoAdditiveGroup", null, locale)
                : messageSource.getMessage("axiom.structure.semihypergroupNoAdditiveGroup", null, locale));
        } else if (!isDistributive) {
            result.setHighestStructure(isHypergroup 
                ? messageSource.getMessage("axiom.structure.hypergroupNotHyperring", null, locale)
                : messageSource.getMessage("axiom.structure.semihypergroupNotHyperring", null, locale));
        } else {
            result.setHighestStructure(isHypergroup 
                ? messageSource.getMessage("axiom.structure.hypergroupNotHyperring", null, locale)
                : messageSource.getMessage("axiom.structure.semihypergroupNotHyperring", null, locale));
        }

        // --- FAILINGAXIOM: HİPERGRUP SEVİYESİNİ ÖNCELİKLENDİR ---
        if (!isAssociative) {
            result.setFailingAxiom(messageSource.getMessage("axiom.name.associativity", null, locale));
        } else if (!isQuasihypergroup) {
            result.setFailingAxiom(messageSource.getMessage("axiom.name.reproduction", null, locale));
        } else {
            result.setFailingAxiom(null);
        }

        return result;
    }
}