package org.e2immu.analyser.parser.basics.testexample;

import java.util.function.Consumer;
import java.util.function.IntPredicate;

public class Basics_29_Identifier {

    public static <T> void method(T[] array, Consumer<T> consumer) {
        int i = 0; // INIT
        IntPredicate continueWithLoop = v -> v < array.length;
        boolean loop = continueWithLoop.test(i);
        T t = array[i];
        i = i + 1;
        consumer.accept(t);
    }

}
