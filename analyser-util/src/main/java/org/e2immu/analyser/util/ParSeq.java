package org.e2immu.analyser.util;

import java.util.Comparator;
import java.util.List;

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
     * Useful as a condition before calling <code>sortParallels</code>.
     *
     * @return false if the ParSeq is simply a sequence; true if it contains any parallel groups.
     */
    boolean containsParallels();

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

}
