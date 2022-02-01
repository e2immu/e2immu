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

package org.e2immu.analyser.model.value.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.expression.util.LhsRhs;
import org.e2immu.analyser.model.value.CommonAbstractValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLhsRhs extends CommonAbstractValue {
    @Test
    public void test1() {
        Expression sNotNull = equals(s, NullConstant.NULL_CONSTANT);
        assertEquals("null==s", sNotNull.toString());
        List<LhsRhs> lhsRhs = LhsRhs.extractEqualities(sNotNull);
        assertEquals("[LhsRhs[lhs=null, rhs=s]]", lhsRhs.toString());
    }

    @Test
    public void test2() {
        Expression sNotNull = negate(equals(s, NullConstant.NULL_CONSTANT));
        assertEquals("null!=s", sNotNull.toString());
        List<LhsRhs> lhsRhs = LhsRhs.extractEqualities(sNotNull);
        assertTrue(lhsRhs.isEmpty());
    }
}
