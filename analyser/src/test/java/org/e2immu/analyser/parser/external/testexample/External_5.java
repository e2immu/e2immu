package org.e2immu.analyser.parser.external.testexample;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD})
@Retention(RUNTIME)
public @interface External_5 {

    Class<?> value();

    String extra() default "?";
}


