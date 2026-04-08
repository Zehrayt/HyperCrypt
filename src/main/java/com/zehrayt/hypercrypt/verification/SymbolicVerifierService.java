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
     * Yeni versiyon: sonsuz kümeler için sembolik denklik kontrolü.
     */
    private <C extends RingElem<C>> void verifyGenerationAxiom(String rule, String domain, VerificationResult result) {
        System.out.println("Symbolically checking generation axiom for rule: " + rule);

        boolean isSolvable = false;
        try {
            String trimmedRule = rule.trim();

            if (trimmedRule.equals("a+b") || trimmedRule.equals("a + b")) {
                // Denklem: a + x = y  => x = y - a.
                // Tamsayılar ve Rasyoneller için her zaman çözüm var.
                isSolvable = true;
            } else if (trimmedRule.equals("a*b") || trimmedRule.equals("a * b")) {
                // Denklem: a * x = y => x = y / a.
                // DÜZELTME: Eğer küme '0' elemanını içeriyorsa (Integers ve Rationals içerir),
                // a=0 ve y≠0 olduğunda bu denklemin çözümü yoktur (0 * x = y olamaz).
                // Dolayısıyla standart a*b kuralı tam sayılarda da rasyonellerde de genel bir üretim aksiyomu (aH=H) SAĞLAMAZ.
                isSolvable = false; 
                result.setSuggestion("The equation a*x=y has no solution when a=0 and y≠0. Reproduction axiom fails for standard multiplication on domains containing zero.");
            }

            // Sonucu ayarla
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
