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


import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.MethodReference_0;
import org.e2immu.analyser.resolver.testexample.MethodReference_1;
import org.e2immu.analyser.resolver.testexample.MethodReference_2;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;


public class TestMethodReference extends CommonTest {


    @Test
    public void test_0() throws IOException {
        inspectAndResolve(MethodReference_0.class);
    }

    @Test
    public void test_1() throws IOException {
        inspectAndResolve(MethodReference_1.class);
    }

    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(MethodReference_2.class);
        TypeInfo typeInfo = typeMap.get(MethodReference_2.class);

        testMethod("getFunction", typeMap, typeInfo, O_FUNCTION, "java.util.Map.get(Object)");
        testMethod("getFunction2", typeMap, typeInfo, S_FUNCTION, "org.e2immu.analyser.resolver.testexample.MethodReference_2.get(String)");
        testMethod("getFunction3", typeMap, typeInfo, S_FUNCTION, "java.lang.String.length()");
        testMethod("getFunction4", typeMap, typeInfo, S_FUNCTION, "java.lang.String.length()");
    }

    private static final String S_FUNCTION = "Type java.util.function.Function<String,Integer>";
    private static final String O_FUNCTION = "Type java.util.function.Function<Object,Integer>";

    private void testMethod(String methodName, TypeMap typeMap, TypeInfo typeInfo, String returnType, String methodFqn) {
        MethodInfo getFunction = typeInfo.findUniqueMethod(methodName, 0);
        Block block = typeMap.getMethodInspection(getFunction).getMethodBody();
        Expression expression = ((ReturnStatement) block.structure.getStatements().get(0)).expression;
        if (expression instanceof MethodReference mr) {
            assertEquals(methodFqn, mr.methodInfo.fullyQualifiedName);
        } else fail();
        assertEquals(returnType, expression.returnType().toString());
    }
}
