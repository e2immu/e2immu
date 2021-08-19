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

package org.e2immu.analyser.util;

import org.e2immu.annotation.*;
import org.e2immu.support.Freezable;

import java.util.*;
import java.util.function.BiConsumer;

@E2Container(after = "frozen")
public class Trie<T> extends Freezable {

    @Modified
    private final TrieNode<T> root = new TrieNode<>();

    @Container // by definition, has no methods
    private static class TrieNode<T> {
        @Nullable
        @Modified
        @Variable
        List<T> data;
        @Nullable
        @Modified
        @Variable
        Map<String, TrieNode<T>> map;
    }

    @Nullable
    @NotModified
    private TrieNode<T> goTo(String[] strings) {
        return goTo(strings, strings.length);
    }

    @Nullable
    @NotModified
    private TrieNode<T> goTo(@NotModified String[] strings, int upToPosition) {
        TrieNode<T> node = root;
        for (int i = 0; i < upToPosition; i++) {
            if (node.map == null) return null;
            node = node.map.get(strings[i]);
            if (node == null) return null;
        }
        return node;
    }

    @NotModified
    public boolean isStrictPrefix(String[] prefix) {
        TrieNode<T> node = goTo(prefix);
        return node != null && node.data == null;
    }

    @NotModified
    @Nullable
    public List<T> get(@NotNull String[] strings) {
        TrieNode<T> node = goTo(strings);
        return node == null ? null : node.data;
    }

    @NotModified
    @Nullable
    public List<T> get(String[] strings, int upToPosition) {
        TrieNode<T> node = goTo(strings, upToPosition);
        return node == null ? null : node.data == null ? List.of() : node.data;
    }

    @NotModified
    public void visitLeaves(@NotModified String[] strings, @Container BiConsumer<String[], List<T>> visitor) {
        TrieNode<T> node = goTo(strings);
        if (node == null) return;
        if (node.map != null) {
            node.map.forEach((s, n) -> {
                if (n.map == null) { // leaf
                    visitor.accept(new String[]{s}, n.data);
                }
            });
        }
    }

    @NotModified
    public void visit(String[] strings,
                      @Container BiConsumer<String[], List<T>> visitor) {
        TrieNode<T> node = goTo(strings);
        if (node == null) return;
        recursivelyVisit(node, new Stack<>(), visitor);
    }

    @NotModified // pushed via contract on visit
    private static <T> void recursivelyVisit(@NotModified TrieNode<T> node,
                                             @Modified Stack<String> strings,
                                             @Container BiConsumer<String[], List<T>> visitor) {
        if (node.data != null) {
            visitor.accept(strings.toArray(String[]::new), node.data);
        }
        if (node.map != null) {
            node.map.forEach((s, n) -> {
                strings.push(s);
                recursivelyVisit(n, strings, visitor);
                strings.pop();
            });
        }
    }

    @NotNull
    @Only(before = "frozen")
    public TrieNode<T> add(@NotNull String[] strings,
                           @NotNull T data) {
        ensureNotFrozen();
        TrieNode<T> node = root;
        for (String s : strings) {
            TrieNode<T> newTrieNode;
            if (node.map == null) {
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
