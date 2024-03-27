package org.e2immu.analyser.parser.modification.testexample;

import java.util.ArrayList;
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

    // independent, because of String
    static List<String> m6(List<String> list) {
        return new ArrayList<>(list);
    }

    // dependent, because of M
    static List<M> m7(List<M> list) {
        return new ArrayList<>(list);
    }

    // independent HC, because of X
    static <X> List<X> m8(List<X> list) {
        return new ArrayList<>(list);
    }
}
