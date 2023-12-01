package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class Modification_31 {

    interface I {
        @Modified
        void modify();

        @NotModified
        int summary();
    }

    static int method1(@Modified I i) {
        int s1 = i.summary();
        if (s1 < 0) return 1;
        i.modify();
        int s2 = i.summary();
        if (s2 < 0) return 2;
        System.out.println("hi");
        return 3;
    }

    static int method2(@Modified I j) {
        int t = j.summary();
        if (t < 0) return 1;
        j.modify();
        t = j.summary();
        if (t < 0) return 2;
        System.out.println("hi");
        return 3;
    }

    static int method3(@Modified I k) {
        int u = k.summary();
        k.modify();
        int v = k.summary();
        if (u == v) return 2;
        System.out.println("hi");
        return 3;
    }

    // even though its value changes, it remains the same object returned
    @Identity
    static I method4(@Modified I i) {
        i.modify();
        return i;
    }
}
