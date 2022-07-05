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

package org.e2immu.analyser.parser.own.util.testexample;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class WGSimplified_0<T extends Comparable<? super T>, W extends WGSimplified_0.Weight> {

    public interface WeightType<W extends Weight> {
        W neutral();
    }

    public interface Weight extends Comparable<Weight> {

    }

    private static class Node<T, W extends Weight> {
        Map<T, W> dependsOn;
        final T t;

        private Node(T t) {
            this.t = t;
        }
    }

    private final W neutral;

    public WGSimplified_0(WeightType<W> weightType) {
        neutral = weightType.neutral();
    }

    private final Map<T, Node<T, W>> nodeMap = new TreeMap<>();

    private void recursivelyComputeLinks(T t, Map<T, W> distanceToStartingPoint) {
        Objects.requireNonNull(t);
        Node<T, W> node = nodeMap.get(t);
        W currentDistanceToT = distanceToStartingPoint.get(t);

        // do I have outgoing arrows?
        if (node != null && node.dependsOn != null) {
            // yes, opportunity (1) to improve distance computations, (2) to visit them
            node.dependsOn.forEach((n, d) -> {
                W distanceToN = currentDistanceToT.compareTo(neutral) < 0
                        ? min(d, currentDistanceToT) : max(currentDistanceToT, d);
            });
        }
    }

    private W min(W w1, W w2) {
        return w1.compareTo(w2) <= 0 ? w1 : w2;
    }

    private W max(W w1, W w2) {
        return w1.compareTo(w2) <= 0 ? w2 : w1;
    }

}
