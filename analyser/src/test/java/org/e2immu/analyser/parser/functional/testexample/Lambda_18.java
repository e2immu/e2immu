package org.e2immu.analyser.parser.functional.testexample;

import java.util.List;
import java.util.stream.Stream;

public abstract class Lambda_18 {

    public static List<String> method1(List<String> list) {
        Stream<String> v = list.stream();
        Stream<String> s2 = v.filter(s -> !s.isEmpty());
        Stream<String> s3 = s2.filter(s -> s.charAt(0) == 1);
        return s3.toList();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    record Tuple2<T0, T1>(T0 t0, T1 t1) {
    }

    abstract Tuple2<Exception, String> tryCatch2(ThrowingSupplier<String> supplier);

    public String method2(String in) {
        ThrowingSupplier<String> supplier = () -> {
            System.out.println("input: " + in);
            return in.toUpperCase();
        };
        Tuple2<Exception, String> tuple = tryCatch2(supplier);
        Runnable finallyMethod = () -> System.out.println("this was same1");
        if (tuple.t0() == null) {
            finallyMethod.run();
            return tuple.t1();
        }
        if (tuple.t0() instanceof NullPointerException npe) {
            System.out.println("Caught null!");
            finallyMethod.run();
            throw npe;
        }
        finallyMethod.run();
        throw (RuntimeException) tuple.t0();
    }
}
