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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.*;
import org.e2immu.annotation.rare.IgnoreModifications;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class E2ImmutableComposition_0 {

    @ImmutableContainer
    interface Marker {
    }

    /**
     * This as an @E2Immutable abstract type.
     * If semantically used correctly, then hiding its content completely, and only showing an aspect.
     */
    @ImmutableContainer
    interface HasSize {

        @NotModified
        int size();

        @NotModified
        default boolean isEmpty() {
            return size() == 0;
        }
    }

    /**
     * This as an @E2Immutable abstract type.
     * We've added a non-modifying, non-exposing (independent) method.
     * Semantically, this type is meant to hold data of the unbound parameter type {@link T}.
     *
     * @param <T>
     */
    @ImmutableContainer
    interface NonEmptyImmutableList<T> extends HasSize {

        @NotModified
        @Independent
        T first();

        /**
         * @param consumer It has the annotation {@link IgnoreModifications} implicitly, because {@link Consumer}
         *                 is an abstract type in the package {@link java.util.function}.
         */
        @NotModified
        void visit(@Independent(hc = true) Consumer<T> consumer);

        @ImmutableContainer("false")
        @Override
        default boolean isEmpty() {
            return false;
        }
    }

    /**
     * This as an abstract type that has lost its immutability because of the setFirst method.
     * Most implementations will use an assignment to set the first element, but that need not be the case.
     * The former ({@link One}) will be @Container, whereas the latter ({@link OneWithOne}) will be {@link FinalFields}.
     *
     * @param <T>
     */
    @Container
    interface NonEmptyList<T> extends NonEmptyImmutableList<T> {

        @NotModified
        T first();

        @Modified
        void setFirst(T t);
    }

    /**
     * One is an assignment-based implementation of {@link NonEmptyList}.
     * <p>
     * Fields: variable
     *
     * @param <T>
     */
    @Container
    static class One<T> implements NonEmptyList<T> {
        @FinalFields(absent = true)
        @NotModified
        private T t;

        @Override
        public T first() {
            return t;
        }

        @Override
        public void setFirst(@NotModified T t) {
            this.t = t;
        }

        @Override
        public int size() {
            return 1;
        }

        @NotModified
        @Override
        public void visit(@Independent(hc = true) Consumer<T> consumer) {
            consumer.accept(t);
        }
    }

    /**
     * OneWithOne is a modification-based implementation of {@link NonEmptyList}.
     * <p>
     * Fields: {@link FinalFields} ({@link Final} and {@link Container}, but {@link Modified}).
     *
     * @param <T>
     */
    @FinalFields
    @Container
    static class OneWithOne<T> implements NonEmptyList<T> {
        private final One<T> one = new One<>();

        @Override
        public T first() {
            return one.first();
        }

        @Override
        public void setFirst(T t) {
            one.setFirst(t);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public void visit(Consumer<T> consumer) {
            consumer.accept(first());
        }
    }

    /**
     * Definition of constant type:
     * <ul>
     *     <li>the type is final</li>
     *     <li>all its fields are literal constants (also String), or themselves (recursively defined) of constant type</li>
     *     <li>all its methods return values of constant type</li>
     * </ul>
     * Constant types are deeply immutable, and therefore, also @E2Immutable, but not too interesting.
     * <p>
     * Fields: {@link ImmutableContainer} with constant value
     */
    @ImmutableContainer
    static final class ConstantOne implements NonEmptyImmutableList<Integer> {
        @ImmutableContainer("3")
        public static final int VALUE = 3;

        @ImmutableContainer("3")
        @Override
        public Integer first() {
            return VALUE;
        }

        @ImmutableContainer("1")
        @Override
        public int size() {
            return 1;
        }

        @Override
        public void visit(Consumer<Integer> consumer) {
            consumer.accept(1); // this should raise an error or warning... 1 is not part of the E2 content
        }
    }

    /**
     * This is an @E2Container implementation of {@link NonEmptyImmutableList}, holding data that is inaccessible.
     * <p>
     * Field composition: Inaccessible data
     */
    @ImmutableContainer(hc = true)
    static class ImmutableOne<T> implements NonEmptyImmutableList<T> {
        private final T t;

        public ImmutableOne(T t) {
            this.t = t;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public T first() {
            return t;
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<T> consumer) {
            consumer.accept(t);
        }
    }

    /**
     * First of a series of variants based on an array field, all implementing {@link NonEmptyImmutableList}.
     * <p>
     * The array itself is mutable, and is being accessed. It is not modified outside the constructor.
     * <p>
     * In this example, the array is fully shielded.
     * The data inside the array is transparent: nowhere in this type, its fields, methods or constructors
     * are accessed, and it is not passed on as an argument where its type is expected.
     * This, however, has no bearing on immutability: while the <code>One&lt;Integer&gt;</code> type has no hidden content,
     * it is mutable.
     * <p>
     * Note that we use a {@link Supplier} to obtain values for the array. If we had used the constructor of {@link One},
     * the type would not have been transparent.
     * <p>
     * Field composition: Modifiable data containing transparent data
     */
    @Container
    static class ImmutableArrayOfTransparentOnes implements NonEmptyImmutableList<One<Integer>> {

        private final One<Integer>[] ones;

        public ImmutableArrayOfTransparentOnes(One<Integer>[] source, Supplier<One<Integer>> generator) {
            ones = source.clone(); // to keep One transparent
            Arrays.setAll(ones, i -> generator.get());
        }

        @Override
        public int size() {
            return ones.length;
        }

        @Override
        public One<Integer> first() {
            return ones[0];
        }

        @NotModified
        public One<Integer> get(int index) {
            return ones[index];
        }

        @Override
        public void visit(Consumer<One<Integer>> consumer) {
            for (One<Integer> one : ones) consumer.accept(one);
        }
    }

    /**
     * Second of a series of variants based on an array field.
     * <p>
     * This one shows the actual replacement of the transparent immutable data of type {@link One<Integer>}
     * by the unbound parameter type {@link T}, which is inaccessible rather than transparent.
     * <p>
     * Note that we have to use {@link Object} to create the array.
     * <p>
     * Field composition: Modifiable data containing inaccessible data
     *
     * @param <T>
     */
    @ImmutableContainer(hc = true)
    static class ImmutableArrayOfT<T> implements NonEmptyImmutableList<T> {

        private final T[] ts;

        @SuppressWarnings("unchecked")
        public ImmutableArrayOfT(int size, @Independent(hc = true) Supplier<T> generator) {
            ts = (T[]) new Object[size];
            Arrays.setAll(ts, i -> generator.get());
        }

        @Override
        public int size() {
            return ts.length;
        }

        @Override
        public T first() {
            return ts[0];
        }

        @NotModified
        @Independent(hc = true)
        public T get(int index) {
            return ts[index];
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<T> consumer) {
            for (T t : ts) consumer.accept(t);
        }
    }

    /**
     * Third of a series of variants based on an array field.
     * <p>
     * This one shows the actual replacement of the transparent type in {@link One<Integer>}
     * of the first in the series, by the (immutable) marker interface {@link Marker}.
     * <p>
     * Note that we still have to use a generator to obtain sensible values for the {@link Marker} objects.
     * <p>
     * Field composition: Modifiable data containing inaccessible data.
     */
    @ImmutableContainer(hc = true)
    static class ImmutableArrayOfMarker implements NonEmptyImmutableList<Marker> {

        private final Marker[] markers;

        public ImmutableArrayOfMarker(int size, @Independent(hc = true) Supplier<Marker> generator) {
            markers = new Marker[size];
            Arrays.setAll(markers, i -> generator.get());
        }

        @Override
        public int size() {
            return markers.length;
        }

        @Override
        @Independent(hc = true)
        public Marker first() {
            return markers[0];
        }

        @NotModified
        public Marker get(int index) {
            return markers[index];
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<Marker> consumer) {
            for (Marker marker : markers) consumer.accept(marker);
        }
    }

    /**
     * Fourth of a series of variants based on an array field.
     * <p>
     * This one holds {@link HasSize} objects, and distinguishes itself from the first three by actually making use
     * of the methods in this type.
     * <p>
     * Note that we still have to use a generator to obtain sensible values for the {@link HasSize} objects.
     * <p>
     * Field composition: Modifiable data containing ERImmutable data.
     * The ERImmutable data is as close as possible to inaccessible data, but is accessible nevertheless.
     */
    @ImmutableContainer(hc = true)
    static class ImmutableArrayOfHasSize implements NonEmptyImmutableList<HasSize> {

        private final HasSize[] elements;

        public ImmutableArrayOfHasSize(int size, @Independent(hc = true) Supplier<HasSize> generator) {
            elements = new HasSize[size];
            Arrays.setAll(elements, i -> generator.get());
        }

        @Override
        public int size() {
            return Arrays.stream(elements).mapToInt(HasSize::size).sum();
        }

        @Override
        @Independent(hc = true)
        public HasSize first() {
            return elements[0];
        }

        @NotModified
        public HasSize get(int index) {
            return elements[index];
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<HasSize> consumer) {
            for (HasSize element : elements) consumer.accept(element);
        }
    }


    /**
     * Fifth of a series of variants based on an array field.
     * <p>
     * This one holds {@link HasSize} objects, and exposes the array in 2 ways: via the return value of <code>getElements</code>,
     * and the argument of <code>visitArray</code>. As a consequence, the type is not immutable anymore.
     * <p>
     * Fields: dependent
     */
    @FinalFields
    static class ExposedArrayOfHasSize implements NonEmptyImmutableList<HasSize> {

        private final HasSize[] elements;

        public ExposedArrayOfHasSize(int size, Supplier<HasSize> generator) {
            elements = new HasSize[size];
            Arrays.setAll(elements, i -> generator.get());
        }

        @Override
        public int size() {
            return Arrays.stream(elements).mapToInt(HasSize::size).sum();
        }

        @Override
        public HasSize first() {
            return elements[0];
        }

        @NotModified
        @Independent(absent = true)
        public HasSize[] getElements() {
            return elements;
        }

        /*
         visitArray and getElements are identical wrt independence: external modifications
         to the array are possible
         */
        @Modified
        public void visitArray(@Independent(absent = true) Consumer<HasSize[]> consumer) {
            consumer.accept(elements);
        }

        @Override
        public void visit(@Independent Consumer<HasSize> consumer) {
            for (HasSize element : elements) consumer.accept(element);
        }
    }

    /**
     * Sixth of a series of variants based on an array field.
     * <p>
     * This one holds {@link HasSize} objects, encapsulated in an {@link ImmutableOne} object.
     * Encapsulating inside a level 2 immutable type has no effect on the final immutability status of the type.
     * <p>
     * Fields: dependent, even if they're encapsulated in an {@link Immutable}.
     */
    @FinalFields
    static class EncapsulatedExposedArrayOfHasSize implements NonEmptyImmutableList<HasSize> {

        private final ImmutableOne<HasSize[]> one;

        public EncapsulatedExposedArrayOfHasSize(int size, @Independent(hc = true) Supplier<HasSize> generator) {
            HasSize[] elements = new HasSize[size];
            Arrays.setAll(elements, i -> generator.get());
            one = new ImmutableOne<>(elements);
        }

        @Override
        public int size() {
            return Arrays.stream(one.first()).mapToInt(HasSize::size).sum();
        }

        @Override
        @Independent(hc = true)
        public HasSize first() {
            return one.first()[0];
        }

        @NotModified
        @Independent(absent = true) // dependent!!
        public HasSize[] getElements() {
            return one.first();
        }

        // should raise an error, elements are not E2 content
        @Override
        public void visit(@Independent(hc = true) Consumer<HasSize> consumer) {
            for (HasSize element : one.first()) consumer.accept(element);
        }
    }


    /**
     * Seventh of a series of variants based on an array field.
     * <p>
     * This one holds {@link HasSize} objects, encapsulated in an {@link ImmutableOne} object.
     * <p>
     * Fields: {@link FinalFields}, independent, but modified, even if encapsulated in {@link Immutable}.
     */
    @FinalFields
    static class EncapsulatedAssignableArrayOfHasSize implements NonEmptyImmutableList<HasSize> {

        private final ImmutableOne<HasSize[]> one;

        public EncapsulatedAssignableArrayOfHasSize(int size, @Independent(hc = true) Supplier<HasSize> generator) {
            HasSize[] elements = new HasSize[size];
            Arrays.setAll(elements, i -> generator.get());
            one = new ImmutableOne<>(elements);
        }

        @Override
        public int size() {
            return Arrays.stream(one.first()).mapToInt(HasSize::size).sum();
        }

        @Override
        @Independent(hc = true)
        public HasSize first() {
            return one.first()[0];
        }

        @Modified
        public void set(int index, @Independent(hc = true) HasSize hasSize) {
            one.first()[index] = hasSize;
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<HasSize> consumer) {
            for (HasSize element : one.first()) consumer.accept(element);
        }
    }


    /**
     * Eight of a series of variants based on an array field.
     * <p>
     * Building on {@link ImmutableArrayOfHasSize}.
     * <p>
     * Field composition: E2Immutable data containing modifiable data containing ERImmutable data.
     * For the (in)dependent computation, this boils down to modifiable data containing ERImmutable data.
     * No hidden content.
     */
    @ImmutableContainer(hc = true)
    static class EncapsulatedImmutableArrayOfHasSize implements NonEmptyImmutableList<HasSize> {

        private final ImmutableOne<HasSize[]> one;

        public EncapsulatedImmutableArrayOfHasSize(int size, @Independent(hc = true) Supplier<HasSize> generator) {
            HasSize[] elements = new HasSize[size];
            Arrays.setAll(elements, i -> generator.get());
            one = new ImmutableOne<>(elements);
        }

        @Override
        public int size() {
            HasSize[] first = one.first();
            assert first != null;
            return Arrays.stream(first).mapToInt(HasSize::size).sum();
        }

        @Override
        @Independent(hc = true)
        public HasSize first() {
            HasSize[] first = one.first();
            assert first != null;
            return first[0];
        }

        @NotModified
        @Independent(hc = true)
        public HasSize get(int index) {
            HasSize[] first = one.first();
            assert first != null;
            return first[index];
        }

        @Override
        public void visit(@Independent(hc = true) Consumer<HasSize> consumer) {
            for (HasSize element : one.first()) consumer.accept(element);
        }
    }

    /**
     * Eight of a series of variants based on an array field.
     * <p>
     * Building on {@link ImmutableArrayOfHasSize}.
     * <p>
     * Field composition: Modifiable data containing constant data.
     */
    @ImmutableContainer
    static class ArrayOfConstants implements NonEmptyImmutableList<String> {

        private final String[] strings = {"a", "b", "c"};

        @Override
        public int size() {
            return strings.length;
        }

        @Override
        @ImmutableContainer("a") // no error, the constant is @Independent
        public String first() {
            return strings[0];
        }

        @NotModified
        public String get(int index) {
            return strings[index];
        }

        @Override
        public void visit(Consumer<String> consumer) {
            for (String string : strings) consumer.accept(string);
        }
    }
}
