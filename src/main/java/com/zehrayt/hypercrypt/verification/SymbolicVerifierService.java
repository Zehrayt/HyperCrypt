package com.zehrayt.hypercrypt.verification;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import edu.jas.arith.BigInteger;
import edu.jas.arith.BigRational;
import edu.jas.poly.ExpVector;
import edu.jas.poly.GenPolynomial;
import edu.jas.poly.GenPolynomialRing;
import edu.jas.structure.RingElem;
import edu.jas.structure.RingFactory;
import org.springframework.stereotype.Service;

@Service
public class SymbolicVerifierService {

    public VerificationResult verifySymbolically(String rule, String domain) {
        System.out.println("Performing symbolic verification for rule '" + rule + "' on domain '" + domain + "'...");

        VerificationResult result = new VerificationResult();

        // 1. Kuralın geçerli bir polinom formatında olup olmadığını kontrol et.
        if (!isValidPolynomialRule(rule)) {
            result.setSuggestion("Symbolic analysis only supports polynomial rules with variables 'a' and 'b'.");
            result.setFailingAxiom("Geçersiz Kural Formatı");
            // Diğer boolean'ları da false yapalım
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setHypergroup(false);
            return result;
        }

        // 2. Kuralın içinde standart çarpma (*) içerip içermediğini kontrol et.
        if (!rule.contains("*")) {
            result.setSuggestion("Symbolic analysis requires a rule that includes standard multiplication (*).");
            result.setFailingAxiom("Çarpma İçermeyen Kural");
            // Diğer boolean'ları da false yapalım
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setHypergroup(false);
            return result;
        }

        try {
            // Önce birleşme aksiyomunu test et
            this.verifyAssociativity(rule, domain, result);

            // Eğer birleşme geçtiyse üretim aksiyomunu da deneyelim
            if (result.isSemihypergroup()) {
                this.verifyGenerationAxiom(rule, domain, result);
            }

            // Son olarak hypergroup durumu belirle
            boolean isHypergroup = result.isSemihypergroup() && result.isQuasihypergroup();
            result.setHypergroup(isHypergroup);

            if (isHypergroup) {
                result.setHighestStructure("Hipergrup (Symbolic)");
                result.setFailingAxiom(null);
            } else if (result.isSemihypergroup()) {
                result.setHighestStructure("Yarı Hipergrup (Semihypergroup)");
            } else if (result.getFailingAxiom() != null) {
                result.setHighestStructure("Hipergrupoid (Symbolic)");
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.setSuggestion("Symbolic analysis failed: " + e.getMessage());
            result.setSemihypergroup(false);
        }

        return result;
    }

    // generic yardımcı metot (birleşme testi)
    private <C extends RingElem<C>> void verifyAssociativity(String rule, String domain, VerificationResult result) {

        @SuppressWarnings("unchecked")
        RingFactory<C> factory = (RingFactory<C>) getCoefficientFactory(domain);

        GenPolynomialRing<C> mainRing = new GenPolynomialRing<>(factory, new String[]{"a", "b", "c"});
        GenPolynomialRing<C> ruleRing = new GenPolynomialRing<>(factory, new String[]{"x", "y"});

        // kural polinomu
        GenPolynomial<C> rulePoly = ruleRing.parse(rule.replace("a", "x").replace("b", "y"));

        GenPolynomial<C> polyA = mainRing.univariate(0);
        GenPolynomial<C> polyB = mainRing.univariate(1);
        GenPolynomial<C> polyC = mainRing.univariate(2);

        try {
            GenPolynomial<C> a_op_b = compose(rulePoly, mainRing, polyA, polyB);
            GenPolynomial<C> lhs = compose(rulePoly, mainRing, a_op_b, polyC);

            GenPolynomial<C> b_op_c = compose(rulePoly, mainRing, polyB, polyC);
            GenPolynomial<C> rhs = compose(rulePoly, mainRing, polyA, b_op_c);

            System.out.println("LHS Parsed: " + lhs);
            System.out.println("RHS Parsed: " + rhs);

            if (lhs.equals(rhs)) {
                result.setSemihypergroup(true);
                result.setHighestStructure("At least a Semihypergroup (Symbolic)");
            } else {
                result.setSemihypergroup(false);
                result.setFailingAxiom("Birleşme Özelliği (Associativity)");
                result.setHighestStructure("Hypergroupoid (Symbolic)");
            }

            result.setQuasihypergroup(false);
            result.setHypergroup(false);

        } catch (Exception e) {
            e.printStackTrace();
            result.setSuggestion("Associativity analysis failed: " + e.getMessage());
            result.setSemihypergroup(false);
        }
    }

    /**
     * Üretim Aksiyomu Testi (Reproduction Axiom).
     * String eşleştirme (göstermelik) yerine JAS kullanılarak 
     * gerçek "Polinom Derecesi (Polynomial Degree)" analizi ile matematiksel ispat yapıldı.
     * Bir hiper-işlemin sonsuz kümelerde (Z veya Q) üretim aksiyomunu (a . H = H) sağlaması için, 
     * a . x = y denkleminin her zaman çözülebilir olması gerekir.
     * Bunun tek bir yolu vardır: Kuralın (polinomun) toplam derecesi kesinlikle 1 olmalıdır.
     * formül c_1a + c_2b + c_3 şeklinde (lineer) olmak zorundadır.
     * Eğer kuralda a . b varsa (toplam derece 1+1=2 olur) veya a^2 varsa (derece 2), bu denklem her zaman çözülemez.
     * Sıfıra bölme veya kök alma hatası verir ve bu da üretim aksiyomunun sağlanmamasına neden olur.
     * Ayrıca formülde hem a hem de b harfi mutlaka bulunmalıdır.
     * JAS'ın degree() fonksiyonunu kullanarak polinomun derecesine göre matematiksel ispat yapacağız.
     */
    private <C extends RingElem<C>> void verifyGenerationAxiom(String rule, String domain, VerificationResult result) {
        System.out.println("Symbolically checking generation axiom using JAS for rule: " + rule);

        boolean isSolvable = false;
        try {
            // JAS Kütüphanesi ile kuralı gerçek bir polinoma çeviriyoruz
            @SuppressWarnings("unchecked")
            RingFactory<C> factory = (RingFactory<C>) getCoefficientFactory(domain);
            GenPolynomialRing<C> ruleRing = new GenPolynomialRing<>(factory, new String[]{"x", "y"});
            GenPolynomial<C> rulePoly = ruleRing.parse(rule.replace("a", "x").replace("b", "y"));

            // MATEMATİKSEL İSPAT ALGORİTMASI:
            long totalDegree = rulePoly.degree(); // Polinomun toplam derecesi
            long degreeX = rulePoly.degree(0);    // 'a' değişkeninin (x) derecesi
            long degreeY = rulePoly.degree(1);    // 'b' değişkeninin (y) derecesi

            if (totalDegree > 1) {
                // Eğer derece 1'den büyükse (örn: a*b çarpımı veya a^2 varsa), evrensel olarak çözülemez.
                // Hakem 1'in bahsettiği "Sıfıra bölme" hatası a*b'nin 2. dereceden olmasından kaynaklanır.
                isSolvable = false;
                result.setSuggestion("Reproduction axiom fails mathematically. The polynomial degree is > 1 (e.g., contains 'a*b' or 'a^2'), which means the function is not surjective mapping over the domain.");
            } else if (degreeX == 0 || degreeY == 0) {
                // Eğer değişkenlerden biri hiç yoksa (Örn: kural sadece "a" veya "5" ise)
                isSolvable = false;
                result.setSuggestion("Both variables 'a' and 'b' must be present in the rule to satisfy both left and right reproduction axioms.");
            } else {
                // Eğer toplam derece 1 ise ve iki değişken de varsa, bu kural lineerdir (c1*a + c2*b + c3).
                // Lineer kurallar Rasyonel sayılarda ve Tamsayılarda üretim aksiyomunu sağlar.
                isSolvable = true;
            }

            result.setQuasihypergroup(isSolvable);
            if (!isSolvable && result.getFailingAxiom() == null) {
                result.setFailingAxiom("Üretim Aksiyomu (Reproduction)");
            }

        } catch (Exception e) {
            e.printStackTrace();
            result.setQuasihypergroup(false);
            if (result.getFailingAxiom() == null) {
                result.setFailingAxiom("Üretim Aksiyomu (Reproduction) - Analiz sırasında hata oluştu");
            }
        }
    }

    /**
     * Polinom kompozisyonu
     */
    private <C extends RingElem<C>> GenPolynomial<C> compose(GenPolynomial<C> rulePoly,
                                                             GenPolynomialRing<C> targetRing,
                                                             GenPolynomial<C>... subs) {
        GenPolynomial<C> result = targetRing.getZERO();

        GenPolynomial<C> rem = rulePoly;
        while (!rem.isZERO()) {
            ExpVector ev = rem.leadingExpVector();
            C coeff = rem.leadingBaseCoefficient();
            rem = rem.reductum();

            GenPolynomial<C> term = targetRing.getONE();

            for (int i = 0; i < subs.length; i++) {
                int e = (int) ev.getVal(i);
                if (e > 0) {
                    GenPolynomial<C> base = subs[i];
                    GenPolynomial<C> pow = targetRing.getONE();
                    for (int k = 0; k < e; k++) {
                        pow = pow.multiply(base);
                    }
                    term = term.multiply(pow);
                }
            }

            term = term.multiply(coeff);
            result = result.sum(term);
        }

        return result;
    }

    @SuppressWarnings("rawtypes")
    private RingFactory getCoefficientFactory(String domain) {
        if (domain == null) {
            return new BigInteger();
        }

        switch (domain.toUpperCase()) {
            case "INTEGERS":
                return new BigInteger();
            case "RATIONALS":
                return new BigRational();
            default:
                throw new IllegalArgumentException("Unsupported domain: " + domain);
        }
    }

    /**
     * Bir kural metninin, sadece izin verilen karakterleri (a, b, sayılar, +, -, *, /, (, ))
     * içerip içermediğini hızlı bir şekilde kontrol eder.
     * @param rule Kontrol edilecek kural metni.
     * @return Kural geçerli bir polinomsal ifade ise true, aksi halde false.
     */
    private boolean isValidPolynomialRule(String rule) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        // İzin verilenler: sayılar (\d), a, b, boşluk (\s), ve operatörler +-*/()
        return !rule.matches(".*[^\\dab\\s+\\-*\\/()].*");
    }
}
