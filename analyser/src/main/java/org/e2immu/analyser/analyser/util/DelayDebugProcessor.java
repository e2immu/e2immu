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
        Set<DelayDebugNode> visited = new HashSet<>();
        for (DelayDebugNode node : byLabel.values()) {
            if (node.nodeType != DelayDebugNode.NodeType.CREATE || node.hasChildren()) {
                printTree(0, node, visited);
            }
        }
        log(DELAYED, "*********** end   delay tree ************");
    }

    private void printTree(int indent, DelayDebugNode node, Set<DelayDebugNode> visited) {
        if (visited.contains(node)) return;
        visited.add(node);
        String indentation = " ".repeat(indent);
        log(DELAYED, "{}{} {} {} {} ", indentation, node.time, node.nodeType, clean(node.label),
                clean(node.where));
        node.children().forEach(child -> printTree(indent + 2, child, visited));
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
