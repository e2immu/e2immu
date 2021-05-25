package org.e2immu.analyser.analyser.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class DelayDebugProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DelayDebugProcessor.class);

    private final List<DelayDebugNode> nodes;
    private final Map<String, DelayDebugNode> byLabel = new LinkedHashMap<>();

    private final Set<String> creationMissing = new HashSet<>();
    private final Set<String> creationDuplicates = new HashSet<>();

    public DelayDebugProcessor(List<DelayDebugNode> nodes) {
        this.nodes = nodes;
    }

    public void process() {
        for (DelayDebugNode node : nodes) {
            addNode(node);
        }
        printWarnings();
        printTree();
    }

    private void printWarnings() {
        creationMissing.forEach(label -> LOGGER.warn("Creation missing: {}", clean(label)));
        creationDuplicates.forEach(label -> LOGGER.warn("Creation duplicated: {}", clean(label)));
    }

    private void printTree() {
        log(DELAYED, "*********** start delay tree ************");
        for (DelayDebugNode node : byLabel.values()) {
            if (node.nodeType != DelayDebugNode.NodeType.CREATE || node.hasChildren()) {
                printTree(0, node);
            }
        }
        log(DELAYED, "*********** end   delay tree ************");
    }

    private void printTree(int indent, DelayDebugNode node) {
        String indentation = " ".repeat(indent);
        log(DELAYED, "{}{} {} {} {} ", indentation, node.time, node.nodeType, clean(node.label),
                clean(node.where));
        node.children().forEach(child -> printTree(indent + 2, child));
    }

    private static String clean(String in) {
        return in.replaceAll("org.e2immu.analyser.testexample.", "");
    }

    private void addNode(DelayDebugNode node) {
        log(DELAYED, "Node {} {} {} {} {}", node.time, node.nodeType, clean(node.label),
                clean(node.where), clean(node.children().map(c -> "-->" + c.time + " " + c.label).findFirst().orElse("")));

        DelayDebugNode inMap = byLabel.getOrDefault(node.label, null);
        if (node.nodeType == DelayDebugNode.NodeType.CREATE) {
            if (inMap != null) {
                creationDuplicates.add(node.label);
            } else {
                byLabel.put(node.label, node);
            }
        } else {
            DelayDebugNode parent;
            if (inMap == null) {
                creationMissing.add(node.label);
                parent = new DelayDebugNode(-1, DelayDebugNode.NodeType.CREATE, node.label, "???");
                byLabel.put(parent.label, parent);
            } else {
                parent = inMap;
            }
            parent.addChild(node);
            if (node.nodeType == DelayDebugNode.NodeType.TRANSLATE) {
                node.children().forEach(this::addNode);
            }
        }
    }
}
