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

import static org.e2immu.analyser.util.StringUtil.inScopeOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestStringUtil {

    @Test
    public void testInScopeOf() {
        assertTrue(inScopeOf("3.0.0", "3.0.0.0.0"));
        assertTrue(inScopeOf("3.0.0", "3.0.1"));
        assertTrue(inScopeOf("3.0.0", "3.0.1.1.0"));
        assertFalse(inScopeOf("3.0.0", "3.1.0"));
        assertFalse(inScopeOf("3.0.0", "2"));
        assertFalse(inScopeOf("3.0.0", "4"));

        assertTrue(inScopeOf("3", "3.0.0.0.0"));
        assertTrue(inScopeOf("3", "3.0.1"));
        assertTrue(inScopeOf("3", "3.0.1.1.0"));
        assertTrue(inScopeOf("3", "3.1.0"));
        assertFalse(inScopeOf("3", "2"));
        assertTrue(inScopeOf("3", "4"));
    }
}
