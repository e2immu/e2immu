package org.e2immu.analyser.resolver.testexample;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class MethodCall_61 {

    interface I extends Cloneable {
        long getId();
    }

    static <T extends I> T byId(T[] data, long id) {
        return null;
    }

    interface D extends java.io.Serializable, I {
    }

    void r(D d) {
    }

    void method(D[] ds) {
        r(byId(ds, 3L));
    }

    private static <T extends I> IntFunction<T[]> getArrayFactory(T[] array) {
        return (length) -> {
            Class componentType = array.getClass().getComponentType();
            return (T[]) Array.newInstance(componentType, length);
        };
    }

    static <T> Predicate<T> predicate(Function<? super T, ?> keyExtractor) {
        return t -> true;
    }

    static <T extends I> T[] method2(T[] array) {
        return Arrays.stream(array).toArray(getArrayFactory(array));
    }

    static <T extends I> T[] method3(T[] array) {
       return Arrays.stream(array).filter(predicate(I::getId)).toArray(getArrayFactory(array));
    }
}
