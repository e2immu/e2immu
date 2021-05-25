package org.e2immu.analyser.analyser.util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class DelayDebugCollector implements DelayDebugger {

    private static final AtomicInteger timeGenerator = new AtomicInteger();

    public static int currentTime() {
        return timeGenerator.get();
    }

    private final List<DelayDebugNode> nodes = new LinkedList<>();

    @Override
    public boolean foundDelay(String where, String delayFqn) {
        DelayDebugNode found = new DelayDebugNode(timeGenerator.incrementAndGet(),
                DelayDebugNode.NodeType.FOUND, delayFqn, where);
        nodes.add(found);
        return true;
    }

    @Override
    public boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
        DelayDebugNode found = new DelayDebugNode(timeGenerator.incrementAndGet(),
                DelayDebugNode.NodeType.TRANSLATE, delayFromFqn, where);
        DelayDebugNode create = new DelayDebugNode(timeGenerator.incrementAndGet(),
                DelayDebugNode.NodeType.CREATE, newDelayFqn, where);
        found.addChild(create);
        nodes.add(found);
        return true;
    }

    @Override
    public boolean createDelay(String where, String delayFqn) {
        DelayDebugNode create = new DelayDebugNode(timeGenerator.incrementAndGet(),
                DelayDebugNode.NodeType.CREATE, delayFqn, where);
        nodes.add(create);
        return true;
    }

    @Override
    public Stream<DelayDebugNode> streamNodes() {
        return nodes.stream();
    }
}
