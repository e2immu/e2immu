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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestTrie {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTrie.class);

    @Test
    public void test() {
        Trie<String> trie = new Trie<>();
        trie.add(new String[]{"org", "e2immu", "analyser", "util"}, "Trie");
        trie.add(new String[]{"org", "e2immu", "analyser", "util"}, "Either");
        trie.add(new String[]{"org", "e2immu", "analyser", "testexample"}, "EquivalentLoop");

        AtomicInteger counter = new AtomicInteger();
        trie.visit(new String[]{"org", "e2immu"}, (ss, list) -> {
            LOGGER.info("Visit 1: {} -> {}", Arrays.toString(ss), list);
            if (counter.getAndIncrement() == 0) {
                assertEquals("[analyser, util]", Arrays.toString(ss));
                assertEquals("[Trie, Either]", list.toString());
            } else if (counter.getAndIncrement() == 1) {
                assertEquals("[analyser, testexample]", Arrays.toString(ss));
                assertEquals("[EquivalentLoop]", list.toString());
            }
        });

        assertTrue(trie.isStrictPrefix(new String[]{"org"}));
        assertFalse(trie.isStrictPrefix(new String[]{"org", "junit"}));
        assertTrue(trie.isStrictPrefix(new String[]{"org", "e2immu"}));
        assertTrue(trie.isStrictPrefix(new String[]{"org", "e2immu", "analyser"}));
        assertFalse(trie.isStrictPrefix(new String[]{"org", "e2immu", "analyser", "util"}));

        assertNull(trie.get(new String[]{"com"}));
        assertNull(trie.get(new String[]{"org"}));
        assertEquals("[Trie, Either]", trie.get(new String[]{"org", "e2immu", "analyser", "util"}).toString());
    }
}
