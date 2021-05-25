package org.e2immu.analyser.analyser.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DelayDebugNode implements Comparable<DelayDebugNode> {

    @Override
    public int compareTo(DelayDebugNode o) {
        return time - o.time;
    }

    public enum NodeType {
        CREATE, FOUND, TRANSLATE
    }

    public final int time;
    public final String where;
    public final NodeType nodeType;
    public final String label;
    private final List<DelayDebugNode> children = new ArrayList<>();

    public DelayDebugNode(int time, NodeType nodeType, String label, String where) {
        this.label = label;
        this.nodeType = nodeType;
        this.time = time;
        this.where = where;
    }

    public void addChild(DelayDebugNode create) {
        children.add(create);
    }

    public Stream<DelayDebugNode> children() {
        return children.stream();
    }

    public void sortRecursively() {
        Collections.sort(children);
        children.forEach(DelayDebugNode::sortRecursively);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DelayDebugNode that = (DelayDebugNode) o;
        return time == that.time;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time);
    }

    @Override
    public String toString() {
        return "DelayDebugNode{" +
                "time=" + time +
                ", where='" + where + '\'' +
                ", nodeType=" + nodeType +
                ", label='" + label + '\'' +
                ", children=" + children +
                '}';
    }
}
