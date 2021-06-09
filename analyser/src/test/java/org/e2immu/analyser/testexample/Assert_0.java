package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class Assert_0 {

    @NotNull
    private final Object object;

    public Assert_0(@NotNull Object object) {
        this.object = object;
        assert object != null;
    }

    public Object getObject() {
        return object;
    }
}
