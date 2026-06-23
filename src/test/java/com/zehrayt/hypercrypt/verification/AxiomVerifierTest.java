package com.zehrayt.hypercrypt.verification;

import com.zehrayt.hypercrypt.dtos.VerificationResult;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class AxiomVerifierTest {

    @Test
    void test_Z3_with_addition_is_a_hypergroup() {
        Set<Integer> z3 = Set.of(0, 1, 2);
        BiFunction<Integer, Integer, Set<Integer>> additionMod3 = (a, b) -> Set.of((a + b) % 3);

        AxiomVerifier verifier = new AxiomVerifier(z3, additionMod3);
        VerificationResult result = verifier.verifyAll();

        assertTrue(result.isHypergroup(), "Z_3 with addition mod 3 should be a hypergroup.");
        assertNull(result.getFailingAxiom());
    }

    @Test
    void test_Z3_with_multiplication_fails_reproduction() {
        Set<Integer> z3 = Set.of(0, 1, 2);
        BiFunction<Integer, Integer, Set<Integer>> multiplicationMod3 = (a, b) -> Set.of((a * b) % 3);

        AxiomVerifier verifier = new AxiomVerifier(z3, multiplicationMod3);
        VerificationResult result = verifier.verifyAll();

        assertFalse(result.isHypergroup());
        assertFalse(result.isQuasihypergroup());
        assertEquals("Üretim Aksiyomu (Reproduction)", result.getFailingAxiom());
    }
    
    @Test
    void test_integers_with_subtraction_fails_associativity() {
        Set<Integer> integers = Set.of(1, 2, 3);
        BiFunction<Integer, Integer, Set<Integer>> subtraction = (a, b) -> Set.of(a - b);

        AxiomVerifier verifier = new AxiomVerifier(integers, subtraction);
        VerificationResult result = verifier.verifyAll();
        
        assertFalse(result.isHypergroup());
        assertFalse(result.isSemihypergroup());
        assertEquals("Birleşme Özelliği (Associativity)", result.getFailingAxiom());
    }
}