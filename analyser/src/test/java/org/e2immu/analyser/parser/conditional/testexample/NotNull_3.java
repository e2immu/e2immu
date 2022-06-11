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

package org.e2immu.analyser.parser.conditional.testexample;

// copied from Trie, but some parts left out.
// main purpose is to test the not null in context of isStrictPrefix, variable "node"

import org.e2immu.annotation.Modified;

import java.util.*;

public class NotNull_3<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        Map<String, TrieNode<T>> map;
    }

    private TrieNode<T> goTo(String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null;
            node = node.map.get(strings[i]);
            // it is the presence of the following line that triggers the problem:
            if (node == null) return null;
        }
        return node;
    }

    public boolean isStrictPrefix(String[] prefix) {
        TrieNode<T> node = goTo(prefix, prefix.length);
        return node != null && node.data == null;
    }

    @Modified
    public TrieNode<T> add(String[] strings, T data) {
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) { // 2.0.1
                node.map = new HashMap<>();
                newTrieNode = new TrieNode<>();
                node.map.put(s, newTrieNode);
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
