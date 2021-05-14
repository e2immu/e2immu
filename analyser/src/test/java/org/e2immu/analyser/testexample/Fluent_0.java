package org.e2immu.analyser.testexample;

import org.e2immu.analyser.testexample.a.IFluent_0;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Fluent;

@E2Container
public class Fluent_0 implements IFluent_0 {

    public final int value;

    private Fluent_0(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }

    @Container
    public static class Builder {
        private int value;

        @Fluent
        public final IFluent_0.Builder value(int value) {
            this.value = value;
            return (IFluent_0.Builder) this;
        }

        public Fluent_0 build() {
            return new Fluent_0(value);
        }
    }
}
