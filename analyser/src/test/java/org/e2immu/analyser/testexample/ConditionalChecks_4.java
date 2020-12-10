package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Nullable;

public class ConditionalChecks_4 {

    private final int i;

    public ConditionalChecks_4(int i) {
        this.i = i;
    }

    public boolean method5(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConditionalChecks_4 conditionalChecks = (ConditionalChecks_4) o;
        return i == conditionalChecks.i;
    }
}
