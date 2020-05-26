package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.*;

import java.util.Collection;

public class SizeChecks {

    @Identity
    @Size(min = 1)
    @NotModified
    static <T> Collection<T> requireNotEmpty(@Size(min = 1) @NotModified Collection<T> ts) {
        if (ts.isEmpty()) throw new UnsupportedOperationException("ts is empty!");
        return ts;
    }

    // important here is that no @Size restriction gets back to
    // the parameter ts, because it is modifying!
    // the result, on the other hand, has @Size information
    @Size(min = 1)
    @NotModified
    @NotNull
    @Identity
    static <T> Collection<T> addOne(@NotModified(type = AnnotationType.VERIFY_ABSENT)
                                    @Size(type = AnnotationType.VERIFY_ABSENT) Collection<T> ts, T t) {
        ts.add(t);
        return ts;
    }

    @NotModified
    static <T> int method1(@NotNull Collection<T> input1) {
        if (input1.size() == 0) return -1;
        if (input1.size() < 3) return 0;
        if (input1.size() >= 3) { // ERROR, constant evaluation
            System.out.println("Always printed");
        }
        return 4;
    }

    @NotModified
    static <T> int method2(@NotNull Collection<T> input2) {
        int size2 = input2.size();
        if (size2 == 0) return -1;
        if (size2 < 3) return 0;
        if (size2 >= 3) { // ERROR, constant evaluation
            System.out.println("Always printed");
        }
        return 4;
    }

    @NotModified
    static <T> int method3(@NotNull Collection<T> input3) {
        int size3 = input3.size();
        if (size3 == 0) return -1;
        if (size3 >= 1) { // ERROR, constant evaluation
            System.out.println("Always printed");
        }
        return 4;
    }
}
