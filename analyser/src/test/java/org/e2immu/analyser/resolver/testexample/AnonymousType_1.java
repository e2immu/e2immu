package org.e2immu.analyser.resolver.testexample;


import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// copied from Independent1_10; issues with functional interface computation
public class AnonymousType_1<T> {
    List<T> list = new ArrayList<>();

    @SafeVarargs
    static <T> AnonymousType_1<T> of(T... ts) {
        AnonymousType_1<T> result = new AnonymousType_1<>();
        result.fill(new Supplier<>() {
            int i;

            @Override
            public T get() {
                return i < ts.length ? ts[i++] : null;
            }
        });
        return result;
    }

    private void fill(Supplier<T> supplier) {
        T t;
        while ((t = supplier.get()) != null) list.add(t);
    }
}
