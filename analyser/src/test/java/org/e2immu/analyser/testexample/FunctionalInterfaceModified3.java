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

    private Consumer<T> nonExposingConsumer;

    @Exposed(type = VERIFY_ABSENT)
    private Runnable runnable;

    @Exposed
    private final Consumer<T> exposingConsumer1;

    @Exposed
    private final Consumer<T> exposingConsumer2;

    public FunctionalInterfaceModified3(T t1, T t2,
                                        @Exposed Consumer<T> exposingConsumerParam1,
                                        @Exposed Consumer<T> exposingConsumerParam2) {
        this.t1 = t1;
        this.t2 = t2;
        this.exposingConsumer1 = exposingConsumerParam1;
        this.exposingConsumer2 = exposingConsumerParam2;
    }

    /*
    causes the @Exposed property on exposingConsumer, which then continues to travel to to exposingConsumerParam.
    It is imperative that in every exposing situation, a parameter takes the @Exposed annotation.
    The annotations on the field are there simply for transfer purposes.
     */
    public void expose1() {
        exposingConsumer1.accept(t1);
    }

    /*
    somehow we must be able to compute that exposingConsumer2 will accept t2...
     */
    public void expose2() {
        staticallyExposing(t2, exposingConsumer2);
    }

    /*
    trivial method, which connects the first parameter to the second in an important way.
    Param 1 exposes param 0.

    The numeric notation is used when it is a parameter rather than a field that is exposed.
     */
    private static <T> void staticallyExposing(T t, @Exposed(0) Consumer<T> consumer) {
        consumer.accept(t);
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


    public void setNonExposingConsumer(Consumer<T> consumer) {
        this.nonExposingConsumer = consumer;
    }

    /*
    This method does NOT expose anything! t is not part of the object graph of the fields
     */
    public void applyNonExposingConsumer(T t) {
        this.nonExposingConsumer.accept(t);
    }

    /*
    even in a delayed way, there will be exposure of one of the fields through this consumer.
    The method is obviously @Modified because runnable is set.

    This method is a good test for computing that consumer3 is @Exposed
     */
    @Modified
    public void acceptDelayedT1(@Exposed Consumer<T> consumer3) {
        this.runnable = () -> consumer3.accept(t1);
    }

    /* this method does the actual exposure, but that is not relevant (?)

    Method is @NotModified:

    1. first note that we are able to compute the property exactly because the single assignment occurs inside our type,
       even though there can be re-assignments.
    2. executing accept on consumer3 is an exposing action, which is not a modifying one to our structures (it may modify t1).
     */
    @NotModified
    public void actuallyAcceptT1() {
        runnable.run();
    }
}
