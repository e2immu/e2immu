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

import java.util.*;
import java.util.function.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class JavaUtil extends AnnotatedAPI {
    final static String PACKAGE_NAME = "java.util";

    // it is important that these helper methods are 'public', because the shallow
    // method analyser only considers public methods

    public static boolean setAddModificationHelper(int i, int j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j : i == j + 1) :
                isKnown(true) ? i == j + 1 : i >= j && i <= j + 1;
    }

    public static boolean setAddValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? !containsE : (isKnown(true) || size == 0 || retVal);
    }

    public static boolean setRemoveModificationHelper(int i, int j, boolean containsE) {
        return isFact(containsE) ? (containsE ? i == j - 1 : i == j) :
                isKnown(true) ? i == j : i >= j - 1 && i <= j;
    }

    public static boolean setContainsValueHelper(int size, boolean containsE, boolean retVal) {
        return isFact(containsE) ? containsE : !isKnown(true) && size > 0 && retVal;
    }

    /*
     Note: we can use T instead of E (in the byte-code), since we use distinguishingName instead of fullyQualifiedName.

     Currently, we cannot make any distinction between the @Modified in remove() which acts on the underlying collection,
     and the @Modified in hasNext()/next() which acts on the counting system of the iterator.
     The critical annotation is on the iterator() method in Collection, which is either @Dependent (to allow remove(),
     but it messes up normal iteration), or @Dependent1, which plays nice with iterating but not with removal.
     Because iterating without removal can, in many cases, be replaced by a for-each loop or a stream,
     we believe we can live with this at the moment.
     */
    @Container
    @Independent1
    interface Iterator$<T> {
        @Modified
        default void forEachRemaining(@NotNull @Independent1 Consumer<? super T> action) {
        }

        @Modified
        boolean hasNext();

        @Modified
        // implicitly @Independent1
        T next();

        @Modified
        void remove();
    }

    /*
     This is not in line with the JDK, but we will block null keys!
     Dependent because of remove() in iterator.
     */
    @Container // implicitly @Dependent, because one @Dependent method
    interface Collection$<E> {

        default boolean add$Postcondition(E e) {
            return contains(e);
        }

        @Modified
        boolean add(@NotNull E e);

        @Modified
        boolean addAll(@Independent1 @NotNull1 java.util.Collection<? extends E> collection);

        default boolean clear$Clear$Size(int i) {
            return i == 0;
        }

        @Modified
        void clear();

        default boolean contains$Value$Size(int i, Object o, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean contains(@NotNull @Independent1 Object object);

        default boolean containsAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean containsAll(@NotNull1 @Independent1 java.util.Collection<?> c);

        default boolean isEmpty$Value$Size(int i, boolean retVal) {
            return i == 0;
        }

        boolean isEmpty();

        @NotNull1
        // implicitly @Dependent
        java.util.Iterator<E> iterator();

        // there is a "default forEach" in Iterable, but here we can guarantee that consumer is @NotNull1 (its
        // arguments will not be null either)
        // @NotModified because the default for void methods in @Container is @Modified
        @NotModified
        void forEach(@Independent1 @NotNull1 Consumer<? super E> action);

        default boolean remove$Modification$Size(int i, int j) {
            return i <= j && i >= j - 1;
        }

        default boolean remove$Value$Size(int i, Object object, boolean retVal) {
            return i != 0 && retVal;
        }

        default boolean remove$Postcondition(Object object) {
            return !contains(object);
        }

        @Modified
        boolean remove(@NotNull @Independent Object object);

        default boolean removeAll$Modification$Size(int i, int j, java.util.Collection<?> c) {
            return i >= j - c.size() && i <= j;
        }

        default boolean removeAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) {
            return i != 0 && c.size() != 0 && retVal;
        }

        @Modified
        boolean removeAll(@NotNull1 @Independent java.util.Collection<?> c);

        @Modified
        boolean removeIf(Predicate<? super E> filter);

        default boolean retainAll$Modification$Size(int i, int j, java.util.Collection<?> c) {
            return i <= c.size() && i <= j;
        }

        default boolean retainAll$Value$Size(int i, java.util.Collection<?> c, boolean retVal) {
            return i != 0 && c.size() != 0 && retVal;
        }

        @Modified
        boolean retainAll(@NotNull1 @Independent java.util.Collection<?> c);

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
        @NotNull1
        @Independent1
        Stream<E> stream();

        default int toArray$Transfer$Size(int i) {
            return i;
        }

        @NotNull1
        @Independent1
        Object[] toArray();

        default <T> int toArray$Transfer$Size(int i, T[] a) {
            return i;
        }

        @NotNull1
        @Independent1
        <T> T[] toArray(@Independent1 @NotNull1 @Modified T[] a);

        default <T> int toArray$Transfer$Size(int i, IntFunction<T[]> g) {
            return i;
        }

        @NotNull1
        @Independent1
        <T> T[] toArray(@NotNull IntFunction<T[]> generator);
    }

    // this is not in line with the JDK, but we will block null keys!

    @Container
    interface List$<E> {

        default boolean add$Modification$Size(int i, int j, E e) {
            return i == j + 1;
        }

        // this is not in line with the JDK, but we will block null keys!
        default boolean add$Value(E e, boolean retVal) {
            return true;
        }

        default boolean add$Postcondition(E e) {
            return contains(e);
        }

        // @Modified inherited; we're not (yet) inheriting companion methods
        boolean add(E e); // @Independent1, @NotNull inherited

        default boolean addAll$Modification$Size(int i, int j, java.util.Collection<? extends E> c) {
            return i == j + c.size();
        }

        default boolean addAll$Value(java.util.Collection<? extends E> c, boolean retVal) {
            return true;
        }

        // IMPROVE causes problems with method resolution
        // boolean addAll$Postcondition(java.util.Collection<? extends E> c) { return c.stream().allMatch(this::contains); }
        //@Modified inherited
        boolean addAll(Collection<? extends E> collection); // @Independent1, @NotNull1 inherited

        // needed here because it is used by a companion of 'add'.
        default boolean contains$Value$Size(int i, Object o, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean contains(@NotNull Object object);

        @E2Container
        @NotNull1
        @Independent1
        <EE> List<EE> copyOf(@NotNull1 @Independent1 Collection<? extends EE> collection);

        static boolean get$Precondition$Size(int size, int index) {
            return index < size;
        }

        @NotNull
        // @Independent1 by default
        E get(int index);

        default int of$Transfer$Size() {
            return 0;
        }

        @NotNull1
        @ERContainer
        <EE> java.util.List<EE> of();

        default <F> int of$Transfer$Size(F e1) {
            return 1;
        }

        default <F> boolean of$Postcondition(F e1) {
            return contains(e1);
        }

        @NotNull1
        @E2Container
        @Independent1 // links to parameters, because of() is a static method
        <F> java.util.List<F> of(@NotNull F e1);

        default <F> int of$Transfer$Size(F f1, F f2) {
            return 2;
        }

        @NotNull1
        @E2Container
        @Independent1
        <G> java.util.List<G> of(@NotNull G e1, @NotNull G e2);

        default <F> int of$Transfer$Size(F f1, F f2, F f3) {
            return 3;
        }

        @NotNull1
        @E2Container
        @Independent1
        <H> java.util.List<H> of(@NotNull H e1, @NotNull H e2, @NotNull H e3);

        @Modified
        boolean remove(@NotNull @Independent Object object);

        @Modified
        @NotNull
            // but there may be an index exception! TODO add precondition
        E remove(int index);

        @Modified
        boolean removeAll(@NotNull1 @Independent Collection<?> c);

        @Modified
        E set(int index, E element);

        @Independent1
        Iterator<E> spliterator();

        // @Dependent implicitly!!!
        @NotNull1
        java.util.List<E> subList(int fromIndex, int toIndex);

        @NotNull1
        @Independent1
        Object[] toArray();

        @NotNull1
        @Independent1
        <T> T[] toArray(@Independent1 @NotNull1 T[] a);
    }

    /*
     - IMPROVE for now we have to repeat the method+companions from Collection, as companions are not inherited
     - Not in line with JDK, but we block null values in the set
     - @Dependent because of the remove() method in Iterator returned by iterator()
     */

    // @Dependent implicitly
    @Container
    interface Set$<E> {

        // note that with the $, we're really in java.util.Set, so we have no knowledge of addModificationHelper unless we add it to the
        // type context IMPROVE not really trivial to sort out
        default boolean add$Modification$Size(int i, int j, E e) {
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

        // @Modified on method, @NotNull, @Independent1 on parameter inherited
        boolean add(E e);

        default boolean addAll$Clear$Size(int i, Collection<? extends E> c) {
            return i >= c.size();
        } // do NOT add isKnown()

        boolean addAll(java.util.Collection<? extends E> collection);

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

        @E2Container
        @NotNull1
        @Independent1
        <EE> Set<EE> copyOf(@NotNull1 @Independent1 Collection<? extends EE> collection);

        default boolean isEmpty$Value$Size(int i, boolean retVal) {
            return i == 0;
        }

        boolean isEmpty();

        default int of$Transfer$Size() {
            return 0;
        }

        @NotNull1
        @E2Container
        <EE> java.util.Set<EE> of();

        default <F> int of$Transfer$Size(F e1) {
            return 1;
        }

        default <F> boolean of$Postcondition(F e1) {
            return contains(e1);
        }

        @NotNull1
        @E2Container
        @Independent1
        <F> java.util.Set<F> of(@NotNull F e1);

        // IMPROVE advanced <F> int of$Postcondition$Size(F f1, F f2, java.util.Set<F> retVal) { return isFact(f1.equals(f2)) ? (f1.equals(f2) ? 1: 2): retVal.size(); }
        @NotNull1
        @E2Container
        @Independent1
        <G> java.util.Set<G> of(@NotNull G e1, @NotNull G e2);

        @NotNull1
        @E2Container
        @Independent1
        <H> java.util.Set<H> of(@NotNull H e1, @NotNull H e2, @NotNull H e3);

        default boolean remove$Modification$Size(int i, int j, Object o) {
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

        boolean remove(Object object);

        @Independent1
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

        ArrayList$(@NotNull1 @Independent1 Collection<? extends E> collection) {
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

        LinkedList$(@NotNull1 @Independent1 Collection<? extends E> c) {
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

        Stack$(@NotNull1 @Independent1 Collection<? extends E> c) {
        }

        @Modified
        E pop() { return null; }

        @Modified
        E push(E item) { return null; }
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

        HashSet$(@NotNull1 @Independent1 Collection<? extends E> c) {
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

    // again this goes against the API, but we want to raise problems when comparing with null
    @ERContainer
    interface Comparator$<T> {

        default int compare$Value(@NotNull T o1, @NotNull T o2, int retVal) {
            return o1.equals(o2) || o2.equals(o1) ? 0 : retVal;
        }

        // @Independent by default (@NotModified method, non-abstract type)
        int compare(@NotNull T o1, @NotNull T o2);

        <U> java.util.Comparator<U> comparingInt(@NotNull ToIntFunction<? super U> keyExtractor);
    }

    @E2Container
    interface Optional$<T> {
        // @Independent because non-static factory method, conflict with @E2Container
        @NotNull
        @ERContainer
        <T> java.util.Optional<T> empty();

        @NotNull
        <T> java.util.Optional<T> of(@NotNull T t);

        @NotNull
        <T> java.util.Optional<T> ofNullable(T t);

        @NotNull
        T get();

        @NotNull
        T orElseThrow();

        @NotNull
        <X extends Throwable> T orElseThrow(@NotNull Supplier<? extends X> exceptionSupplier);

    }

    @UtilityClass
    interface Arrays$ {
        @NotNull
        IntStream stream(@NotNull @NotModified @Independent int[] array);

        @NotNull
        @Independent1
        <T> Stream<T> stream(@NotNull @NotModified @Independent1 T[] array);
    }

    @UtilityClass
    interface Collections$ {

        <T> boolean addAll(@NotNull @Modified @Independent1(parameters = {1}) Collection<? super T> c, @NotModified T... elements);
    }

    // dependent, because of entrySet, which has an iterator with remove()
    @Container
    interface Map$<K, V> {

        default boolean clear$Clear$Size(int i) {
            return i == 0;
        }

        @Modified
        void clear();

        @NotNull
        @Modified
        V computeIfAbsent(@NotNull K key, @Independent1 @NotNull1 Function<? super K, ? extends V> mappingFunction);

        default boolean containsKey$Value$Size(int i, Object key, boolean retVal) {
            return i != 0 && retVal;
        }

        boolean containsKey(@NotNull Object key);

        @E2Container
        @NotNull
        @NotModified
        @Independent1
        <KK, VV> Map<KK, VV> copyOf(@NotNull @Independent1 Map<? extends KK, ? extends VV> map);

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

        // @Dependent!
        @NotNull1
        Set<Map.Entry<K, V>> entrySet();

        default int keySet$Transfer$Size(int i) {
            return i;
        }

        @NotNull1
        Set<K> keySet();

        @NotModified
        void forEach(@NotNull @Independent1 BiConsumer<? super K, ? super V> action);

        V get(@NotNull Object key);

        V getOrDefault(@NotNull Object key, V defaultValue);

        @Modified
        V put(@NotNull K key, @NotNull V value);

        @Modified
        V merge(@NotNull K key, @NotNull V value, BiFunction<? super V, ? super V, ? extends V> remap);

        @Modified
        V remove(Object key);

        @NotNull1
        Collection<V> values();

        @Container
        @Independent1
        interface Entry<K, V> {
            @NotNull
            K getKey();

            @NotNull
            V getValue();

            @Modified
            V setValue(V v);
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

        public HashMap$(@NotNull1 @Independent1 Map<? extends K, ? extends V> map) {
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

        public TreeMap$(@NotNull1 @Independent1 Map<? extends K, ? extends V> map) {
        }

        // Entry does not support modification!
        // returns null when the map is empty TODO add correct companions
        @E2Container
        Map.Entry<K, V> firstEntry() { return null; }
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
    interface AbstractMap$ {

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
    interface LinkedHashMap$ {

    }
}
