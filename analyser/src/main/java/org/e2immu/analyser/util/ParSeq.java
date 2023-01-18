package org.e2immu.analyser.util;

import org.e2immu.analyser.model.MethodInfo;

import java.util.*;
import java.util.function.Function;

/**
 * An interface describing a collection of parallel (unsorted) and sequential (sorted) sets of items.
 * It is here to describe the equivalence of different orders of variables in an operator expression,
 * or in a method call.
 * Examples:
 * <ul>
 *     <ol>Math.min(a,b) == Math.min(b, a)</ol>
 *     <ol>a+(b*c) == (b*c)+a == (c*b)+a == ...</ol>
 * </ul>
 * The two critical methods for constructing a ParSeq are <code>before</code>, indicating that one comes before the
 * other; and <code>atSameLevel</code>, indicating that both are exchangeable.
 * <p>
 * The ParSeq can be applied using the <code>sortParallels</code> methods; its elements can be obtained using
 * the <code>toList</code> method.
 *
 * @param <T>
 */
public interface ParSeq<T> {

    /**
     * Compute a new ParSeq indicating that the items of this ParSeq come before the ones of the other.
     * For example, in the division a/b, the ParSeq describing "a" comes before the one describing "b".
     *
     * @param other the other ParSeq
     * @return a ParSeq indicating that this ParSeq comes before the other
     */
    ParSeq<T> before(ParSeq<T> other);

    /**
     * Compute a new ParSeq indicating that the items of this ParSeq are in a parallel group with the other.
     * For example, in the sum a+b, the ParSeq describing "a" sits in a parallel group with the one describing "b".
     * <p>
     * The operator is used to make a distinction between different types of parallel groups: a+(b*c) results
     * in something like {a, {b, c}}, whereas a+b+c results in a parallel group of size 3, {a, b, c}.
     *
     * @param other    the other ParSeq
     * @param operator the operator
     * @return a new ParSeq indicating that this ParSeq appears in parallel with the other one
     */
    ParSeq<T> inParallelWith(ParSeq<T> other, MethodInfo operator);

    /**
     * Test if this ParSeq contains a given item
     *
     * @param t the item
     * @return true if the ParSeq contains this item
     */
    boolean contains(T t);

    /**
     * Test if this ParSeq contains the other ParSeq as a component.
     * The test is based on equality.
     *
     * @param other the other ParSeq
     * @return true if this ParSeq contains the other ParSeq as a component
     */
    boolean contains(ParSeq<T> other);

    ParSeq<T> intersection(ParSeq<T> other);

    /**
     * @return true when this is the empty ParSeq.
     */
    default boolean isEmpty() {
        return false;
    }

    /**
     * @return the number of (distinct) items  in this ParSeq.
     */
    int size();

    /**
     * Useful as a condition before calling <code>sortParallels</code>.
     *
     * @return false if the ParSeq is simply a sequence; true if it contains any parallel groups.
     */
    boolean containsParallels();

    /**
     * List all items in order of appearance, maintaining the order in which they currently
     * appear in any parallel group.
     *
     * @return the list of items in its current order, treating parallel groups as if they are sequential.
     */
    List<T> toList();

    /**
     * Sort a list of items, of a completely unrelated type, according to the ParSeq.
     * Inside parallel groups, use the comparator.
     *
     * @param items      the input
     * @param comparator the comparator for parallel groups
     * @param <X>        the type of the input
     * @return a new list of items, sorted accordingly.
     */
    <X> List<X> sortParallels(List<X> items, Comparator<X> comparator);

    /**
     * Apply the mapping function to each of the items, so they can be replaced by other items.
     *
     * @param function the mapping function; it should map each item to a uniquely different item; its result should not be null.
     * @param <X>      the new type
     * @return a new ParSeq, where all items have been replaced.
     */
    <X> ParSeq<X> map(Function<T, X> function);

    /**
     * Construct a ParSeq by applying the before and inParallelWith methods to the list according to this parSeq.
     *
     * @param list the size of the list should equal the result of the size() method on this object
     * @return a newly constructed ParSeq
     */
    ParSeq<T> apply(List<ParSeq<T>> list, MethodInfo operator);
}
