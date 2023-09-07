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

import org.e2immu.annotation.NotNull;

import java.util.*;

public class TrieSimplified_5<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<S> {
        List<S> data;
        Map<String, TrieNode<S>> map;
    }

    @NotNull
    public TrieNode<T> add(@NotNull(content = true) String[] strings, T data) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
                node.map = new HashMap<>();
                newTrieNode = new TrieNode<>();
                node.map.put(s, newTrieNode); // 1.0.1.0.2
            } else {
                newTrieNode = node.map.get(s);
                if (newTrieNode == null) {
                    newTrieNode = new TrieNode<>();
                    node.map.put(s, newTrieNode);
                }
            }
            node = newTrieNode;
        }
        if (node.data == null) node.data = new LinkedList<>();
        node.data.add(Objects.requireNonNull(data));
        return node;
    }
}
