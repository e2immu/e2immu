package org.e2immu.analyser.testexample.a;

public class TypeWithStaticSubType {

    public static final class C1 {
        public static final int CONSTANT = 33;
    }

    public static final class C2 {
        public static final int CONSTANT = 34;
    }

    public static final class SubType1 {
        private final int divisor;

       public SubType1(int divisor) {
            this.divisor = divisor;
        }

        public int doSomething(int i) {
            return i / divisor;
        }
    }

    public interface SubType2 {
        int doSomething(int i);
    }
}
