/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Trie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTypeMapImpl {

    @Test
    public void testFromTrie() {

        Trie<TypeInfo> trie = new Trie<>();

        String[] orgE2ImmuParser = {"org", "e2immu", "Parser"};
        TypeInfo parser = new TypeInfo("org.e2immu", "Parser");
        trie.add(orgE2ImmuParser, parser);

        assertSame(parser, TypeMapImpl.fromTrie(trie, orgE2ImmuParser));

        String[] orgE2ImmuParserSub = {"org", "e2immu", "Parser", "Sub"};

        TypeInfo parserSub = TypeMapImpl.fromTrie(trie, orgE2ImmuParserSub);
        assertNotNull(parserSub);
        assertEquals("Sub", parserSub.simpleName);
        assertSame(parser, parserSub.packageNameOrEnclosingType.getRight());

        String[] orgE2ImmuParserSubSup = {"org", "e2immu", "Parser", "Sub", "Sup"};

        TypeInfo parserSubSup = TypeMapImpl.fromTrie(trie, orgE2ImmuParserSubSup);
        assertNotNull(parserSubSup);
        assertEquals("Sup", parserSubSup.simpleName);
        assertNotSame(parserSub, parserSubSup.packageNameOrEnclosingType.getRight()); // not adding to trie
        assertEquals(parserSub, parserSubSup.packageNameOrEnclosingType.getRight());

        String[] orgE3ImmuParser = {"org", "e3immu", "Parser"};
        assertNull(TypeMapImpl.fromTrie(trie, orgE3ImmuParser));
    }
}
