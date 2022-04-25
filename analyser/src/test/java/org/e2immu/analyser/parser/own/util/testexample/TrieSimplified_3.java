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

package org.e2immu.analyser.parser.own.util.testexample;

import org.e2immu.annotation.ERContainer;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.Map;

@ERContainer
public class TrieSimplified_3<T> {
    private final TrieNode<T> root = new TrieNode<>();

    @ERContainer
    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    private TrieNode<T> goTo(String[] strings) {
        return goTo(strings, strings.length);
    }

    @NotNull // because the null return is not reachable
    @NotModified
    private TrieNode<T> goTo(String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null; // eval to true, always return null
            node = node.map.get(strings[i]); // statement should be unreachable from it 1
            if (node == null) return null; // unreachable from it 1
        }
        return node;
    }
}
