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

// tests because obtaining an inspection for java.util.List fails.
// must keep the java.util.* import; it does work with explicit imports.
// also works when with Annotated APIs, only fails without 20220611

import org.e2immu.annotation.ImmutableContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotNull_4_1<T> {

    private final TrieNode<T> root = new TrieNode<>();

    private static class TrieNode<T> {
        List<T> data;
        final Map<String, TrieNode<T>> map = new HashMap<>();
    }

    private TrieNode<T> goTo(String[] strings, int upToPosition) {
        return strings.length <= upToPosition ? null : root;
    }

    public boolean isStrictPrefix(String[] prefix) {
        TrieNode<T> node = goTo(prefix, prefix.length);
        return node != null && node.data == null;
    }

    @ImmutableContainer("null")
    public List<T> get(String[] strings) {
        TrieNode<T> node = goTo(strings, strings.length);
        return node == null ? null : node.data;
    }

}
