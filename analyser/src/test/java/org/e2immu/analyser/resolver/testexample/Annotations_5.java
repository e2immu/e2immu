package org.e2immu.analyser.resolver.testexample;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
public @interface Annotations_5 {

    Class<?> value();

    String extra() default "!";
}


