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

import org.e2immu.annotation.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class DGSimplified_4<T> {

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

    private DGSimplified_4<T> reverse(Set<T> set) {
        DGSimplified_4<T> dg = new DGSimplified_4<>();
        for (T t : set) {
            dg.addNode(t, Set.of());
            Node<T> node = nodeMap.get(t);
            if (node.dependsOn != null) { // 1.0.2
                for (T d : node.dependsOn) {
                    if (set.contains(d)) { // 1.0.2.0.0.0.0
                        dg.addNode(d, Set.of(t)); // 1.0.2.0.0.0.0.0.0
                    }
                }
            }
        }
        return dg;
    }

    public List<T> sorted1() {
        return sorted(null, null, null);
    }

    public List<T> sorted(@Nullable Consumer<List<T>> reportPartOfCycle,
                          @Nullable Consumer<T> reportIndependent,
                          @Nullable Comparator<T> backupComparator) {
        Map<T, Node<T>> toDo = new HashMap<>(nodeMap);
        Set<T> done = new HashSet<>();
        List<T> result = new ArrayList<>(nodeMap.size());
        while (!toDo.isEmpty()) {
            List<T> keys = new LinkedList<>();
            for (Map.Entry<T, Node<T>> entry : toDo.entrySet()) {
                List<T> dependencies = entry.getValue().dependsOn;
                boolean safe;
                if (dependencies == null || dependencies.isEmpty()) {
                    safe = true;
                } else {
                    Set<T> copy = new HashSet<>(dependencies);
                    copy.removeAll(done);
                    copy.remove(entry.getKey());
                    safe = copy.isEmpty();
                }
                if (safe) {
                    keys.add(entry.getKey());
                    done.add(entry.getKey());
                    if (reportIndependent != null) reportIndependent.accept(entry.getKey());
                }
            }
            if (keys.isEmpty()) {
                // find the core of the cycle
                Map<T, Node<T>> cycle = new HashMap<>(toDo);
                removeAsManyAsPossible(cycle.keySet());
                assert !cycle.isEmpty();
                List<T> sortedCycle = cycle.entrySet().stream().sorted(comparator(backupComparator)).map(Map.Entry::getKey).toList();
                T key = sortedCycle.get(0);
                toDo.remove(key);
                done.add(key);
                result.add(key);
                if (reportPartOfCycle != null) reportPartOfCycle.accept(sortedCycle);
            } else {
                keys.forEach(toDo.keySet()::remove);
                result.addAll(keys);
            }
        }
        return result;
    }

    private static <T> Comparator<Map.Entry<T, Node<T>>> comparator(@Nullable Comparator<T> backupComparator) {
        return (e1, e2) -> {
            int c = e1.getValue().dependsOn.size() - e2.getValue().dependsOn.size();
            if (c == 0) {
                c = backupComparator == null ? 0 : backupComparator.compare(e1.getKey(), e2.getKey());
            }
            return c;
        };
    }

    private void removeAsManyAsPossible(Set<T> set) {
        boolean changed = true;
        while (changed) {
            changed = singleRemoveStep(set);
            DGSimplified_4<T> reverse = reverse(set);
            changed |= reverse.singleRemoveStep(set);
        }
    }

    private boolean singleRemoveStep(Set<T> set) {
        return set.removeIf(t -> {
            Node<T> node = nodeMap.get(t);
            assert node != null;
            return node.dependsOn == null || node.dependsOn.isEmpty() ||
                    node.dependsOn.stream().noneMatch(set::contains);
        });
    }
}
