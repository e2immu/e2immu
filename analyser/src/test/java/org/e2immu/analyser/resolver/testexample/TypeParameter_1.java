package org.e2immu.analyser.resolver.testexample;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.Freezable;

import java.util.*;

/*
type parameter has a bit of a weird name, to facilitate debugging.
T as a name is too common, and processing of byte code will also go through ParameterizedTypeFactory.
 */
public class TypeParameter_1<TP0> extends Freezable {

    private static class Node<TP0> {
        List<TP0> dependsOn;
        final TP0 t;

        private Node(TP0 t) {
            this.t = t;
        }

        private Node(TP0 t, List<TP0> dependsOn) {
            this.t = t;
            this.dependsOn = dependsOn;
        }
    }

    @NotModified(after = "frozen")
    private final Map<TP0, Node<TP0>> nodeMap = new HashMap<>();

    @NotNull
    @Modified
    @Only(before = "frozen")
    private Node<TP0> getOrCreate(@NotNull TP0 t) {
        ensureNotFrozen();
        Objects.requireNonNull(t);
        Node<TP0> node = nodeMap.get(t);
        if (node == null) {
            node = new Node<>(t);
            nodeMap.put(t, node);
        }
        return node;
    }

    @Only(before = "frozen")
    @Modified
    public void addNode(@NotNull @NotModified TP0 t, @NotNull(content = true) Collection<TP0> dependsOn, boolean bidirectional) {
        for (TP0 d : dependsOn) {
            if (bidirectional) {
                Node<TP0> n = getOrCreate(d);
                if (n.dependsOn == null) n.dependsOn = new LinkedList<>();
                n.dependsOn.add(t);
            }
        }
    }
}
