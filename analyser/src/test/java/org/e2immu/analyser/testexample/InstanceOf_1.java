package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class InstanceOf_1 {

    @NotNull
    private final Number number;

    public InstanceOf_1(Object in) {
        if (in instanceof Number number) {
            this.number = number;
        } else {
            this.number = 3.14;
        }
    }

    public Number getNumber() {
        return number;
    }
}
