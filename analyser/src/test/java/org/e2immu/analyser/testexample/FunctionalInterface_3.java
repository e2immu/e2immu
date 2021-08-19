/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Variable;

import java.util.function.Consumer;

public class FunctionalInterface_3<T> {

    private final T t1;

    private final T t2;

    @Variable
    private Consumer<T> nonExposingConsumer;

    @Variable
    private Runnable runnable;

    // we do not write @NotModified unless there is a declaration
    private final Consumer<T> exposingConsumer1;
    private final Consumer<T> exposingConsumer2;

    public FunctionalInterface_3(T t1, T t2,
                                 Consumer<T> exposingConsumerParam1,
                                 Consumer<T> exposingConsumerParam2) {
        this.t1 = t1;
        this.t2 = t2;
        this.exposingConsumer1 = exposingConsumerParam1;
        this.exposingConsumer2 = exposingConsumerParam2;
    }

    @Modified // exposing non-support-data does not cause a modification;
    // however, calling accept raises the possibility of acceptDelayedT1 to be called
    public void expose1() {
        exposingConsumer1.accept(t1);
    }

    @Modified
    public void expose2() {
        staticallyExposing(t2, exposingConsumer2);
    }

    @Modified
    public void expose3(Consumer<T> consumer) {
        staticallyExposing(t2, consumer);
    }

    // passing on an undeclared FI object amounts to same as invoking it: you copy the modification status
    // from the method you pass the FI on to
    @Modified
    public void expose4(Consumer<T> consumer) {
        expose3(consumer);
    }

    @Modified
    private static <T> void staticallyExposing(@NotModified T t, Consumer<T> consumer) {
        consumer.accept(t);
    }

    @Modified
    public void acceptT1(Consumer<T> consumer) {
        consumer.accept(t1);
    }

    @Modified // we promise not to modify t2, but we can still call acceptDelayedT1...
    public void acceptT2(@Container Consumer<T> consumer) {
        consumer.accept(t2);
    }

    @NotModified(contract = true) // we promise not to modify t2, and we cannot call any modifying operation
    public void acceptT2NonModifying(@Container Consumer<T> consumer) {
        consumer.accept(t2);
    }

    @Modified
    public void setNonExposingConsumer(Consumer<T> consumer) {
        this.nonExposingConsumer = consumer;
    }

    @Modified
    public void applyNonExposingConsumer(T t) {
        this.nonExposingConsumer.accept(t);
    }

    @Modified
    public void acceptDelayedT1(Consumer<T> consumer3) {
        this.runnable = () -> consumer3.accept(t1);
    }

    @Modified
    public void actuallyAcceptT1() {
        runnable.run();
    }
}
