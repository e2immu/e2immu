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

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.Map;

/*
Variant on _1; without getter and setter

map is never assigned, found to be null, which kills branch 0.1 in add
 */
@ImmutableContainer(hc = true)
public class TrieSimplified_1_2<T> {

    @NotModified
    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        Map<String, TrieNode<T>> map;
    }

    @NotNull
    @NotModified
    public TrieNode<T> add(@NotNull String s) {
        if (root.map == null) {
            //
        } else {
            TrieNode<T> newTrieNode = root.map.get(s);
            if (newTrieNode == null) {
                newTrieNode = new TrieNode<>();
                root.map.put(s, newTrieNode);
            }
        }
        return root;
    }

    @NotNull
    @NotModified
    public synchronized TrieNode<T> addSynchronized(@NotNull String s) {
        if (root.map == null) {
            //
        } else {
            TrieNode<T> newTrieNode = root.map.get(s);
            if (newTrieNode == null) {
                newTrieNode = new TrieNode<>();
                root.map.put(s, newTrieNode);
            }
        }
        return root;
    }
}
