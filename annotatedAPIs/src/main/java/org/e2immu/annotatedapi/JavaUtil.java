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

package org.e2immu.annotatedapi;

import org.e2immu.annotation.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JavaUtil extends AnnotatedAPI {
    final static String PACKAGE_NAME = "java.util";

    static boolean setAddModificationHelper(int i, int j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j : i == j + 1):
                isKnown(true) ? i == j + 1: i >= j && i <= j+1;
    }

    static boolean setAddValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? !containsE : (isKnown(true) || size == 0 || retVal);
    }

    static boolean setRemoveModificationHelper(int i, int j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j - 1 : i == j):
                isKnown(true) ? i == j : i >= j-1 && i <= j;
    }

    static boolean setContainsValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? containsE : !isKnown(true) && size > 0 && retVal;
    }

    // Note: we can use T instead of E (in the byte-code), since we use distinguishingName instead of fullyQualifiedName
    interface Iterator$<T> {
        @NotModified
        default void forEachRemaining(Consumer<? super T> action) {
        }
        // rest has no annotations
    }

    @Container
    // this is not in line with the JDK, but we will block null keys!
    static class Collection$<E>  {

        boolean add$Postcondition(E e) { return contains(e); }
        boolean add(@NotNull E e) { return true; }

        @Independent
        boolean addAll(@NotNull1 java.util.Collection<? extends E> collection) { return true; }

        boolean clear$Clear$Size(int i) { return i == 0; }
        @Modified
        void clear() { }

        static boolean contains$Value$Size(int i, Object o, boolean retVal) { return i != 0 && retVal; }
        @NotModified
        boolean contains(@NotNull Object object) { return true; }

        static boolean containsAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) { return i != 0 && retVal; }
        @NotModified
        boolean containsAll(@NotNull1 java.util.Collection<?> c) { return true; }

        // there is a "default forEach" in Iterable, but here we can guarantee that consumer is @NotNull1 (its
        // arguments will not be null either)
        // IMPROVE allow for "overrides" (copies)
        //void forEach(@NotNull1 Consumer<? super E> action) {}

        boolean isEmpty$Value$Size(int i, boolean retVal) { return i == 0; }
        @NotModified
        boolean isEmpty() { return true; }

        // there is a "default forEach" in Iterable, but here we can guarantee that consumer is @NotNull1 (its
        // arguments will not be null either)
        void forEach(@NotNull1 Consumer<? super E> action) {}

        boolean remove$Modification$Size(int i, int j) { return i <= j && i >= j - 1; }
        boolean remove$Value$Size(int i, Object object, boolean retVal) { return i != 0 && retVal; }
        boolean remove$Postcondition(Object object) { return !contains(object); }
        boolean remove(@NotNull Object object) { return true; }

        boolean removeAll$Modification$Size(int i, int j, java.util.Collection<?> c) { return i >= j - c.size() && i <= j; }
        boolean removeAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) { return i != 0 && c.size() != 0 && retVal; }
        @Independent
        boolean removeAll(@NotNull1 java.util.Collection<?> c) { return true; }

        boolean retainAll$Modification$Size(int i, int j, java.util.Collection<?> c) { return i <= c.size() && i <= j; }
        boolean retainAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) { return i != 0 && c.size() != 0 && retVal; }
        @Independent
        boolean retainAll(@NotNull1 java.util.Collection<?> c) { return true; }

        boolean size$Invariant$Size(int i) { return i >= 0; }
        void size$Aspect$Size() {}
        @NotModified
        int size() { return 0; }

        int stream$Transfer$Size(int i) { return i; }
        @NotNull1
        @NotModified
        Stream<E> stream() { return null; }

        int toArray$Transfer$Size(int i) { return i; }
        @NotNull1
        @NotModified
        Object[] toArray() { return null; }

        <T> int toArray$Transfer$Size(int i, T[] a) { return i; }
        @NotNull1
        @Independent
        @NotModified
        <T> T[] toArray(@NotNull1 T[] a) { return null; }

        <T> int toArray$Transfer$Size(int i, IntFunction<T[]> g) { return i; }
        @NotNull1
        @Independent
        @NotModified
        <T> T[] toArray(@NotNull IntFunction<T[]> generator) { return null; }
    }


    @Container
    // this is not in line with the JDK, but we will block null keys!
    static class List$<E> {

        boolean add$Modification$Size(int i, int j, E e) { return i == j + 1; }
        boolean add$Value(E e, boolean retVal) { return true; }
        boolean add$Postcondition(E e) { return contains(e); }
        boolean add(@NotNull E e) { return false; /* actually, true, see $Value */ }

        boolean addAll$Modification$Size(int i, int j, java.util.Collection<? extends E> c) { return i == j + c.size(); }
        boolean addAll$Value(java.util.Collection<? extends E> c, boolean retVal) { return true; }
        // IMPROVE causes problems with method resolution
        // boolean addAll$Postcondition(java.util.Collection<? extends E> c) { return c.stream().allMatch(this::contains); }
        @Independent
        boolean addAll(@NotNull1 Collection<? extends E> collection) { return false; }

        // needed here because it is used by a companion of 'add'.
        static boolean contains$Value$Size(int i, Object o, boolean retVal) { return i != 0 && retVal; }
        @NotModified
        boolean contains(@NotNull Object object) { return false; }

        @NotNull1
        java.util.Iterator<E> iterator() { return null; }

        static boolean get$Precondition$Size(int size, int index) { return index < size; }
        @NotModified
        @NotNull
        E get(int index) { return null; }

        int of$Transfer$Size() { return 0; }
        @NotModified
        @NotNull1
        @E2Container
        static <EE> java.util.List<EE> of() { return null; }

        <F> int of$Transfer$Size(F e1) { return 1; }
        <F> boolean of$Postcondition(F e1) { return contains(e1); }
        @NotModified
        @NotNull1
        @E2Container
        static <F> java.util.List<F> of(@NotNull F e1) { return null; }

        <F> int of$Transfer$Size(F f1, F f2) { return 2; }
        @NotModified
        @NotNull1
        @E2Container
        <G> java.util.List<G> of(@NotNull G e1, @NotNull G e2) { return null; }

        <F> int of$Transfer$Size(F f1, F f2, F f3) { return 3; }
        @NotModified
        @NotNull1
        @E2Container
        <H> java.util.List<H> of(@NotNull H e1, @NotNull H e2, @NotNull H e3) { return null; }

        boolean remove(@NotNull Object object) { return false; }

        @Independent
        boolean removeAll(@NotNull1 Collection<?> c) { return false; }

        @NotModified
        @NotNull1
        java.util.List<E> subList(int fromIndex, int toIndex) { return null; }

        @NotNull1
        @NotModified
        Object[] toArray() { return null; }

        @NotNull1
        @Independent
        @NotModified
        <T> T[] toArray(@NotNull1 T[] a) { return null; }
    }

    // IMPROVE for now we have to repeat the method+companions from Collection, as companions are not inherited

    @Container
    // this is not in line with the JDK, but we will block null keys!
    static class Set$<E> {

        // note that with the $, we're really in java.util.Set, so we have no knowledge of addModificationHelper unless we add it to the
        // type context IMPROVE not really trivial to sort out
        boolean add$Modification$Size(int i, int j, E e) { return org.e2immu.annotatedapi.JavaUtil.setAddModificationHelper(i, j, contains(e)); }
        boolean add$Value$Size(int size, E e, boolean retVal) { return org.e2immu.annotatedapi.JavaUtil.setAddValueHelper(size, contains(e), retVal); }
        boolean add$Remove(E e) { return !contains(e); }
        boolean add$Postcondition(E e) { return contains(e); }
        boolean add(@NotNull E e) { return true; }

        boolean addAll$Clear$Size(int i, Collection<? extends E> c) { return i >= c.size(); } // do NOT add isKnown()
        @Independent
        boolean addAll(@NotNull1 java.util.Collection<? extends E> collection) { return true; }

        boolean clear$Clear$Size(int i) { return i == 0 && org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false); }
        @Modified
        void clear() { }

        boolean contains$Value$Size(int i, Object o, boolean retVal) { return org.e2immu.annotatedapi.JavaUtil.setContainsValueHelper(i, contains(o), retVal); }
        @NotModified
        boolean contains(@NotNull Object object) { return true; }

        boolean isEmpty$Value$Size(int i, boolean retVal) { return i == 0; }
        @NotModified
        boolean isEmpty() { return true; }

        @NotNull1
        java.util.Iterator<E> iterator() { return null; }

        int of$Transfer$Size() { return 0; }
        @NotModified
        @NotNull1
        @E2Container
        static <EE> java.util.Set<EE> of() { return null; }

        //<F> int of$Transfer$Size(F e1) { return 1; }
        //<F> boolean of$Postcondition(F e1) { return contains(e1); }
        @NotModified
        @NotNull1
        @E2Container
        static <F> java.util.Set<F> of(@NotNull F e1) { return null; }

       // IMPROVE advanced <F> int of$Postcondition$Size(F f1, F f2, java.util.Set<F> retVal) { return isFact(f1.equals(f2)) ? (f1.equals(f2) ? 1: 2): retVal.size(); }
        @NotModified
        @NotNull1
        @E2Container
        <G> java.util.Set<G> of(@NotNull G e1, @NotNull G e2) { return null; }

        @NotModified
        @NotNull1
        @E2Container
        <H> java.util.Set<H> of(@NotNull H e1, @NotNull H e2, @NotNull H e3) { return null; }

        boolean remove$Modification$Size(int i, int j, Object o) { return org.e2immu.annotatedapi.JavaUtil.setRemoveModificationHelper(i, j, contains(o)); }
        boolean remove$Value$Size(int i, Object o, boolean retVal) { return org.e2immu.annotatedapi.JavaUtil.setContainsValueHelper(i, contains(o), retVal);}
        boolean remove$Remove(Object object) { return contains(object); }
        boolean remove$Postcondition(Object object) { return !contains(object); }
        @Modified
        boolean remove(@NotNull Object object) { return true; }
    }

    @Container
    static class ArrayList$<E> {

        // tested in BCM_0, _1
        boolean ArrayList$Modification$Size(int post) { return post == 0; }
        public ArrayList$() {
        }

        boolean ArrayList$Modification$Size(int post, int size) { return post == 0; }
        public ArrayList$(int size) {
        }
    }

    @Container
    static class HashSet$<E> {

        // content is known
        boolean HashSet$Modification$Size(int post) { return post == 0; }
        boolean HashSet$Postcondition() { return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false); }

        @Independent
        public HashSet$() {
        }

        // content is not known
        boolean HashSet$Modification$Size(int post, Collection<? extends  E> c) { return post == c.size(); }
        @Independent
        public HashSet$(@NotNull1 Collection<? extends  E> c) {
        }
    }


    @UtilityClass
    @Container
    static class Objects$ {
        @NotNull
        @NotModified
        @Identity
        public static <T> T requireNonNull(@NotNull T t) {
            return null;
        }

        @NotNull
        @NotModified
        @Identity
        public static <T> T requireNonNull(@NotNull T t, String message) {
            return null;
        }

        @NotNull
        @NotModified
        public static <T> T requireNonNullElse(T obj, T defaultObj) {
            return  null;
        }

        @NotModified
        public static int hashCode(Object object) {
            return  0;
        }

        @NotModified
        public static int hash(Object... values) {
            return 0;
        }

        @NotModified
        public static boolean equals(Object left, Object right) {
            return false;
        }
    }

    // again this goes against the API, but we want to raise problems when comparing with null
    static class Comparator$<T> {

        int compare$Value(T o1, T o2, int retVal) { return o1.equals(o2) || o2.equals(o1) ? 0: retVal; }
        int compare(@NotModified T o1, @NotModified T o2) { return 0; }

        static <U> java.util.Comparator<U> comparingInt(@NotNull ToIntFunction<? super U> keyExtractor) { return null; }
    }

    @E2Container
    static class Optional$<T> {
        @Independent
        @NotNull
        static <T> java.util.Optional<T> empty() { return java.util.Optional.empty();
        }

        @NotNull
        static <T> java.util.Optional<T> of(@NotNull T t) { return java.util.Optional.empty();
        }

        @NotNull
        static <T> java.util.Optional<T> ofNullable(T t) { return java.util.Optional.empty();
        }

        @NotNull
        T get() { return null;
        }

        T orElse(T other) { return null;
        }

        @NotNull
        T orElseThrow() { return null;
        }

        @NotNull
        <X extends Throwable> T orElseThrow(@NotNull Supplier<? extends X> exceptionSupplier) {
            return null;
        }

        boolean isEmpty() { return false;
        }

        boolean isPresent() { return false;
        }
    }

    @UtilityClass
    static class Arrays$ {
        @NotNull
        static IntStream stream(@NotNull int[] array) {
            return null;
        }
    }

    @UtilityClass
    static class Collections$ {

        static <T> boolean addAll(@NotNull @Modified Collection<? super T> c,
                                  @NotModified T... elements) { return false; }
    }

    @Container
    static class Map$<K, V> {

        boolean clear$Clear$Size(int i) { return i == 0; }
        @Modified
        void clear() { }

        boolean containsKey$Value$Size(int i, Object key, boolean retVal) { return i != 0 && retVal; }
        @NotModified
        boolean containsKey(@NotNull Object key) { return true; }

        boolean size$Invariant$Size(int i) { return i >= 0; }
        void size$Aspect$Size() {}
        @NotModified
        int size() { return 0; }

        int entrySet$Transfer$Size(int i) { return i; }
        @NotNull1
        @NotModified
        Set<Map.Entry<K, V>> entrySet() { return null; }

        @NotModified
        V get(Object key) { return null; }

        @NotNull1
        @NotModified
        Collection<V> values() { return null; }
    }

    @Container
    static class HashMap$<K, V> {
        // content is known
        boolean HashMap$Modification$Size(int post) { return post == 0; }
        boolean HashMap$Postcondition() { return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false); }

        @Independent
        public HashMap$() {
        }

        // content is not known
        boolean HashMap$Modification$Size(int post, Map<? extends  K, ? extends V> map) { return post == map.size(); }
        @Independent
        public HashMap$(@NotNull1 Map<? extends  K, ? extends V> map) {
        }
    }
}
