package org.e2immu.analyser.util;

public record CommutableData(String par, String seq, String multi) {

    public boolean isDefault() {
        return par.isBlank() && seq.isBlank() && multi.isBlank();
    }

    public boolean isParallel() {
        return par.isBlank() && seq.isBlank();
    }

    public boolean isSequential() {
        return par.isBlank() && !seq.isBlank();
    }
}
