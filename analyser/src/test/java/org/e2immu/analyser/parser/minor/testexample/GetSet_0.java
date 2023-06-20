package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.method.GetSet;

public class GetSet_0 {

    record R1(int k) {
        @GetSet("k")
        int l() {
            return k;
        }

        // should raise error: wrong field name / parameter value
        @GetSet("l")
        int ll() {
            return k;
        }

        @GetSet(absent = true)
        int m() {
            return k + 1;
        }

    }

    static class C1 {
        private int i;

        @GetSet
        public int getI() {
            return i;
        }

        @GetSet
        public void setI(int i) {
            this.i = i;
        }

        @GetSet("i")
        public C1 setIReturn(int i) {
            this.i = i;
            return this;
        }

        @GetSet(absent = true)
        public C1 setIAlmost(int i) {
            this.i = i;
            System.out.println("i = " + i);
            return this;
        }
    }

    static class C2 {
        private int a;

        public int getA() {
            return a;
        }

        @GetSet("a")
        public void setA(int a) {
            this.a = a;
        }
    }

    static class C3 {
        private boolean abc;

        @GetSet("abc")
        public boolean isAbc() {
            return abc;
        }

        @GetSet
        public boolean hasAbc() {
            return abc;
        }

        @GetSet("abc")
        public boolean getAbc() {
            return abc;
        }

        // raises an error: wrong parameter value
        @GetSet("Abc")
        public void setAbc(boolean abc) {
            this.abc = abc;
        }
    }

}
