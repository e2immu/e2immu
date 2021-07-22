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
        CREATE, FOUND, TRANSLATE;
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

    public boolean hasChildren() {
        return !children.isEmpty();
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
