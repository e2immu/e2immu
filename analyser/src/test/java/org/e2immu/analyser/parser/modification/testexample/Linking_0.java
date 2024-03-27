package org.e2immu.analyser.parser.modification.testexample;

import java.util.List;

public class Linking_0 {

    // the archetypal modifiable type
    static class M {
        private int i;

        public int getI() {
            return i;
        }

        public void setI(int i) {
            this.i = i;
        }
    }

    // no linking
    static String m0(List<String> list) {
        return list.get(0);
    }

    // dependent
    static M m1(List<M> list) {
        return list.get(0);
    }

    // common HC
    static <X> X m2(List<X> list) {
        return list.get(0);
    }

    // dependent, regardless of String
    static List<String> m3(List<String> list) {
        return list.subList(0, 1);
    }

    // dependent
    static List<M> m4(List<M> list) {
        return list.subList(0, 1);
    }

    // dependent, regardless of X
    static <X> List<X> m5(List<X> list) {
        return list.subList(0, 1);
    }
}
