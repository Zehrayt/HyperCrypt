package com.zehrayt.hypercrypt.dtos;

import java.util.Map;

public class VerificationResult {
    private boolean isHypergroupoid = true; // Temel tanım her zaman sağlanır.
    private boolean isSemihypergroup;
    private boolean isQuasihypergroup;
    private boolean isHypergroup;
    private String highestStructure; // Ulaşılan en yüksek yapının adı
    private String failingAxiom; // Hatanın olduğu ilk aksiyom
    private String suggestion;
    private Map<String, Map<String, String>> cayleyTable; // Cayley tablosu
    //private boolean isAssociative;
    private boolean isDistributive;
    private boolean hasNegativeProperty;

    public boolean isHypergroupoid() {
        return isHypergroupoid;
    }

    public void setHypergroupoid(boolean hypergroupoid) {
        isHypergroupoid = hypergroupoid;
    }

    public boolean isSemihypergroup() {
        return isSemihypergroup;
    }

    public void setSemihypergroup(boolean semihypergroup) {
        isSemihypergroup = semihypergroup;
    }

    public boolean isQuasihypergroup() {
        return isQuasihypergroup;
    }

    public void setQuasihypergroup(boolean quasihypergroup) {
        isQuasihypergroup = quasihypergroup;
    }

    public boolean isHypergroup() {
        return isHypergroup;
    }

    public void setHypergroup(boolean hypergroup) {
        isHypergroup = hypergroup;
    }

    public String getHighestStructure() {
        return highestStructure;
    }

    public void setHighestStructure(String highestStructure) {
        this.highestStructure = highestStructure;
    }

    public String getFailingAxiom() {
        return failingAxiom;
    }

    public void setFailingAxiom(String failingAxiom) {
        this.failingAxiom = failingAxiom;
    }

    /* 
    public boolean isAssociative() {
        return isAssociative;
    }
    public void setAssociative(boolean associative) {
        isAssociative = associative;
    }
    */

    public String getSuggestion() {
        return this.suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public Map<String, Map<String, String>> getCayleyTable() {
        return cayleyTable;
    }

    public void setCayleyTable(Map<String, Map<String, String>> cayleyTable) {
        this.cayleyTable = cayleyTable;
    }

    public boolean isDistributive() {
        return isDistributive;
    }

    public void setDistributive(boolean distributive) {
        isDistributive = distributive;
    }

    public boolean isHasNegativeProperty() {
        return hasNegativeProperty;
    }

    public void setHasNegativeProperty(boolean hasNegativeProperty) {
        this.hasNegativeProperty = hasNegativeProperty;
    }

}