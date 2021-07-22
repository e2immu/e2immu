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
