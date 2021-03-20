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
