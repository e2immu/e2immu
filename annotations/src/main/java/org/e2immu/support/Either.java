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

package org.e2immu.support;

import org.e2immu.annotation.*;

import java.util.Objects;

/**
 * An object holding either an object of type <code>A</code>, or one of type <code>B</code>; it cannot hold both or neither.
 *
 * <code>Either</code> is a typical example of a {@link E2Container} without support data:
 * final fields of an unbound parameter type, that are {@link NotModified}.
 *
 * @param <A>
 * @param <B>
 */
@E2Container
public class Either<A, B> {

    // because getLeft and getRight cause exceptions
    @Nullable(contract = true)
    private final A left;
    @Nullable(contract = true)
    private final B right;

    private static <A, B> boolean Either$Precondition(A a, B b) { return a==null && b!=null || a!=null && b==null; }
    private Either(@Nullable A a, @Nullable B b) {
        if ((a == null && b == null) || (a != null && b != null)) throw new IllegalArgumentException();
        left = a;
        right = b;
    }

    /**
     * This method should only be called when the left value has been initialised.
     *
     * @return the left value, of type A.
     * @throws NullPointerException when the right value had been initialised.
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
     * @throws NullPointerException when the left value had been initialised.
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
        return new Either<>(null, Objects.requireNonNull(right));
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
        return new Either<>(Objects.requireNonNull(left), null);
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
        A local = left;
        return local != null ? local : Objects.requireNonNull(orElse);
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
        B local = right;
        return local != null ? local : Objects.requireNonNull(orElse);
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

    @Override
    public String toString() {
        return "[" + left + "|" + right + "]";
    }
}
