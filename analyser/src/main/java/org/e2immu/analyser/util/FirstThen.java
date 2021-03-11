/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import org.e2immu.annotation.*;

import java.util.Objects;

/**
 * An object which first holds an object of type <code>S</code>, and, in the second and final stage of its life-cycle,
 * holds an object of type <code>T</code>.
 * <p>
 * This class is eventually immutable: once the second stage has been reached, its fields cannot be changed anymore.
 * There is no support data and no possibility of content modification; therefore, the type is eventually {@link E2Immutable}.
 *
 * @param <S> type of the initial stage
 * @param <T> type of the final stage
 */

@E2Container(after = "first")
public class FirstThen<S, T> {
    @Final(after = "first")
    private volatile S first;
    @Final(after = "first")
    private volatile T then;

    /**
     * Only constructor
     *
     * @param first the initial value
     * @throws NullPointerException when the argument is <code>null</code>
     */
    public FirstThen(@NotNull S first) {
        this.first = Objects.requireNonNull(first);
    }

    @NotModified
    public boolean isFirst() {
        return first != null;
    }

    @NotModified
    public boolean isSet() {
        return first == null;
    }

    /**
     * The method that sets the final value, discarding the initial one forever.
     * It transitions from mutable to immutable.
     *
     * @param then the final value
     * @throws NullPointerException          when the final value is <code>null</code>
     * @throws UnsupportedOperationException when the object had already reached its final stage
     */
    @Mark("first")
    public void set(@NotNull T then) {
        Objects.requireNonNull(then);
        synchronized (this) {
            if (first == null) throw new UnsupportedOperationException("Already set");
            this.then = then;
            first = null;
        }
    }

    /**
     * Getter for the initial value.
     *
     * @return The initial value
     * @throws UnsupportedOperationException when the object has transitioned into the final stage
     */
    @NotNull
    @NotModified
    @Only(before = "first")
    public S getFirst() {
        if (first == null) throw new NullPointerException();
        return first;
    }

    /**
     * Getter for the final value.
     *
     * @return The final value
     * @throws UnsupportedOperationException when the object has not yet transitioned into the final stage
     */
    @NotNull
    @NotModified
    @Only(after = "first")
    public T get() {
        // we could have had a check on "then" directly, but then @Only would not be recognized
        if (first != null) throw new UnsupportedOperationException("Not yet set");
        if (then == null) throw new UnsupportedOperationException();
        return then;
    }

    /**
     * Delegating equals.
     *
     * @param o the other value
     * @return <code>true</code> when <code>o</code> is also a <code>FirstThen</code> object, with
     * the same initial or final object object, as defined by the <code>equals</code> method on <code>S</code>
     * or <code>T</code> respectively.
     */
    @Override
    @NotModified
    public boolean equals(@NotNull(absent=true) Object o) { // o is @NotModified because of Object
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FirstThen<?, ?> firstThen = (FirstThen<?, ?>) o;
        return Objects.equals(first, firstThen.first) &&
                Objects.equals(then, firstThen.then);
    }

    /**
     * Delegating hash code.
     *
     * @return a hash code based on the hash code of the initial or final value.
     */
    @Override
    @NotModified
    public int hashCode() {
        return Objects.hash(first, then);
    }
}
