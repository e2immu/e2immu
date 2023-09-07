package org.e2immu.analyser.resolver.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.Freezable;

import java.util.*;
import java.util.function.BiConsumer;

public class TypeParameter_1<T> extends Freezable {

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

    @NotModified(after = "frozen")
    private final Map<T, Node<T>> nodeMap = new HashMap<>();

    @NotModified
    public int size() {
        return nodeMap.size();
    }

    @NotModified
    public boolean isEmpty() {
        return nodeMap.isEmpty();
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

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @NotModified T t, @NotNull(content = true) Collection<T> dependsOn, boolean bidirectional) {
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

}
