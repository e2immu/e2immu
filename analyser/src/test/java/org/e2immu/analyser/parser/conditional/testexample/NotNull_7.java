package org.e2immu.analyser.parser.conditional.testexample;

public class NotNull_7 {

    public static Object method(Object object) {
        if(object == null) throw new NullPointerException();
        return object;
    }
}
