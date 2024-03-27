package org.e2immu.analyser.parser.modification.testexample;

import java.util.List;

public class Linking_0<X> {

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
    String m0(List<String> list) {
        return list.get(0);
    }

    // dependent
    M m1(List<M> list) {
        return list.get(0);
    }

    // common HC
    X m2(List<X> list) {
        return list.get(0);
    }

    // dependent, regardless of String
    List<String> m3(List<String> list) {
        list.add("x");
        return list;
    }

    // dependent
    List<M> m4(List<M> list) {
        list.add(new M());
        return list;
    }

    // dependent, regardless of X
    List<X> m5(List<X> list) {
        list.remove(1);
        return list;
    }
}
