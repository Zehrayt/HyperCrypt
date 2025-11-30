package com.zehrayt.hypercrypt.verification;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.function.Function;

import com.zehrayt.hypercrypt.dtos.VerificationResult;

public class AxiomVerifier<T extends java.lang.Number> {

    // Hiper-işlemi temsil eden fonksiyonel arayüz
    // İki eleman alır (T, T), bir küme döndürür (Set<T>)
    //private final BiFunction<T, T, Set<T>> hyperOperation;

    private final BiFunction<T, T, Set<T>> hyperMultiplication;
    private final Set<T> baseSet;

    //private final List<T> baseSetAsList; //////////////////////////// Kombinasyonlar için listeye ihtiyacımız olacak

    private final BiFunction<T, T, T> standardAddition;
    private final Function<T, T> standardNegation;

    //public AxiomVerifier(Set<T> baseSet, BiFunction<T, T, Set<T>> hyperOperation) {
    //    this.baseSet = baseSet;
    //    this.hyperOperation = hyperOperation;
    //    this.baseSetAsList = new java.util.ArrayList<>(baseSet);
    //}

    public AxiomVerifier(Set<T> baseSet, BiFunction<T, T, Set<T>> hyperMultiplication) {
        this.baseSet = baseSet;
        this.hyperMultiplication = hyperMultiplication;

        // Toplama ve negatif almayı tamsayılar için sabitliyoruz.
        // Bu, (R,+)'nın değişmeli grup olduğu varsayımımızdır.
        this.standardAddition = (a, b) -> (T) Integer.valueOf(a.intValue() + b.intValue());
        this.standardNegation = (a) -> (T) Integer.valueOf(-a.intValue());
    }

