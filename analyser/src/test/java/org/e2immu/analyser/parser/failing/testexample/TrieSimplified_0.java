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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

@E1Container
public class TrieSimplified_0<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    @NotNull
    public TrieNode<T> add(@NotNull String[] strings) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
                node.map = new HashMap<>();
            } else {
                newTrieNode = node.map.get(s);
                if (newTrieNode == null) {
                    newTrieNode = new TrieNode<>();
                    node.map.put(s, newTrieNode); // null ptr
                }
            }
        }
        return node;
    }


    @NotNull
    public TrieNode<T> addSynchronized(@NotNull String[] strings) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            synchronized (root) {
                if (node.map == null) {
                    node.map = new HashMap<>();
                } else {
                    newTrieNode = node.map.get(s);
                    if (newTrieNode == null) {
                        newTrieNode = new TrieNode<>();
                        node.map.put(s, newTrieNode); // NO null ptr
                    }
                }
            }
        }
        return node;
    }
}
