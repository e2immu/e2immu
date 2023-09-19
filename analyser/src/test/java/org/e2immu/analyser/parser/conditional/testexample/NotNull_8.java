package org.e2immu.analyser.parser.conditional.testexample;

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class NotNull_8<T> {

    private final List<T> list = new ArrayList<>();

    private void fill(Supplier<T> supplier) {
        T t;
        while ((t = supplier.get()) != null){
            list.add(t);
        }
    }

    @NotNull
    public List<T> getList() {
        return list;
    }
}
