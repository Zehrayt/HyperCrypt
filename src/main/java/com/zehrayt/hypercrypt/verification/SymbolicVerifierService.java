package com.zehrayt.hypercrypt.verification;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import edu.jas.arith.BigInteger;
import edu.jas.arith.BigRational;
import edu.jas.poly.ExpVector;
import edu.jas.poly.GenPolynomial;
import edu.jas.poly.GenPolynomialRing;
import edu.jas.structure.RingElem;
import edu.jas.structure.RingFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SymbolicVerifierService {

    private static final Logger log = LoggerFactory.getLogger(SymbolicVerifierService.class);

    public VerificationResult verifySymbolically(String rule, String domain) {
        log.debug("Performing symbolic verification for rule '{}' on domain '{}'...", rule, domain);

        VerificationResult result = new VerificationResult();

        // 1. Kuralın geçerli bir polinom formatında olup olmadığını kontrol et.
        if (!isValidPolynomialRule(rule)) {
            result.setSuggestion("Symbolic analysis only supports polynomial rules with variables 'a' and 'b'.");
            result.setFailingAxiom("Geçersiz Kural Formatı");
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setHypergroup(false);
            return result;
        }

        // 2. Kuralın içinde standart çarpma (*) içerip içermediğini kontrol et.
        if (!rule.contains("*")) {
            result.setSuggestion("Symbolic analysis requires a rule that includes standard multiplication (*).");
            result.setFailingAxiom("Çarpma İçermeyen Kural");
            result.setSemihypergroup(false);
            result.setQuasihypergroup(false);
            result.setHypergroup(false);
            return result;
        }

        try {
            // DÜZELTME (Hakem 2 uyarısı): Birleşme (associativity) ve üretim (reproduction)
            // aksiyomları birbirinden BAĞIMSIZ aksiyomlardır. Genel bir hipergrupoid,
            // birleşmeli olmadan da üretim aksiyomunu sağlayabilir (ve tam tersi). Bu yüzden
            // üretim aksiyomu artık birleşme sonucuna göre KOŞULLU çalıştırılmıyor; her ikisi
            // de her zaman ayrı ayrı test edilir ve sonuçları en sonda birleştirilir.
            this.verifyAssociativity(rule, domain, result);
            this.verifyGenerationAxiom(rule, domain, result);

            // Son olarak hypergroup durumu belirle
            boolean isHypergroup = result.isSemihypergroup() && result.isQuasihypergroup();
            result.setHypergroup(isHypergroup);

            if (isHypergroup) {
                result.setHighestStructure("Hipergrup (Symbolic)");
                result.setFailingAxiom(null);
            } else if (result.isSemihypergroup()) {
                result.setHighestStructure("Yarı Hipergrup (Semihypergroup)");
                result.setFailingAxiom("Üretim Aksiyomu (Reproduction)");
            } else if (result.isQuasihypergroup()) {
                // Birleşmeli değil ama üretim aksiyomunu sağlıyor: bu da geçerli, ayrı bir
                // sınıflandırmadır ve artık (düzeltme sayesinde) doğru şekilde tespit edilebiliyor.
                result.setHighestStructure("Quasihypergrup (Symbolic) - Birleşmeli Değil");
                result.setFailingAxiom("Birleşme Özelliği (Associativity)");
            } else if (result.getFailingAxiom() != null) {
                result.setHighestStructure("Hipergrupoid (Symbolic)");
            }

        } catch (Exception e) {
            log.error("Symbolic verification failed for rule '{}'", rule, e);
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

            log.debug("LHS Parsed: {}", lhs);
            log.debug("RHS Parsed: {}", rhs);

            if (lhs.equals(rhs)) {
                result.setSemihypergroup(true);
                result.setHighestStructure("At least a Semihypergroup (Symbolic)");
            } else {
                result.setSemihypergroup(false);
                result.setFailingAxiom("Birleşme Özelliği (Associativity)");
                result.setHighestStructure("Hypergroupoid (Symbolic)");
            }

            // DÜZELTME (Hakem 2 uyarısı): Burada ARTIK isQuasihypergroup/isHypergroup
            // false olarak zorlanmıyor. Bu iki alan, kendi bağımsız testi olan
            // verifyGenerationAxiom tarafından belirlenecek; birleşme testinin
            // sonucu üretim aksiyomunun sonucunu OLUMSUZ ETKİLEMEMELİDİR.

        } catch (Exception e) {
            log.error("Associativity analysis failed for rule '{}'", rule, e);
            result.setSuggestion("Associativity analysis failed: " + e.getMessage());
            result.setSemihypergroup(false);
        }
    }

    /**
     * Üretim Aksiyomu Testi (Reproduction Axiom) - DÜZELTİLMİŞ VERSİYON.
     *
     * Teori: a ο H = H aksiyomu tek bir "toplam derece" sayısıyla test edilemez;
     * bu, a sabitken b'nin H'yi taraması (SAĞ üretim) ile b sabitken a'nın H'yi
     * taraması (SOL üretim) olmak üzere iki AYRI koşuldur. Örneğin a^2+b kuralı
     * sağ üretimi Z üzerinde sağlar (a sabit, b=y-a^2 alınarak her y'ye ulaşılır)
     * ama sol üretimi sağlamaz (b sabit, a^2 hiçbir zaman negatif değer alamaz).
     * Aynı şekilde 2a+3b gibi doğrusal bir kural bile Z üzerinde üretim aksiyomunu
     * SAĞLAMAZ, çünkü b'nin katsayısı (3) Z'de bir birim (unit) değildir: a sabit
     * iken 2a+3b'nin görüntü kümesi 2a+3Z'dir ve bu Z'ye eşit değildir. Q üzerinde
     * ise katsayının sıfırdan farklı olması (bölme mümkün olduğu için) yeterlidir.
     *
     * ÖNEMLİ (Hakem 2 uyarısı üzerine ikinci düzeltme): Bu metot artık, çağıran
     * verifySymbolically metodunda, birleşme aksiyomunun sonucundan BAĞIMSIZ olarak
     * her zaman çalıştırılır. Üretim (reproduction) ve birleşme (associativity)
     * mantıksal olarak birbirinden bağımsız iki aksiyomdur; bir hipergrupoid
     * birleşmeli olmadan da üretim aksiyomunu sağlayabilir.
     *
     * Algoritma: rulePoly'nin terimleri (ExpVector bazında) tek tek gezilir.
     *  - SAĞ üretim (b'ye göre): b'nin üssü >= 2 olan herhangi bir terim varsa
     *    veya b'li terimin katsayısı a'ya bağlıysa (örn. a*b) sağ üretim
     *    yapısal olarak imkansızdır. Aksi halde saf "c*b" teriminin katsayısı
     *    c bulunur; Z'de c = ±1, Q'da c != 0 olmalıdır.
     *  - SOL üretim (a'ya göre): simetrik olarak aynı analiz a için yapılır.
     *  - Üretim aksiyomu (quasihypergroup), ancak HER İKİ yön de sağlanırsa geçerlidir.
     */
    private <C extends RingElem<C>> void verifyGenerationAxiom(String rule, String domain, VerificationResult result) {
        log.debug("Symbolically checking generation axiom using JAS for rule: {}", rule);

        try {
            @SuppressWarnings("unchecked")
            RingFactory<C> factory = (RingFactory<C>) getCoefficientFactory(domain);
            GenPolynomialRing<C> ruleRing = new GenPolynomialRing<>(factory, new String[]{"x", "y"});
            GenPolynomial<C> rulePoly = ruleRing.parse(rule.replace("a", "x").replace("b", "y"));

            boolean isIntegerDomain = domain == null || "INTEGERS".equalsIgnoreCase(domain);

            C one = factory.getONE();
            C negOne = one.negate();

            // Saf "c*b" (x^0 y^1) ve saf "c*a" (x^1 y^0) terimlerinin katsayıları.
            C yCoefficient = null;
            boolean yStructureOk = true; // SAĞ üretim (a sabit, b tarar) için yapısal uygunluk

            C xCoefficient = null;
            boolean xStructureOk = true; // SOL üretim (b sabit, a tarar) için yapısal uygunluk

            GenPolynomial<C> rem = rulePoly;
            while (!rem.isZERO()) {
                ExpVector ev = rem.leadingExpVector();
                C coeff = rem.leadingBaseCoefficient();
                rem = rem.reductum();

                long ex = ev.getVal(0); // 'a' (x) üssü
                long ey = ev.getVal(1); // 'b' (y) üssü

                // --- SAĞ üretim analizi (b'ye göre) ---
                if (ey >= 2) {
                    yStructureOk = false; // b^2 veya üstü -> tek değişkenli çözülebilirlik garanti edilemez
                } else if (ey == 1) {
                    if (ex != 0) {
                        yStructureOk = false; // katsayı a'ya bağlı (örn. a*b terimi)
                    } else {
                        yCoefficient = coeff; // saf c*b terimi
                    }
                }
                // ey == 0 olan terimler b-doğrusallığını bozmaz (sabit kaydırma)

                // --- SOL üretim analizi (a'ya göre) ---
                if (ex >= 2) {
                    xStructureOk = false;
                } else if (ex == 1) {
                    if (ey != 0) {
                        xStructureOk = false; // katsayı b'ye bağlı
                    } else {
                        xCoefficient = coeff; // saf c*a terimi
                    }
                }
            }

            boolean rightReproduction = yStructureOk && yCoefficient != null
                    && (isIntegerDomain ? (yCoefficient.equals(one) || yCoefficient.equals(negOne))
                                        : !yCoefficient.isZERO());

            boolean leftReproduction = xStructureOk && xCoefficient != null
                    && (isIntegerDomain ? (xCoefficient.equals(one) || xCoefficient.equals(negOne))
                                        : !xCoefficient.isZERO());

            boolean isSolvable = rightReproduction && leftReproduction;

            if (!isSolvable) {
                StringBuilder reasons = new StringBuilder();
                String unitHint = isIntegerDomain
                        ? "must be a constant equal to +1 or -1 (a unit in Z)"
                        : "must be a nonzero constant (Q allows division)";
                if (!rightReproduction) {
                    reasons.append("Right reproduction (a\u2218H=H) fails: the coefficient of 'b' ")
                            .append(unitHint).append(".");
                }
                if (!leftReproduction) {
                    if (reasons.length() > 0) {
                        reasons.append(" ");
                    }
                    reasons.append("Left reproduction (H\u2218a=H) fails: the coefficient of 'a' ")
                            .append(unitHint).append(".");
                }
                result.setSuggestion(reasons.toString());
            }

            result.setQuasihypergroup(isSolvable);
            if (!isSolvable && result.getFailingAxiom() == null) {
                result.setFailingAxiom("Üretim Aksiyomu (Reproduction)");
            }

        } catch (Exception e) {
            log.error("Generation axiom analysis failed for rule '{}'", rule, e);
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