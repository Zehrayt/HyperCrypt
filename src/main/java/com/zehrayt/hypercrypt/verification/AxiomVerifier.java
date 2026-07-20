package com.zehrayt.hypercrypt.verification;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.zehrayt.hypercrypt.dtos.VerificationResult;


public class AxiomVerifier {

    // Hiper-işlemi temsil eden fonksiyonel arayüz
    // İki eleman alır (Integer, Integer), bir küme döndürür (Set<Integer>)
    private final BiFunction<Integer, Integer, Set<Integer>> hyperMultiplication;
    private final Set<Integer> baseSet;

    private final BiFunction<Integer, Integer, Integer> standardAddition;
    private final Function<Integer, Integer> standardNegation;

    public AxiomVerifier(Set<Integer> baseSet, BiFunction<Integer, Integer, Set<Integer>> hyperMultiplication) {
        this.baseSet = baseSet;
        this.hyperMultiplication = hyperMultiplication;

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
        System.out.println("Checking for closure...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                Set<Integer> result = hyperMultiplication.apply(a, b);

                // Kural 1: Sonuç boş küme olamaz
                if (result == null || result.isEmpty()) {
                    System.out.println("Closure failed: Result is empty for (a,b) = (" + a + "," + b + ")");
                    return false;
                }

                // Kural 2: Sonuç, baseSet'in bir alt kümesi olmalıdır
                if (!baseSet.containsAll(result)) {
                    System.out.println("Closure failed: Result contains elements outside baseSet for (a,b) = (" + a + "," + b + ")");
                    return false;
                }
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
        System.out.println("Checking for associativity...");
        // Kümedeki tüm olası (a, b, c) üçlülerini denememiz gerekiyor.
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                for (Integer c : baseSet) {
                    // Sol Taraf: (a ο b) ο c
                    Set<Integer> leftSideResult = new HashSet<>();
                    Set<Integer> firstOpResult = hyperMultiplication.apply(a, b);
                    for (Integer intermediateResult : firstOpResult) {
                        leftSideResult.addAll(hyperMultiplication.apply(intermediateResult, c));
                    }

                    // Sağ Taraf: a ο (b ο c)
                    Set<Integer> rightSideResult = new HashSet<>();
                    Set<Integer> secondOpResult = hyperMultiplication.apply(b, c);
                    for (Integer intermediateResult : secondOpResult) {
                        rightSideResult.addAll(hyperMultiplication.apply(a, intermediateResult));
                    }

                    // İki sonuç kümesi eşit değilse, özellik sağlanmıyor demektir.
                    if (!leftSideResult.equals(rightSideResult)) {
                        System.out.println("Associativity failed for (a,b,c) = (" + a + "," + b + "," + c + ")");
                        System.out.println("LHS: " + leftSideResult);
                        System.out.println("RHS: " + rightSideResult);
                        return false;
                    }
                }
            }
        }
        return true; // Tüm üçlüler için kontrol başarılı oldu.
    }

    /**
     * Üretim aksiyomunu kontrol eder: a ο H = H ve H ο a = H
     * Bu, kümedeki her 'a' elemanı için kontrol edilmelidir.
     * @return Üretim aksiyomu sağlanıyorsa true, aksi halde false.
     */
    public boolean checkReproductionAxiom() {
        System.out.println("Checking for reproduction axiom...");
        for (Integer a : baseSet) {
            // a ο H kontrolü
            Set<Integer> leftResult = new HashSet<>();
            for (Integer h : baseSet) {
                leftResult.addAll(hyperMultiplication.apply(a, h));
            }
            if (!leftResult.equals(baseSet)) {
                System.out.println("Reproduction failed for a ο H where a = " + a);
                return false;
            }

            // H ο a kontrolü
            Set<Integer> rightResult = new HashSet<>();
            for (Integer h : baseSet) {
                rightResult.addAll(hyperMultiplication.apply(h, a));
            }
            if (!rightResult.equals(baseSet)) {
                 System.out.println("Reproduction failed for H ο a where a = " + a);
                return false;
            }
        }
        return true;
    }


    /**
     * Dağılma özelliğini kontrol eder: a * (b + c) ⊆ a*b + a*c
     */
    public boolean checkDistributivity() {
        System.out.println("Checking for distributivity...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                for (Integer c : baseSet) {
                    // Sol Taraf: a * (b + c)
                    Integer b_plus_c = standardAddition.apply(b, c);
                    Set<Integer> leftSideResult = hyperMultiplication.apply(a, b_plus_c);

                    // Sağ Taraf: a*b + a*c
                    Set<Integer> a_mult_b = hyperMultiplication.apply(a, b);
                    Set<Integer> a_mult_c = hyperMultiplication.apply(a, c);

                    Set<Integer> rightSideResult = new HashSet<>();
                    for (Integer x : a_mult_b) {
                        for (Integer y : a_mult_c) {
                            rightSideResult.add(standardAddition.apply(x, y));
                        }
                    }

                    // Kontrol: Sol taraf, sağ tarafın bir alt kümesi mi?
                    if (!rightSideResult.containsAll(leftSideResult)) {
                        System.out.println("Distributivity failed for (a,b,c) = (" + a + "," + b + "," + c + ")");
                        return false;
                    }

                    // --- SAĞDAN DAĞILMA: (b + c) * a ⊆ b*a + c*a ---
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
                        System.out.println("Right distributivity failed for (a,b,c) = (" + a + "," + b + "," + c + ")");
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
        System.out.println("Checking for negative property...");
        for (Integer a : baseSet) {
            for (Integer b : baseSet) {
                Integer negB = standardNegation.apply(b);
                Integer negA = standardNegation.apply(a);

                Set<Integer> res1 = hyperMultiplication.apply(a, negB);     // a.(-b)
                Set<Integer> res2 = hyperMultiplication.apply(negA, b);     // (-a).b

                Set<Integer> a_mult_b = hyperMultiplication.apply(a, b);   // a.b
                Set<Integer> res3 = new HashSet<>();                     // -(a.b)
                for (Integer x : a_mult_b) {
                    res3.add(standardNegation.apply(x));
                }

                if (!res1.equals(res2) || !res1.equals(res3)) {
                    System.out.println("Negative property failed for (a,b) = (" + a + "," + b + ")");
                    return false;
                }
            }
        }
        return true;
    }


    public VerificationResult verifyAll() {
        VerificationResult result = new VerificationResult();

        // 1. Kapanıklık Kontrolü (Closure)
        boolean isClosed = checkClosure();
        result.setHypergroupoid(isClosed);

        // Kapanıklık yoksa diğer testleri yapmadan işlemi bitir
        if (!isClosed) {
            result.setHighestStructure("Tanımsız Yapı (Kapanıklık Sağlanmıyor)");
            result.setFailingAxiom("Kapanıklık (Closure)");
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setDistributive(false);
            result.setHasNegativeProperty(false);
            result.setHypergroup(false);
            return result;
        }

        // 2. Kapanıklık sağlandıysa diğer aksiyomları test et ve sonucunu bir değişkende sakla.
        boolean isAssociative = isAssociative();
        boolean isDistributive = checkDistributivity();
        boolean hasNegativeProperty = checkNegativeProperty();
        boolean isQuasihypergroup = checkReproductionAxiom();

        // --- SONUÇLARI DTO'YA NET BİR ŞEKİLDE YAZ ---
        result.setSemihypergroup(isAssociative);
        result.setQuasihypergroup(isQuasihypergroup);
        result.setDistributive(isDistributive);
        result.setHasNegativeProperty(hasNegativeProperty);

        boolean isHypergroup = isAssociative && isQuasihypergroup;
        result.setHypergroup(isHypergroup);

        // --- HİPERHALKA KONTROLÜ ---
        // Varsayım: (R,+) değişmeli gruptur.
        // NOT: Rota'nın tanımı 'reproduction' aksiyomunu içermediğinden, 
        // isMultiplicativeHyperring kontrolü isQuasihypergroup'tan bağımsızdır.
        // Bir yapı, hipergrup olmasa bile çarpımsal hiperhalka özelliği gösterebilir.
        boolean isMultiplicativeHyperring = isAssociative && isDistributive && hasNegativeProperty;

        // --- EN YÜKSEK YAPIYI BELİRLE (highestStructure) ---
        if (isMultiplicativeHyperring) {
            result.setHighestStructure("Çarpımsal Hiperhalka");
        } else if (!isAssociative) {
            result.setHighestStructure("Hiper yapı (ama Yarı Hipergrup değil)");
        } else if (!isDistributive) {
            result.setHighestStructure(isHypergroup ? "Hipergrup (ama Hiperhalka değil)" : "Yarı Hipergrup (ama Hiperhalka değil)");
        } else {
            // !hasNegativeProperty
            result.setHighestStructure(isHypergroup ? "Hipergrup (ama Hiperhalka değil)" : "Yarı Hipergrup (ama Hiperhalka değil)");
        }

        // --- FAILINGAXIOM: HİPERGRUP SEVİYESİNİ ÖNCELİKLENDİR ---
        // failingAxiom: Hipergrup aksiyomlarını (Birleşme -> Üretim) öncelik sırasına göre raporlar.
        // Halka özel (Distributivity/Negative) eksiklikler, 'highestStructure' ile bildirildiği için null geçilir.
        if (!isAssociative) {
            result.setFailingAxiom("Birleşme Özelliği (Madde 2)");
        } else if (!isQuasihypergroup) {
            result.setFailingAxiom("Üretim Aksiyomu (Reproduction)");
        } else {
            result.setFailingAxiom(null);
        }

        return result;
    }
}
