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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.InspectionProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestEqualsMethod extends CommonAbstractValue {

    private static final MethodInfo equalsMethodInfo;

    static {
        TypeInfo typeInfo = PRIMITIVES.objectTypeInfo();
        ParameterInspection.Builder other = new ParameterInspectionImpl.Builder(Identifier.generate())
                .setParameterizedType(PRIMITIVES.objectParameterizedType())
                .setIndex(0).setVarArgs(false).setName("other");
        equalsMethodInfo = new MethodInspectionImpl.Builder(typeInfo, "equals")
                .setReturnType(PRIMITIVES.booleanParameterizedType())
                .addParameter(other).build(InspectionProvider.DEFAULT).getMethodInfo();
        assert equalsMethodInfo != null : "Cannot find equals method in type " + typeInfo;
    }

    private static Expression eqMethod(Expression lhs, Expression rhs) {
        return new MethodCall(Identifier.generate(), lhs, equalsMethodInfo, List.of(rhs));
    }

    private static Expression newString(String s) {
        return new StringConstant(PRIMITIVES, s);
    }

    @Test
    public void test1() {
        Expression eq1 = eqMethod(s, newString("a"));
        assertEquals("s.equals(\"a\")", eq1.toString());
        Expression eq2 = eqMethod(newString("b"), s);
        assertEquals("\"b\".equals(s)", eq2.toString());
        assertEquals("false", newAndAppend(eq1, eq2).toString());
    }

    @Test
    public void test2() {
        Expression eq1 = eqMethod(s, newString("a"));
        assertEquals("s.equals(\"a\")", eq1.toString());
        Expression eq2 = eqMethod(s, newString("b"));
        assertEquals("s.equals(\"b\")", eq2.toString());
        Expression eq3 = eqMethod(s, newString("c"));
        assertEquals("s.equals(\"c\")", eq3.toString());
        Or or = (Or) newOrAppend(eq2, eq3);
        assertTrue(And.safeToExpandOr(s, or));
        assertEquals("false", newAndAppend(eq1, or).toString());
    }
}
