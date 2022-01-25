package org.e2immu.analyser.parser.eventual.testexample;

import org.e2immu.support.EventuallyFinal;

import java.util.Objects;

public class Warnings_0 {
    private Warnings_0() {
        throw new UnsupportedOperationException();
    }

    public static <T> void setFinalAllowEquals(EventuallyFinal<T> eventuallyFinal, T t) {
        if (eventuallyFinal.isVariable() || !Objects.equals(eventuallyFinal.get(), t)) {
            eventuallyFinal.setFinal(t);
        }
    }
}
