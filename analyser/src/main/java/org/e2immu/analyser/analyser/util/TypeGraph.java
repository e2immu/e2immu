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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.model.TypeInfo;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;


public class TypeGraph {

    public static class Dependencies {
        private final Map<TypeInfo, Integer> weights;
        private int sumIncoming;

        public Dependencies(Map<TypeInfo, Integer> weights) {
            this.weights = weights;
        }

        public Map<TypeInfo, Integer> getWeights() {
            return weights;
        }

        public int getSumIncoming() {
            return sumIncoming;
        }
    }

    private final Map<TypeInfo, Dependencies> nodeMap = new TreeMap<>();

    public void addNode(TypeInfo v, Map<TypeInfo, Integer> dependsOn) {
        nodeMap.put(v, new Dependencies(dependsOn));
    }

    public Map<TypeInfo, Dependencies> getNodeMap() {
        return nodeMap;
    }

    public List<TypeInfo> sorted(Consumer<List<TypeInfo>> cycleConsumer,
                                 Consumer<TypeInfo> independentConsumer,
                                 Comparator<TypeInfo> comparing,
                                 boolean breakCycle,
                                 boolean parallel) {
        for (Dependencies dependencies : nodeMap.values()) {
            for (Map.Entry<TypeInfo, Integer> dep : dependencies.weights.entrySet()) {
                Dependencies d = nodeMap.get(dep.getKey());
                if (d != null) {
                    d.sumIncoming += dep.getValue();
                }
            }
        }
        // LinkedHashMap so that we can maintain order
        Map<TypeInfo, Dependencies> toDo = new LinkedHashMap<>(nodeMap.size());
        nodeMap.entrySet().stream().sorted(Comparator.comparing(e -> -e.getValue().sumIncoming)).forEach(
                e -> toDo.put(e.getKey(), e.getValue()));
        List<TypeInfo> result = new ArrayList<>(nodeMap.size());
        while (!toDo.isEmpty()) {
            Set<Map.Entry<TypeInfo, Dependencies>> entries = toDo.entrySet();
            Stream<Map.Entry<TypeInfo, Dependencies>> entryStream = parallel ? entries.parallelStream()
                    : entries.stream();
            List<TypeInfo> doneInThisIteration = entryStream.filter(entry -> {
                Dependencies dependencies = entry.getValue();
                boolean safe;
                if (dependencies == null || dependencies.weights.isEmpty()) {
                    safe = true;
                } else {
                    Map<TypeInfo, Integer> copy = new HashMap<>(dependencies.weights);
                    result.forEach(copy.keySet()::remove);
                    copy.remove(entry.getKey());
                    safe = copy.isEmpty();
                }
                if (safe && independentConsumer != null) independentConsumer.accept(entry.getKey());
                return safe;
            }).map(Map.Entry::getKey).toList();
            // there are no types without dependencies at the moment -- we must have a cycle
            if (doneInThisIteration.isEmpty()) {
                assert toDo.size() > 1 : "The last one should always be safe";
                int first = entries.stream().findFirst().orElseThrow().getValue().sumIncoming;
                List<TypeInfo> sortedCycle = entries.stream()
                        .takeWhile(e -> e.getValue().sumIncoming == first || !breakCycle)
                        .map(Map.Entry::getKey)
                        .sorted(comparing).toList();
                if (cycleConsumer != null) cycleConsumer.accept(sortedCycle);
                sortedCycle.forEach(toDo.keySet()::remove);
                result.addAll(sortedCycle);
            } else {
                doneInThisIteration.forEach(toDo.keySet()::remove);
                result.addAll(doneInThisIteration);
            }
        }
        return result;
    }
}
