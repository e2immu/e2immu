package org.e2immu.analyser.resolver.testexample;

public class Constructor_10Scope {

    private final int i;

    public int getI() {
        return i;
    }

    public Constructor_10Scope(int i) {
        this.i = i;
    }

    public class Sub {
        final int j;

        Sub(int j) {
            this.j = j;
        }

        @Override
        public String toString() {
            return "together = " + i + ", " + j;
        }
    }

    Sub getSub(int j) {
        return this.new Sub(j);
    }

    static Sub copy(Constructor_10Scope c) {
        return c.new Sub(c.i);
    }
}
