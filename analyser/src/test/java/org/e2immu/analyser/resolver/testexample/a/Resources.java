package org.e2immu.analyser.resolver.testexample.a;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Resources {
    Resource[] value();
}