    /**
     * Birleşme özelliğini kontrol eder: (a ο b) ο c = a ο (b ο c)
     * Bu, kümedeki tüm (a, b, c) üçlüleri için kontrol edilmelidir.
     * @return Birleşme özelliği sağlanıyorsa true, aksi halde false.
     */
    public boolean isAssociative() {
        System.out.println("Checking for associativity...");
        // Kümedeki tüm olası (a, b, c) üçlülerini denememiz gerekiyor.
        for (T a : baseSet) {
            for (T b : baseSet) {
                for (T c : baseSet) {
                    // Sol Taraf: (a ο b) ο c
                    Set<T> leftSideResult = new HashSet<>();
                    Set<T> firstOpResult = hyperMultiplication.apply(a, b);
                    for (T intermediateResult : firstOpResult) {
                        leftSideResult.addAll(hyperMultiplication.apply(intermediateResult, c));
                    }

                    // Sağ Taraf: a ο (b ο c)
                    Set<T> rightSideResult = new HashSet<>();
                    Set<T> secondOpResult = hyperMultiplication.apply(b, c);
                    for (T intermediateResult : secondOpResult) {
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
        for (T a : baseSet) {
            // a ο H kontrolü
            Set<T> leftResult = new HashSet<>();
            for (T h : baseSet) {
                leftResult.addAll(hyperMultiplication.apply(a, h));
            }
            if (!leftResult.equals(baseSet)) {
                System.out.println("Reproduction failed for a ο H where a = " + a);
                return false;
            }

            // H ο a kontrolü
            Set<T> rightResult = new HashSet<>();
            for (T h : baseSet) {
                rightResult.addAll(hyperMultiplication.apply(h, a));
            }
            if (!rightResult.equals(baseSet)) {
                 System.out.println("Reproduction failed for H ο a where a = " + a);
                return false;
            }
        }
        return true;
    }

    
    //////YENİ EKLENEN METOT//////
    /// 
    /// 

    /**
     * Dağılma özelliğini kontrol eder: a * (b + c) ⊆ a*b + a*c
     */
    public boolean checkDistributivity() {
        System.out.println("Checking for distributivity...");
        for (T a : baseSet) {
            for (T b : baseSet) {
                for (T c : baseSet) {
                    // Sol Taraf: a * (b + c)
                    T b_plus_c = standardAddition.apply(b, c);
                    Set<T> leftSideResult = hyperMultiplication.apply(a, b_plus_c);

                    // Sağ Taraf: a*b + a*c
                    Set<T> a_mult_b = hyperMultiplication.apply(a, b);
                    Set<T> a_mult_c = hyperMultiplication.apply(a, c);

                    Set<T> rightSideResult = new HashSet<>();
                    for (T x : a_mult_b) {
                        for (T y : a_mult_c) {
                            rightSideResult.add(standardAddition.apply(x, y));
                        }
                    }

                    // Kontrol: Sol taraf, sağ tarafın bir alt kümesi mi?
                    if (!rightSideResult.containsAll(leftSideResult)) {
                        System.out.println("Distributivity failed for (a,b,c) = (" + a + "," + b + "," + c + ")");
                        return false;
                    }
                    
                    // TODO: Sağdan dağılma ((b+c)*a ⊆ b*a + c*a) da benzer şekilde kontrol edilebilir.
                }
            }
        }
        return true;
    }

    ///// YENİ EKLENEN METOT SONU//////
    /// 
    /// 


    ///YENİ EKLENEN METOT//////
    /// 

    /**
     * Negatif özelliğini kontrol eder: a.(-b) = (-a).b = -(a.b)
     */
    public boolean checkNegativeProperty() {
        System.out.println("Checking for negative property...");
        for (T a : baseSet) {
            for (T b : baseSet) {
                T negB = standardNegation.apply(b);
                T negA = standardNegation.apply(a);

                Set<T> res1 = hyperMultiplication.apply(a, negB);     // a.(-b)
                Set<T> res2 = hyperMultiplication.apply(negA, b);     // (-a).b
                
                Set<T> a_mult_b = hyperMultiplication.apply(a, b);   // a.b
                Set<T> res3 = new HashSet<>();                     // -(a.b)
                for (T x : a_mult_b) {
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
    /// YENİ EKLENEN METOT SONU//////




    // Mevcut verifyAll metodunu sil ve yerine bunu yapıştır.

    public VerificationResult verifyAll() {
        VerificationResult result = new VerificationResult();
        
        // --- HER BİR AKSIYOMU TEST EDİP SONUCUNU BİR DEĞİŞKENDE SAKLA ---
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

        // --- NİHAİ KARAR: HİPERHALKA KONTROLÜ ---
        // (Varsayım: (R,+) değişmeli grup)
        boolean isMultiplicativeHyperring = isAssociative && isDistributive && hasNegativeProperty;

        // --- EN YÜKSEK YAPIYI VE HATA MESAJINI AYARLA ---
        if (isMultiplicativeHyperring) {
            result.setHighestStructure("Çarpımsal Hiperhalka");
            result.setFailingAxiom(null);
        } else {
            // Hatanın ilk bulunduğu yere göre raporla.
            if (!isAssociative) {
                result.setFailingAxiom("Birleşme Özelliği (Madde 2)");
                result.setHighestStructure("Hiper yapı (ama Yarı Hipergrup değil)");
            } else if (!isDistributive) {
                result.setFailingAxiom("Dağılma Özelliği (Madde 3)");
                // Bu noktada en azından bir yarı hipergrup olduğunu biliyoruz.
                result.setHighestStructure(isHypergroup ? "Hipergrup (ama Hiperhalka değil)" : "Yarı Hipergrup (ama Hiperhalka değil)");
            } else if (!hasNegativeProperty) {
                result.setFailingAxiom("Negatif Özelliği (Madde 4)");
                result.setHighestStructure("Yarı Hipergrup (ama Hiperhalka değil)");
            } else if (!isQuasihypergroup) {
                // Not: Bu durum hiperhalka tanımını doğrudan engellemez, ama hipergrup olmasını engeller.
                // Bu yüzden failingAxiom'u burada ayarlamayabiliriz.
                result.setHighestStructure(isMultiplicativeHyperring ? "Çarpımsal Hiperhalka" : "Yarı Hipergrup (ama Hiperhalka değil)");
            }
        }
        
        return result;
    }
}