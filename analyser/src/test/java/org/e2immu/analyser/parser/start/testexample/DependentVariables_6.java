package org.e2immu.analyser.parser.start.testexample;

public class DependentVariables_6 {

    private final byte[] emptyArray = new byte[]{};

    public byte[] method(int i) {
        if (i < 0) return emptyArray;
        return new byte[]{(byte) (i % 255)};
    }
}
