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

package org.e2immu.analyser.resolver.impl;

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.util.DependencyGraph;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnalyseCycle {

    /*
    If methodInfo is in methodsReached, we have a call cycle.
    This method returns true when removing me solves the cycle.
    In strict order!
     */
    public static boolean analyseCycle(MethodInfo methodInfo,
                                       Set<MethodInfo> methodsReached,
                                       DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        if (!methodsReached.contains(methodInfo) || methodsReached.size() == 1) return false;
        List<MethodInfo> sorted = new ArrayList<>(methodsReached);
        sorted.sort(Comparator.comparing(m -> m.fullyQualifiedName));
        Set<MethodInfo> neededToBreakCycle = computeNeededToBreakCycle(sorted, methodGraph);
        return neededToBreakCycle.contains(methodInfo);
    }

    private static Set<MethodInfo> computeNeededToBreakCycle(List<MethodInfo> sorted, DependencyGraph<WithInspectionAndAnalysis> methodGraph) {
        Set<MethodInfo> toRemove = new HashSet<>();
        for (MethodInfo remove : sorted) {
            toRemove.add(remove);
            if (toRemove.size() == sorted.size() - 1) break; // what remains are recursions
            // we'll try to remove me. If the rest has no cycles, then stop. Otherwise, continue.
            DependencyGraph<WithInspectionAndAnalysis> newGraph = methodGraph.copyRemove(w -> w instanceof MethodInfo && w != remove);
            AtomicBoolean haveCycle = new AtomicBoolean();
            newGraph.sorted(w -> haveCycle.set(true), null, null);
            if (!haveCycle.get()) break;
        }
        return toRemove;
    }

}
