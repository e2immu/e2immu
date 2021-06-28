package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Variable;

// more complicated version of Basics_6
// showing that local variable copies are needed in the scope of fields, when that
// field is variable
public class Basics_18 {

    @E2Container
    record A(int i) {
    }

    @Variable
    @NotNull
    private A a = new A(3);

    public void get() {
        A a11 = a;
        System.out.println("!");
        A a12 = a;
        assert a11 == a12;
        assert a11.i == a12.i; // mmmm.... why would it be possible they're different, but mush have the same i?
        // we can do better than IntelliJ here; it is possible that a11!=a12, and therefore also that a11.i!=a12.i
    }

    public void setA(A a) {
        if(a == null) throw new UnsupportedOperationException();
        this.a = a;
    }
}
