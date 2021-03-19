/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
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
