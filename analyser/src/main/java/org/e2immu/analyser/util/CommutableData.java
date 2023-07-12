package org.e2immu.analyser.util;

public record CommutableData(String par, String seq, String par2, String seq2) {

    public boolean isDefault() {
        return par.isBlank() && seq.isBlank() && par2.isBlank() && seq2.isBlank();
    }

}
