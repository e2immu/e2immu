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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DGSimplified_3<T> {

    private static class Node<S> {
        List<S> dependsOn;
        final S t;

        private Node(S t, List<S> dependsOn) {
            this.t = t;
            this.dependsOn = dependsOn;
        }
    }

    private final Map<T, Node<T>> nodeMap = new HashMap<>();

    public Map<T, Node<T>> getNodeMap() {
        return nodeMap;
    }

    // completely stripped down to got to the core of the delay loop
    public void reverse(Set<T> set) {
        for (T t : set) {
            Node<T> node = nodeMap.get(t);
            if (node.dependsOn != null) {
                for (T d : node.dependsOn) {
                    if (set.contains(d)) {

                    }
                }
            }
        }
    }
}
