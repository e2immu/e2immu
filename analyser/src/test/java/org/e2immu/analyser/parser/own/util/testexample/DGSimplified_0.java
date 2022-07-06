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

import org.e2immu.annotation.*;
import org.e2immu.support.Freezable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

// comment out copyRemove, and things work out!
@E2Container(after = "frozen")
public class DGSimplified_0<T> extends Freezable {

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

    @Modified
    private final Map<T, Node<T>> nodeMap = new HashMap<>();

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
    }
/*
    // return all transitive dependencies
    @Independent
    public Set<T> dependencies(@NotNull T t) {
        Set<T> result = new HashSet<>();
        recursivelyComputeDependencies(t, result);
        return result;
    }

    @NotModified
    private void recursivelyComputeDependencies(@NotNull T t, @NotNull Set<T> result) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);

        result.add(t);

        if (node != null && node.dependsOn != null) {
            node.dependsOn.forEach(d -> {
                if (!result.contains(d)) {
                    recursivelyComputeDependencies(d, result);
                }
            });
        }
    }

    @Independent
    public Set<T> dependenciesWithoutStartingPoint(@NotNull T t) {
        Set<T> result = new HashSet<>();
        recursivelyComputeDependenciesWithoutStartingPoint(t, result);
        return result;
    }


    @NotModified
    private void recursivelyComputeDependenciesWithoutStartingPoint(@NotNull T t, @NotNull Set<T> result) {
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);

        if (node != null && node.dependsOn != null) {
            node.dependsOn.forEach(d -> {
                if (!result.contains(d)) {
                    result.add(d);
                    recursivelyComputeDependenciesWithoutStartingPoint(d, result);
                }
            });
        }
    }
*/
    public Set<T> removeAsManyAsPossible(Set<T> set) {
        AtomicBoolean changed = new AtomicBoolean(true);
        while (changed.get()) {
            changed.set(false);
            set.removeIf(t -> {
                Node<T> node = nodeMap.get(t);
                assert node != null;
                boolean remove = node.dependsOn == null || node.dependsOn.isEmpty() ||
                        node.dependsOn.stream().noneMatch(set::contains);
                if (remove) {
                    changed.set(true);
                }
                return remove;
            });
        }
        return set;
    }

    public List<T> getDependsOn(T t) {
        Node<T> node = nodeMap.get(t);
        assert node != null;
        return node.dependsOn;
    }

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node<T> getOrCreate(@NotNull T t) {
        ensureNotFrozen();
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    @NotModified(contract = true)
    public void visit(@NotNull BiConsumer<T, List<T>> consumer) {
        nodeMap.values().forEach(n -> consumer.accept(n.t, n.dependsOn));
    }
/*
    public void visitBidirectionalGroups(Consumer<List<T>> consumer) {
        Set<T> done = new HashSet<>();
        for (Map.Entry<T, Node<T>> e : nodeMap.entrySet()) {
            T t = e.getKey();
            if (!done.contains(t)) {
                Set<T> dependencies = dependencies(t);
                List<T> list = new ArrayList<>(dependencies.size() + 1);
                list.add(t);
                list.addAll(dependencies);
                done.addAll(list);
                consumer.accept(list);
            }
        }
    }
*/

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @NotModified T t, @NotNull Collection<T> dependsOn, boolean bidirectional) {
        ensureNotFrozen();
        Node<T> node = getOrCreate(t);
        for (T d : dependsOn) {
            if (node.dependsOn == null) node.dependsOn = new LinkedList<>();
            node.dependsOn.add(d);
            if (bidirectional) {
                Node<T> n = getOrCreate(d);
                if (n.dependsOn == null) n.dependsOn = new LinkedList<>();
                n.dependsOn.add(t);
            }
        }
    }

    @Independent
    public List<T> sorted() {
        return sorted(null, null, null);
    }

    Comparator<Map.Entry<T, Node<T>>> comparator(Comparator<T> backupComparator) {
        return (e1, e2) -> {
            int c = e1.getValue().dependsOn.size() - e2.getValue().dependsOn.size();
            if (c == 0) {
                c = backupComparator == null ? 0 : backupComparator.compare(e1.getKey(), e2.getKey());
            }
            return c;
        };
    }

    @Independent
    public List<T> sorted(Consumer<T> reportPartOfCycle,
                          Consumer<T> reportIndependent,
                          Comparator<T> backupComparator) {
        Map<T, Node<T>> toDo = new HashMap<>(nodeMap);
        Set<T> done = new HashSet<>();
        List<T> result = new ArrayList<>(nodeMap.size());
        while (!toDo.isEmpty()) {
            List<T> keys = new LinkedList<>();
            for (Map.Entry<T, Node<T>> entry : toDo.entrySet()) {
                List<T> dependencies = entry.getValue().dependsOn;
                if (dependencies == null || dependencies.isEmpty() || done.containsAll(dependencies)) {
                    keys.add(entry.getKey());
                    done.add(entry.getKey());
                    if (reportIndependent != null) reportIndependent.accept(entry.getKey());
                }
            }
            if (keys.isEmpty()) {
                // we have a cycle, break by taking one with a minimal number of dependencies
                Map.Entry<T, Node<T>> toRemove = toDo.entrySet().stream()
                        .min(comparator(backupComparator)).orElseThrow();
                T key = toRemove.getKey();
                toDo.remove(key);
                done.add(key);
                result.add(key);
                if (reportPartOfCycle != null) reportPartOfCycle.accept(key);
            } else {
                keys.forEach(toDo.keySet()::remove);
                result.addAll(keys);
            }
        }
        return result;
    }

    public DGSimplified_0<T> copyRemove(Predicate<T> accept) {
        DGSimplified_0<T> copy = new DGSimplified_0<>();
        nodeMap.forEach((t, node) -> {
            if (accept.test(t)) {
                List<T> newDependsOn = node.dependsOn == null ? null :
                        node.dependsOn.stream().filter(accept).toList();
                copy.nodeMap.put(t, new Node<>(t, newDependsOn));
            }
        });
        copy.freeze();
        return copy;
    }
}
    
