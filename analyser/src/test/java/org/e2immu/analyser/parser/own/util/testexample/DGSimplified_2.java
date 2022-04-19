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

import java.util.*;

public class DGSimplified_2<T> {


    private static class Node<T> {
        List<T> dependsOn;
        final T t;

        private Node(T t) {
            this.t = t;
        }

        private Node(T t, List<T> dependsOn) {
            this.t = t;
            this.dependsOn = dependsOn;
        }
    }

    private final Map<T, Node<T>> nodeMap = new HashMap<>();


    private Node<T> getOrCreate(T t) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    public void addNode(T t, Collection<T> dependsOn) {
        addNode(t, dependsOn, false);
    }

    public void addNode(T t, Collection<T> dependsOn, boolean bidirectional) {
        Node<T> node = getOrCreate(t);
        for (T d : dependsOn) {
            if (node.dependsOn == null) node.dependsOn = new LinkedList<>();
            node.dependsOn.add(d);
            Node<T> n = getOrCreate(d);
            if (bidirectional) {
                if (n.dependsOn == null) n.dependsOn = new LinkedList<>();
                n.dependsOn.add(t);
            }
        }
    }

    // nonsensical version, which cause(s/d) an issue
    // Already have final value '<no return value>'; trying to write delayed value '<m:addNode>'
    public DGSimplified_2<T> reverse(Set<T> set) {
        DGSimplified_2<T> dg = new DGSimplified_2<>();
        for (T t : set) {
            dg.addNode(t, Set.of());
        }
        return dg;
    }
}
