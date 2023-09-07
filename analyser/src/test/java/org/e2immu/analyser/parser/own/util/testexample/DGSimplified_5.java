package org.e2immu.analyser.parser.own.util.testexample;

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.Freezable;

import java.util.*;

public class DGSimplified_5<T> extends Freezable {

    private static class Node<S> {
        List<S> dependsOn;
        final S t;

        private Node(S t) {
            this.t = t;
        }
    }

    @NotModified(after = "frozen")
    private final Map<T, Node<T>> nodeMap = new HashMap<>();

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node<T> getOrCreate(@NotNull @Independent(hc = true) T t) {
        ensureNotFrozen();
        Objects.requireNonNull(t);
        Node<T> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    public record SortResult<T>(T t, int group) {
        @Override
        public String toString() {
            return t + ":" + group;
        }
    }

    /*
     This method throws an exception when the graph is cyclic.
     Return an element sort, the fewest dependencies first, with additional information about the dependencies
     between the elements.
     */
    public List<SortResult<T>> sortedSequenceOfParallel() {
        Map<T, Node<T>> toDo = new HashMap<>(nodeMap);
        Set<T> done = new HashSet<>();
        List<SortResult<T>> result = new ArrayList<>(nodeMap.size());
        int iteration = 0;

        while (!toDo.isEmpty()) {
            List<T> parallel = new LinkedList<>();
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
                    parallel.add(entry.getKey());
                }
            }
            if (parallel.isEmpty()) {
                throw new UnsupportedOperationException("Found a cycle; don't use this method");
            } else {
                for (T t : parallel) result.add(new SortResult<>(t, iteration));
                parallel.forEach(toDo.keySet()::remove);
                done.addAll(parallel);
            }
            iteration++;
        }
        return result;
    }
}
