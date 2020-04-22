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

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

/**
 * An object holding either an object of type <code>A</code>, or one of type <code>B</code>; it cannot hold both or neither.
 *
 * <code>Either</code> is a typical example of a {@link E1Container}: final fields, therefore immutable at the
 * first level. All parameters of the methods are {@link NotModified}.
 *
 * @param <A>
 * @param <B>
 */
@E1Container
public class Either<A, B> {
    private final A left;
    private final B right;

    private Either(A a, B b) {
        left = a;
        right = b;
    }

    /**
     * This method should only be called when the left value has been initialised.
     *
     * @return the left value, of type A.
     * @throws UnsupportedOperationException when the right value had been initialised.
     */
    @NotNull
    @NotModified
    public A getLeft() {
        return Objects.requireNonNull(left);
    }

    /**
     * This method should only be called when the right value has been initialised.
     *
     * @return the right value, of type B.
     * @throws UnsupportedOperationException when the left value had been initialised.
     */
    @NotNull
    @NotModified
    public B getRight() {
        return Objects.requireNonNull(right);
    }

    public boolean isLeft() {
        return left != null;
    }

    public boolean isRight() {
        return right != null;
    }

    /**
     * Factory method that produces an <code>Either</code> object with the right side filled in.
     *
     * @param right of type <code>R</code>; cannot be <code>null</code>
     * @param <L>   type of left side
     * @param <R>   type of right side
     * @return an <code>Either</code> object with the right side filled in.
     * @throws NullPointerException when the first argument is <code>null</code>.
     */
    @NotModified
    @NotNull
    public static <L, R> Either<L, R> right(@NotNull R right) {
        return new Either<L, R>(null, Objects.requireNonNull(right));
    }

    /**
     * Factory method that produces an <code>Either</code> object with the left side filled in.
     *
     * @param left of type <code>L</code>
     * @param <L>  type of left side
     * @param <R>  type of right side
     * @return an <code>Either</code> object with the left side filled in.
     * @throws NullPointerException when the first argument is <code>null</code>.
     */
    @NotModified
    @NotNull
    public static <L, R> Either<L, R> left(@NotNull L left) {
        return new Either<L, R>(Objects.requireNonNull(left), null);
    }

    /**
     * Getter with alternative.
     *
     * @param orElse an alternative value, in case the left value had not been initialised.
     * @return the left value or the alternative value
     * @throws NullPointerException when the <code>orElse</code> parameter is <code>null</code>.
     */
    @NotNull
    @NotModified
    public A getLeftOrElse(@NotNull A orElse) {
        return left != null ? left : Objects.requireNonNull(orElse);
    }

    /**
     * Getter with alternative.
     *
     * @param orElse an alternative value, in case the right value had not been initialised.
     * @return the right value or the alternative value
     * @throws NullPointerException when the <code>orElse</code> parameter is <code>null</code>.
     */
    @NotNull
    @NotModified
    public B getRightOrElse(@NotNull B orElse) {
        return right != null ? right : Objects.requireNonNull(orElse);
    }

    /**
     * Delegating equals.
     *
     * @param o the other value
     * @return <code>true</code> when <code>o</code> is also an <code>Either</code> object, with
     * the same left or right object, as defined by the <code>equals</code> method on <code>A</code>
     * or <code>B</code> respectively.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Either<?, ?> either = (Either<?, ?>) o;
        return Objects.equals(left, either.left) &&
                Objects.equals(right, either.right);
    }

    /**
     * Delegating hash code.
     *
     * @return a hash code based on the hash code of the left or right value.
     */
    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
}
