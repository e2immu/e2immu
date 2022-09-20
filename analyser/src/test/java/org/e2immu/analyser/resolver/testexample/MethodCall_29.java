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

package org.e2immu.analyser.resolver.testexample;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

// a variant in Import fails because of a static import, see Import_11
public class MethodCall_29 {
    interface Variable {
    }

    interface DV {
    }

    private static class Node {
        Map<Variable, DV> dependsOn;
        final Variable variable;

        private Node(Variable v) {
            variable = v;
        }
    }

    private final Map<Variable, Node> nodeMap = new TreeMap<>();

    public void visit(BiConsumer<Variable, Map<Variable, DV>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.variable, n.dependsOn));
    }

}

