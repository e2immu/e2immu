package org.e2immu.analyser.parser.start.testexample;

public class TryStatement_11 {

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get();
    }

    record TryCatchHelper<T>(T returnValue, Exception exception) {
    }

    public static <X> TryCatchHelper<X> tryCatch(ThrowingSupplier<X> supplier) {
        try {
            X x = supplier.get();
            return new TryCatchHelper<>(x, null);
        } catch (Exception re) {
            return new TryCatchHelper<>(null, re);
        }
    }

    public static String same1(String in) {
        try {
            return in.toUpperCase();
        } finally {
            System.out.println(in);
        }
    }

    public static String same2(String in) {
        ThrowingSupplier<String> supplier = in::toUpperCase;
        TryCatchHelper<String> tryCatchHelper = tryCatch(supplier);
        Runnable runFinally = () -> System.out.println(in);

        if (tryCatchHelper.exception() == null) {
            runFinally.run();
            return tryCatchHelper.returnValue();
        } else {
            runFinally.run();
            throw (RuntimeException) tryCatchHelper.exception();
        }
    }
}
