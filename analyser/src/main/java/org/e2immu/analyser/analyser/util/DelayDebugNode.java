package org.e2immu.analyser.analyser.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class DelayDebugNode implements Comparable<DelayDebugNode> {

    @Override
    public int compareTo(DelayDebugNode o) {
        return time - o.time;
    }

    public enum NodeType {
        CREATE, FOUND, TRANSLATE
    }

    private final List<DelayDebugNode> children = new ArrayList<>();

    public final int time;
    public final NodeType nodeType;
    public final String label;

    public DelayDebugNode(int time, NodeType nodeType, String label) {
        this.label = label;
        this.nodeType = nodeType;
        this.time = time;
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
}
