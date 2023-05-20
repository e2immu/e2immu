package org.e2immu.analyser.parser.loops.testexample;

import java.util.function.Consumer;

/*
Error: consumer.accept(array[i]);
Works fine when array[i] is in a separate variable.

 */
public class Loops_25 {
    public static <T> void loop1(T[] array, Consumer<T> consumer) {
        //noinspection ALL
        for (int i = 0; i < array.length; i++) {
            T t = array[i];
            consumer.accept(t);
        }
    }

    public static <T> void loop2(T[] array, Consumer<T> consumer) {
        //noinspection ALL
        for (int i = 0; i < array.length; i++) {
            consumer.accept(array[i]);
        }
    }

}
