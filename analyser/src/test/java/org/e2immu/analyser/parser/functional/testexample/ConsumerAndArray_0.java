package org.e2immu.analyser.parser.functional.testexample;

import java.util.function.Consumer;

public class ConsumerAndArray_0 {

    public static <T> void method1(T[] array, Consumer<T> consumer) {
        consumer.accept(array[3]);
    }
}
