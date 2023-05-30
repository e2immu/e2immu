package org.e2immu.analyser.parser.conditional.testexample;

public class Precondition_10 {

    @FunctionalInterface
    interface ThrowingSupplier<X> {
        X get() throws Exception;
    }

    public record TryCatchHelper<T>(T returnValue, Exception exception) {
    }

    public static <X> TryCatchHelper<X> tryCatch(ThrowingSupplier<X> supplier) {
        try {
            X x = supplier.get();
            return new TryCatchHelper<>(x, null);
        } catch (Exception re) {
            return new TryCatchHelper<>(null, re);
        }
    }

    public static String method(String in) {
        ThrowingSupplier<String> supplier = () -> {
            System.out.println("input: " + in);
            return in.toUpperCase();
        };
        TryCatchHelper<String> t0 = tryCatch(supplier);
        Runnable finallyMethod = () -> System.out.println("this was same1");
        Exception exception = t0.exception();
        if (exception == null) {
            finallyMethod.run();
            return t0.returnValue();
        }
        if (exception instanceof NullPointerException npe) {
            System.out.println("Caught null!");
            finallyMethod.run();
            throw npe;
        }
        finallyMethod.run();
        throw (RuntimeException) exception;
    }
}
