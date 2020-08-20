/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.function.Consumer;

import static org.e2immu.annotation.AnnotationType.CONTRACT;
import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

public class FunctionalInterfaceModified3<T> {

    private final T t1;

    private final T t2;

    @Exposed(type = VERIFY_ABSENT)
    private Consumer<T> consumer;

    @Exposed // what does this mean????
    private Runnable runnable;

    public FunctionalInterfaceModified3(T t1, T t2) {
        this.t1 = t1;
        this.t2 = t2;
    }

    /*
     The reasoning behind acceptT1 being @NotModified, with an @Exposed consumer:

     1. Unless specified with @NotModified1 on consumer, accept modifies its parameter.
     2. The enclosing type has no means to modify T, as it is an unbound generic type.

     3. Combining 1 and 2 leads us to the the path of exposure: the method is @NotModified, and the consumer
        is marked @Exposed
    */
    @NotModified
    public void acceptT1(@Exposed Consumer<T> consumer) {
        consumer.accept(t1);
    }

    /*
     The reasoning behind acceptT2 being @NotModified:

     1. The consumer is @NotModified1, implying that the accept method does not modify t2
     2. As a consequence, acceptT2 does not modify any fields
     3. As a consequence, t2 is @NotModified
     */

    @NotModified
    public void acceptT2(@NotModified1(type = CONTRACT) Consumer<T> consumer) {
        consumer.accept(t2);
    }


    public void acceptT1version2(@Exposed Consumer<T> consumer) {
        consumer.accept(t1);
        this.consumer = consumer;
    }

    /*
    This method does NOT expose anything!
     */
    public void applyConsumer(T t) {
        this.consumer.accept(t);
    }

    /* even in a delayed way, there will be exposure of one of the fields through this consumer */
    public void acceptT1Version3(@Exposed Consumer<T> consumer) {
        this.runnable = () -> consumer.accept(t1);
    }

    /* this method does the actual exposure */
    public void applyRunnable() {
        runnable.run();
    }
}
