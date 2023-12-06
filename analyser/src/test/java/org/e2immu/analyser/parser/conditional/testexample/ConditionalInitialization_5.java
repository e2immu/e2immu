package org.e2immu.analyser.parser.conditional.testexample;

public class ConditionalInitialization_5 {

    private static ConditionalInitialization_5 s;

    static ConditionalInitialization_5 getInstance() {
        if (s == null) {
            s = new ConditionalInitialization_5();
        }
        return s;
    }

}
