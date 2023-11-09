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

package org.e2immu.analyser.resolver;


import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.Array_0;
import org.e2immu.analyser.resolver.testexample.Array_1;
import org.e2immu.analyser.resolver.testexample.Varargs_0;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;


public class TestArray extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Array_0.class);
        TypeInfo typeInfo = typeMap.get(Array_0.class);
        assertNotNull(typeInfo);
        MethodInfo method2 = typeInfo.findUniqueMethod("method2", 0);
        Block block = method2.methodInspection.get().getMethodBody();
        Statement s1 = block.structure.statements().get(0);
        if (s1 instanceof ExpressionAsStatement eas && eas.expression instanceof LocalVariableCreation lvc) {
            assertEquals("entries", lvc.localVariableReference.simpleName());
            ParameterizedType type = lvc.localVariableReference.parameterizedType;
            assertEquals(1, type.arrays);
            assertEquals(2, type.parameters.size());
        } else fail();
    }

    @Test
    public void test_1() throws IOException {
        inspectAndResolve(Array_1.class);
    }
}
