package org.e2immu.analyser.testexample.a;

public @interface Value {

    @interface Immutable {

        @interface DeeplyImmutable {
            int level() default 3;
        }
    }
}
