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

package org.e2immu.annotatedapi.java;

import org.e2immu.annotatedapi.AnnotatedAPI;
import org.e2immu.annotation.*;
import org.e2immu.annotation.Commutable;
import org.e2immu.annotation.type.UtilityClass;

import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/*
TODO Implementation issue: JavaUtil must be public, and marked @Independent as a consequence.
 */
@Independent
public class JavaUtil extends AnnotatedAPI {
    final static String PACKAGE_NAME = "java.util";

    // it is important that these helper methods are 'public', because the shallow
    // method analyser only considers public methods

    /**
     * @param i         the "post" value
     * @param j         the "pre" value, null when it is not known
     * @param containsE a placeholder for the actual clause
     * @return an expression indicating the new state
     */
    public static boolean setAddModificationHelper(int i, Integer j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j : i == j + 1) :
                isKnown(true) ? (j == null ? i >= 1 : i == j + 1) : (j == null ? i >= 1 : i >= j && i <= j + 1);
    }

    public static boolean setAddValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? !containsE : (isKnown(true) || size == 0 || retVal);
    }

    // TODO j == null situation
    public static boolean setRemoveModificationHelper(int i, Integer j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j - 1 : i == j) :
                isKnown(true) ? i == j : i >= j - 1 && i <= j;
    }

    public static boolean setContainsValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? containsE : !isKnown(true) && size > 0 && retVal;
    }

    /*
     Currently, we cannot make any distinction between the @Modified in remove() which acts on the underlying collection,
     and the @Modified in hasNext()/next() which acts on the counting system of the iterator.
     The critical annotation is on the iterator() method in Collection, which is either dependent (to allow remove(),
     but it messes up normal iteration), or independent, which plays nice with iterating but not with removal.
     Because iterating without removal can, in many cases, be replaced by a for-each loop or a stream,
     we believe we can live with this at the moment.
     
     @Independent(hc=true): the purpose of an iterator is to expose the content of a type!!
     */
    @Container
    @Independent(hc = true)
    interface Iterator$<T> {
        @Modified
        default void forEachRemaining(@NotNull @Independent(hc = true) Consumer<? super T> action) {
        }

        @Modified
        boolean hasNext();

        @Modified
        @Independent(hc = true)
        T next();

        @Modified
        void remove();
    }

    @Container
    @Independent(hc = true)
    interface ListIterator$<T> {

    }

    @Container
    @Independent(hc = true)
    interface PrimitiveIterator$<T> {

        /*
        contract=true to override the expected independence with hidden content
         */
        @Container
        @Independent(contract = true)
        interface OfInt {

        }

        @Container
        @Independent(contract = true)
        interface OfLong {

        }

        @Container
        @Independent(contract = true)
        interface OfDouble {

        }
    }

    @UtilityClass
    interface Spliterators$ {

        @Independent(hc = true)
        <T> Spliterator<T> spliterator(Object[] array, int additionalCharacteristics);
    }

    /*
     This is not in line with the JDK, but we will block null keys!
     Dependent because of remove() in iterator.
     */
    @Container
    interface Collection$<E> {

        default boolean add$Postcondition(E e) {
            return contains(e);
        }

        @Commutable(seq = "add", multi = "addAll")
        @Modified
        boolean add(@Independent(hc = true) @NotNull E e);

        @Modified
        boolean addAll(@Independent(hc = true) @NotNull(content = true) Collection<? extends E> collection);

        default boolean clear$Clear$Size(int i) {
            return i == 0;
        }

        @Modified
        void clear();

        default boolean contains$Value$Size(int i, Object o, boolean retVal) {
            return i != 0 && retVal;
        }

        /*
        not modifying, so implicitly @Independent
         */
        boolean contains(@NotNull Object object);

        default boolean containsAll$Value$Size(int i, Collection<?> c, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean containsAll(@NotNull(content = true) Collection<?> c);

        default boolean isEmpty$Value$Size(int i, boolean retVal) {
            return i == 0;
        }

        boolean isEmpty();

        /*
        @Dependent, because of the remove() in Iterator.
         */
        @NotNull(content = true)
        java.util.Iterator<E> iterator();

        /*
         there is a "default forEach" in Iterable, but here we can guarantee that consumer is @NotNull(content=true):
         its arguments will not be null either.
         @NotModified because the default for void methods in @Container is @Modified.
         */
        @NotModified
        void forEach(@Independent(hc = true) @NotNull(content = true) Consumer<? super E> action);

        default boolean remove$Modification$Size(int i, Integer j) {
            return j == null ? i >= 0 : (i <= j && i >= j - 1);
        }

        default boolean remove$Value$Size(int i, Object object, boolean retVal) {
            return i != 0 && retVal;
        }

        default boolean remove$Postcondition(Object object) {
            return !contains(object);
        }

        /*
         The object is only used for comparison, it is never stored!
         */
        @Modified
        boolean remove(@Independent @NotNull Object object);

        default boolean removeAll$Modification$Size(int i, Integer j, Collection<?> c) {
            return j == null ? i >= 0 : (i >= j - c.size() && i <= j);
        }

        default boolean removeAll$Value$Size(int i, Collection<?> c, boolean retVal) {
            return i != 0 && c.size() != 0 && retVal;
        }

        @Modified
        boolean removeAll(@Independent @NotNull(content = true) Collection<?> c);

        @Modified
        boolean removeIf(@Independent(hc = true) @NotNull Predicate<? super E> filter);

        default boolean retainAll$Modification$Size(int i, Integer j, Collection<?> c) {
            return i <= c.size() && i <= (j == null ? 0 : j);
        }

        default boolean retainAll$Value$Size(int i, Collection<?> c, boolean retVal) {
            return i != 0 && c.size() != 0 && retVal;
        }

        @Modified
        boolean retainAll(@Independent @NotNull(content = true) Collection<?> c);

        default boolean size$Invariant$Size(int i) {
            return i >= 0;
        }

        default void size$Aspect$Size() {
        }

        int size();

        default int stream$Transfer$Size(int i) {
            return i;
        }

        /*
        Streams are @E2Container
         */
        @NotNull(content = true)
        @Independent(hc = true)
        Stream<E> stream();

        default int toArray$Transfer$Size(int i) {
            return i;
        }

        @NotNull(content = true)
        @Independent(hc = true)
        Object[] toArray();

        default <T> int toArray$Transfer$Size(int i, T[] a) {
            return i;
        }

        /*
         Important: we're contradicting the container property here.
         Use of this method will result in a warning that the container property is being violated.
         */
        @NotNull(content = true)
        @Independent(hc = true)
        <T> T[] toArray(@Independent(hc = true) @NotNull @Modified T[] a);

        default <T> int toArray$Transfer$Size(int i, IntFunction<T[]> g) {
            return i;
        }

        @NotNull(content = true)
        @Independent(hc = true)
        <T> T[] toArray(@NotNull IntFunction<T[]> generator);
    }

    // this is not in line with the JDK, but we will block null keys!

    @Container
    interface List$<E> {

        default boolean add$Modification$Size(int i, Integer j, E e) {
            return j == null ? i > 0 : i == j + 1;
        }

        // this is not in line with the JDK, but we will block null keys!
        default boolean add$Value(E e, boolean retVal) {
            return true;
        }

        default boolean add$Postcondition(E e) {
            return contains(e);
        }

        /*
         @Modified inherited; we're not (yet) inheriting companion methods.
         @Independent(hc=true), @NotNull on parameter "e" inherited.
         */
        @Commutable(seq = "add", multi = "addAll")
        boolean add(E e);

        @Modified
        void add(int index, @NotNull E e);

        default boolean addAll$Modification$Size(int i, Integer j, Collection<? extends E> c) {
            return i == (j == null ? 0 : j) + c.size();
        }

        default boolean addAll$Value(Collection<? extends E> c, boolean retVal) {
            return true;
        }

        // IMPROVE causes problems with method resolution
        // boolean addAll$Postcondition(Collection<? extends E> c) { return c.stream().allMatch(this::contains); }
        //@Modified inherited
        boolean addAll(Collection<? extends E> collection); // @Independent1, @NotNull1 inherited

        // needed here because it is used by a companion of 'add'.
        default boolean contains$Value$Size(int i, Object o, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean contains(Object object);

        /*
         Static method, producing an effectively immutable result.
         */
        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <EE> List<EE> copyOf(@Independent(hc = true) @NotNull(content = true) Collection<? extends EE> collection);

        static boolean get$Precondition$Size(int size, int index) {
            return index < size;
        }

        /*
         @NotModified by default
         */
        @NotNull
        E get(int index);

        default int of$Transfer$Size() {
            return 0;
        }

        /*
         Result has no hidden content (is deeply immutable).
         */
        @NotNull(content = true)
        @ImmutableContainer
        <EE> java.util.List<EE> of();

        default <F> int of$Transfer$Size(F e1) {
            return 1;
        }

        default <F> boolean of$Postcondition(F e1) {
            return contains(e1);
        }

        /*
         Static method; the hidden content comes from the parameters.
         */
        @NotNull(content = true)
        @ImmutableContainer(hc = true)
        <F> java.util.List<F> of(@NotNull F e1);

        default <F> int of$Transfer$Size(F f1, F f2) {
            return 2;
        }

        @NotNull(content = true)
        @ImmutableContainer(hc = true)
        <G> java.util.List<G> of(@NotNull G e1, @NotNull G e2);

        default <F> int of$Transfer$Size(F f1, F f2, F f3) {
            return 3;
        }

        @NotNull(content = true)
        @ImmutableContainer(hc = true)
        <H> java.util.List<H> of(@NotNull H e1, @NotNull H e2, @NotNull H e3);

        @Modified
        boolean remove(@NotNull @Independent Object object);

        /*
         There may be an index exception! TODO add precondition
         */
        @Modified
        @NotNull
        E remove(int index);

        @Modified
        boolean removeAll(@NotNull(content = true) @Independent Collection<?> c);

        @Modified
        @Independent(hc = true)
        E set(int index, @Independent(hc = true) E element);

        @Independent(hc = true)
        @NotNull(content = true)
        Iterator<E> spliterator();

        /*
         @Dependent!!
         */
        @NotNull(content = true)
        java.util.List<E> subList(int fromIndex, int toIndex);

        @NotNull(content = true)
        @Independent(hc = true)
        Object[] toArray();

        @NotNull(content = true)
        @Independent(hc = true)
        <T> T[] toArray(@Independent(hc = true) @NotNull(content = true) T[] a);
    }

    /*
     - FIXME we now have inheritance (2022 05 18), clean up!!! some are not consistent with Collection

      Not in line with JDK, but we block null values in the set.

      @Dependent because of the remove() method in Iterator returned by iterator()
     */
    @Container
    interface Set$<E> {

        // note that with the $, we're really in java.util.Set, so we have no knowledge of addModificationHelper unless we add it to the
        // type context IMPROVE not really trivial to sort out
        default boolean add$Modification$Size(int i, Integer j, E e) {
            return JavaUtil.setAddModificationHelper(i, j, contains(e));
        }

        default boolean add$Value$Size(int size, E e, boolean retVal) {
            return JavaUtil.setAddValueHelper(size, contains(e), retVal);
        }

        default boolean add$Remove(E e) {
            return !contains(e);
        }

        default boolean add$Postcondition(E e) {
            return contains(e);
        }

        // @Modified on method, @NotNull, @Independent(hc = true) on parameter inherited
        @Commutable(multi = "addAll")
        boolean add(E e);

        default boolean addAll$Clear$Size(int i, Collection<? extends E> c) {
            return i >= c.size();
        } // do NOT add isKnown()

        boolean addAll(Collection<? extends E> collection);

        default boolean clear$Clear$Size(int i) {
            return i == 0 && org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false);
        }

        @Modified
        void clear();

        default boolean contains$Value$Size(int i, Object o, boolean retVal) {
            return JavaUtil.setContainsValueHelper(i, contains(o), retVal);
        }

        // @NotModified on method, @NotNull, @Independent on parameter inherited
        boolean contains(Object object);

        /*
        factory method: independence in immutable container is with respect to the parameters.
         */
        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <EE> Set<EE> copyOf(@NotNull(content = true) @Independent(hc = true) Collection<? extends EE> collection);

        default boolean isEmpty$Value$Size(int i, boolean retVal) {
            return i == 0;
        }

        boolean isEmpty();

        default int of$Transfer$Size() {
            return 0;
        }

        @ImmutableContainer
        @NotNull(content = true)
        <EE> java.util.Set<EE> of();

        default <F> int of$Transfer$Size(F e1) {
            return 1;
        }

        default <F> boolean of$Postcondition(F e1) {
            return contains(e1);
        }

        /*
         factory method: independence in immutable container is with respect to the parameters.
         */
        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <F> java.util.Set<F> of(@NotNull F e1);

        // IMPROVE advanced <F> int of$Postcondition$Size(F f1, F f2, java.util.Set<F> retVal) { return isFact(f1.equals(f2)) ? (f1.equals(f2) ? 1: 2): retVal.size(); }
        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <G> java.util.Set<G> of(@Commutable @NotNull G e1, @Commutable @NotNull G e2);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5, @Commutable @NotNull H e6);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5, @Commutable @NotNull H e6, @Commutable @NotNull H e7);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5, @Commutable @NotNull H e6, @Commutable @NotNull H e7, @Commutable @NotNull H e8);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5, @Commutable @NotNull H e6, @Commutable @NotNull H e7, @Commutable @NotNull H e8, @Commutable @NotNull H e9);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@Commutable @NotNull H e1, @Commutable @NotNull H e2, @Commutable @NotNull H e3, @Commutable @NotNull H e4, @Commutable @NotNull H e5, @Commutable @NotNull H e6, @Commutable @NotNull H e7, @Commutable @NotNull H e8, @Commutable @NotNull H e9, @Commutable @NotNull H e10);

        @ImmutableContainer(hc = true)
        @NotNull(content = true)
        <H> java.util.Set<H> of(@NotNull(content = true) @Independent(hc = true) H... hs);

        default boolean remove$Modification$Size(int i, Integer j, Object o) {
            return JavaUtil.setRemoveModificationHelper(i, j, contains(o));
        }

        default boolean remove$Value$Size(int i, Object o, boolean retVal) {
            return JavaUtil.setContainsValueHelper(i, contains(o), retVal);
        }

        default boolean remove$Remove(Object object) {
            return contains(object);
        }

        default boolean remove$Postcondition(Object object) {
            return !contains(object);
        }

        @Modified
        boolean remove(@Independent @NotNull Object object);

        @Independent(hc = true)
        @NotNull(content = true)
        Iterator<E> spliterator();
    }

    @Container
    static class ArrayList$<E> {

        // tested in BCM_0, _1
        boolean ArrayList$Modification$Size(int post) {
            return post == 0;
        }

        ArrayList$() {
        }

        boolean ArrayList$Modification$Size(int post, int size) {
            return post == 0;
        }

        ArrayList$(int size) {
        }

        ArrayList$(@NotNull(content = true) @Independent(hc = true) Collection<? extends E> collection) {
        }
    }


    @Container
    static class LinkedList$<E> {

        boolean LinkedList$Modification$Size(int post) {
            return post == 0;
        }

        LinkedList$() {
        }

        boolean LinkedList$Modification$Size(int post, Collection<? extends E> c) {
            return post == c.size();
        }

        LinkedList$(@NotNull(content = true) @Independent(hc = true) Collection<? extends E> c) {
        }
    }

    @Container
    static class Stack$<E> {

        boolean Stack$Modification$Size(int post) {
            return post == 0;
        }

        Stack$() {
        }

        boolean Stack$Modification$Size(int post, Collection<? extends E> c) {
            return post == c.size();
        }

        Stack$(@NotNull(content = true) @Independent(hc = true) Collection<? extends E> c) {
        }

        @Modified
        E pop() {
            return null;
        }

        @Modified
        E push(@Independent(hc = true) @NotNull E item) {
            return null;
        }
    }

    @Container
    static class HashSet$<E> {

        // content is known
        boolean HashSet$Modification$Size(int post) {
            return post == 0;
        }

        boolean HashSet$Postcondition() {
            return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false);
        }

        HashSet$() {
        }

        // content is not known
        boolean HashSet$Modification$Size(int post, Collection<? extends E> c) {
            return post == c.size();
        }

        HashSet$(@NotNull(content = true) @Independent(hc = true) Collection<? extends E> c) {
        }
    }


    @UtilityClass
    @Container
    interface Objects$ {
        @NotNull
        @Identity
        <T> T requireNonNull(@NotNull T t);

        @NotNull
        @Identity
        <T> T requireNonNull(@NotNull T t, String message);

        @NotNull
        <T> T requireNonNullElse(T obj, T defaultObj);

        int hashCode(Object object);

        int hash(Object... values);

        boolean equals(Object left, Object right);
    }

    /*
     This goes against the API, but we want to raise problems when comparing with null
     */
    @ImmutableContainer
    interface Comparator$<T> {

        default int compare$Value(@NotNull T o1, @NotNull T o2, int retVal) {
            return o1.equals(o2) || o2.equals(o1) ? 0 : retVal;
        }

        /*
         The parameters are implicitly @Independent
         */
        int compare(@NotNull T o1, @NotNull T o2);

        <U> java.util.Comparator<U> comparingInt(@NotNull ToIntFunction<? super U> keyExtractor);

        @ImmutableContainer
        <T extends Comparable<? super T>> Comparator<T> naturalOrder();

        @ImmutableContainer
        <T extends Comparable<? super T>> Comparator<T> reverseOrder();
    }

    /*
     Analyser does not add hc=true automatically, Optional is not abstract
     */
    @ImmutableContainer(hc = true)
    interface Optional$<T> {
        /*
         no hidden content here
         */
        @NotNull
        @ImmutableContainer
        <T> java.util.Optional<T> empty();

        @NotModified
        void ifPresent(Consumer<? super T> action);

        /*
         factory method, link to parameter
         */
        @NotNull
        @Independent(hc = true)
        <T> java.util.Optional<T> of(@NotNull T t);

        /*
         factory method, link to parameter
         */
        @NotNull
        @Independent(hc = true)
        <T> java.util.Optional<T> ofNullable(T t);

        @NotNull
        @Independent(hc = true)
        T get();

        @NotNull
        @Independent(hc = true)
        T orElseThrow();

        @NotNull
        @Independent(hc = true)
        <X extends Throwable> T orElseThrow(@NotNull Supplier<? extends X> exceptionSupplier);

    }

    @UtilityClass
    interface Arrays$ {
        /*
        Note that the parameter 'array' is @Independent by default, because a utility class is @Immutable
         */
        @NotNull
        @Independent
        IntStream stream(@NotNull @NotModified int[] array);

        /*
        static method: hidden content transferred from the parameter
         */
        @NotNull
        @Independent(hc = true)
        <T> Stream<T> stream(@NotNull @NotModified T[] array);

        @NotNull
        @Independent(hc = true)
        <T> List<T> asList(T... ts);

        <T> void setAll(@NotNull T[] array, @NotNull @Independent(hcParameters = {0}) IntFunction<? extends T> generator);
    }

    @UtilityClass
    interface Collections$ {

        <T> boolean addAll(@NotNull @Modified @Independent(hcParameters = {1}) Collection<? super T> c, @NotModified T... elements);
    }

    /*
     dependent, because of entrySet, which has an iterator with remove()
     explicitly marked because of circular dependencies
     */
    @Container
    @Independent(absent = true)
    interface Map$<K, V> {

        default boolean clear$Clear$Size(int i) {
            return i == 0;
        }

        @Modified
        void clear();

        @NotNull
        @Modified
        V computeIfAbsent(@NotNull K key, @Independent(hc = true) @NotNull(content = true) Function<? super K, ? extends V> mappingFunction);

        default boolean containsKey$Value$Size(int i, Object key, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean containsKey(@NotNull @Independent Object key);

        @ImmutableContainer
        @NotNull
        <KK, VV> Map<KK, VV> of();

        /*
         factory method, @NotModified by default, independence in the immutable container is with respect to the
         parameter 'map'.
         */
        @ImmutableContainer(hc = true)
        @NotNull
        <KK, VV> Map<KK, VV> copyOf(@NotNull @Independent(hc = true) Map<? extends KK, ? extends VV> map);

        @ImmutableContainer(hc = true)
        @NotNull
        <KK, VV> Map<KK, VV> of(@NotNull KK k, @NotNull VV v);

        default boolean size$Invariant$Size(int i) {
            return i >= 0;
        }

        default void size$Aspect$Size() {
        }

        int size();

        default boolean isEmpty$Value$Size(int i, boolean retVal) {
            return i == 0;
        }

        boolean isEmpty();

        default int entrySet$Transfer$Size(int i) {
            return i;
        }

        /* @Dependent, as it is a view on the map: changes to the set are also changes to the map.
         Note also that Iterable<T> iterable() as a method of Set, Collection etc. is @Dependent on the collection
         (via the iterator.remove()). So map.entrySet().iterator().remove() is a possible chain of events that
         modifies the map!

         Also note that Entry is mutable, because of the setValue() method.
         It is not hidden in Map (only type parameters are in shallow analysis).
         So if write
            for(Map.Entry<K, V> e: map.entrySet()) {
                ...
            }
         then e is linked to map, in a @Dependent way!
         e.getKey() and e.getValue() are linked to map at the hidden content level.
         See e.g. Loops_18
         */
        @NotNull(content = true)
        Set<Map.Entry<K, V>> entrySet();

        default int keySet$Transfer$Size(int i) {
            return i;
        }

        /*
        @Dependent!
         */
        @NotNull(content = true)
        Set<K> keySet();

        @NotModified
        void forEach(@NotNull @Independent(hc = true) BiConsumer<? super K, ? super V> action);

        /*
         Parameter 'key' is @Independent because get is @NotModified.
         */
        @Independent(hc = true)
        V get(@NotNull Object key);

        /*
         Parameters 'key' and 'defaultValue' are @Independent because get is @NotModified.
         */
        @Independent(hc = true)
        V getOrDefault(@NotNull Object key, V defaultValue);

        @Modified
        @Independent(hc = true)
        V put(@NotNull @Independent(hc = true) K key, @NotNull @Independent(hc = true) V value);

        @Modified
        @Independent(hc = true)
        V merge(@NotNull @Independent(hc = true) K key, @NotNull @Independent(hc = true) V value,
                @Independent(hc = true) BiFunction<? super V, ? super V, ? extends V> remap);

        @Modified
        V remove(@NotNull @Independent Object key);

        /*
         @Dependent! changes to values() have an effect on the map
         */
        @NotNull(content = true)
        Collection<V> values();

        /*
         The analyser will compute this type as @Independent(hc=true), however, any method returning entries
         will be @Dependent because a call to setValue() will change the underlying map. However, see Entry firstEntry()
         in TreeMap.
         */
        @Container
        @Independent(hc = true)
        interface Entry<K, V> {
            @NotNull
            @Independent(hc = true)
            K getKey();

            @NotNull
            @Independent(hc = true)
            V getValue();

            @Modified
            @Independent(hc = true)
            V setValue(@Independent(hc = true) @NotNull V v);
        }
    }

    @Container
    interface AbstractMap$<K, V> {

        @Independent(hc = true)
        interface SimpleEntry<K, V> {

        }

        @ImmutableContainer
        interface SimpleImmutableEntry<K, V> {

        }
    }

    @Container
    static class HashMap$<K, V> {
        // content is known
        boolean HashMap$Modification$Size(int post) {
            return post == 0;
        }

        boolean HashMap$Postcondition() {
            return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false);
        }

        public HashMap$() {
        }

        // content is not known
        boolean HashMap$Modification$Size(int post, Map<? extends K, ? extends V> map) {
            return post == map.size();
        }

        public HashMap$(@NotNull(content = true) @Independent(hc = true) Map<? extends K, ? extends V> map) {
        }
    }


    @Container
    static class LinkedHashMap$<K, V> {
        // content is known
        boolean LinkedHashMap$Modification$Size(int post) {
            return post == 0;
        }

        boolean LinkedHashMap$Postcondition() {
            return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false);
        }

        public LinkedHashMap$() {
        }

        // content is not known
        boolean LinkedHashMap$Modification$Size(int post, Map<? extends K, ? extends V> map) {
            return post == map.size();
        }

        public LinkedHashMap$(@NotNull(content = true) @Independent(hc = true) Map<? extends K, ? extends V> map) {
        }
    }


    @Container
    static class TreeMap$<K, V> {
        // content is known
        boolean TreeMap$Modification$Size(int post) {
            return post == 0;
        }

        boolean TreeMap$Postcondition() {
            return org.e2immu.annotatedapi.AnnotatedAPI.isKnown(false);
        }

        public TreeMap$() {
        }

        // content is not known
        boolean TreeMap$Modification$Size(int post, Map<? extends K, ? extends V> map) {
            return post == map.size();
        }

        public TreeMap$(@NotNull(content = true) @Independent(hc = true) Map<? extends K, ? extends V> map) {
        }

        /*
         This particular Entry does not support modification!
         */
        // returns null when the map is empty TODO add correct companions
        @ImmutableContainer(hc = true)
        Map.Entry<K, V> firstEntry() {
            return null;
        }
    }

    interface AbstractCollection$<E> {

        Iterator<E> iterator();
    }

    @Container
    @Independent
    interface Random$ {

        @Modified
        int nextInt();
    }

    @Container
    interface SortedMap$ {

    }

    @Container
    interface NavigableMap$ {

    }

    @Container
    interface WeakHashMap$ {

    }

    @Container
    @Independent(hc = true)
    interface IntSummaryStatistics$ {
        void combine(@NotNull @Independent(hc = true) @NotModified IntSummaryStatistics other);
    }
}
