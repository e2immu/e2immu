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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTypeParameter extends CommonTest {

    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(TypeParameter_0.class);

        TypeInfo typeParameter = typeMap.get(TypeParameter_0.class);
        assertNotNull(typeParameter);
        MethodInfo addNode = typeParameter.findUniqueMethod("addNode", 3);
        List<ParameterInfo> addNodeParameters = addNode.methodInspection.get().getParameters();
        ParameterInfo p0 = addNodeParameters.get(0);
        assertNotNull(p0.parameterizedType.typeParameter);
        String TP0_FQN = TypeParameter_0.class.getCanonicalName();
        assertEquals(TP0_FQN, p0.parameterizedType.typeParameter.getOwner().getLeft().fullyQualifiedName);

        ParameterInfo p1 = addNodeParameters.get(1);
        TypeParameter p1tp = p1.parameterizedType.parameters.get(0).typeParameter;
        assertNotNull(p1tp);
        assertEquals(TP0_FQN, p1tp.getOwner().getLeft().fullyQualifiedName);

        ForEachStatement forEach = (ForEachStatement) addNode.methodInspection.get().getMethodBody().structure
                .getStatements().get(2);
        LocalVariableCreation lvc = (LocalVariableCreation) forEach.structure.initialisers().get(0);
        assertEquals("T d", lvc.toString());
        TypeParameter tpLvc = lvc.localVariableReference.parameterizedType.typeParameter;
        assertNotNull(tpLvc);
        assertEquals(TP0_FQN, tpLvc.getOwner().getLeft().toString());
    }

    @Test
    public void test_1a() throws IOException {
        TypeMap typeMap = inspectAndResolve(TypeParameter_1.class);

        TypeInfo typeParameter = typeMap.get(TypeParameter_1.class);
        assertNotNull(typeParameter);
        MethodInfo addNode = typeParameter.findUniqueMethod("addNode", 3);
        List<ParameterInfo> addNodeParameters = addNode.methodInspection.get().getParameters();
        ParameterInfo p0 = addNodeParameters.get(0);
        assertNotNull(p0.parameterizedType.typeParameter);
        String TP0_FQN = TypeParameter_1.class.getCanonicalName();
        assertEquals(TP0_FQN, p0.parameterizedType.typeParameter.getOwner().getLeft().fullyQualifiedName);

        ParameterInfo p1 = addNodeParameters.get(1);
        TypeParameter p1tp = p1.parameterizedType.parameters.get(0).typeParameter;
        assertNotNull(p1tp);
        assertEquals(TP0_FQN, p1tp.getOwner().getLeft().fullyQualifiedName);

        ForEachStatement forEach = (ForEachStatement) addNode.methodInspection.get().getMethodBody().structure
                .getStatements().get(0);
        LocalVariableCreation lvc = (LocalVariableCreation) forEach.structure.initialisers().get(0);
        assertEquals("TP0 d", lvc.toString());
        TypeParameter tpLvc = lvc.localVariableReference.parameterizedType.typeParameter;
        assertNotNull(tpLvc);
        assertEquals(TP0_FQN, tpLvc.getOwner().getLeft().toString());
    }

    @Test
    public void test_1b() throws IOException {
        TypeMap typeMap = inspectAndResolve(TypeParameter_1.class);

        TypeInfo typeParameter = typeMap.get(TypeParameter_1.class);
        assertNotNull(typeParameter);
        MethodInfo getOrCreate = typeParameter.findUniqueMethod("getOrCreate", 1);
        List<ParameterInfo> getOrCreateParameters = getOrCreate.methodInspection.get().getParameters();
        ParameterInfo p0 = getOrCreateParameters.get(0);
        assertNotNull(p0.parameterizedType.typeParameter);
        String TP0_FQN = TypeParameter_1.class.getCanonicalName();
        assertEquals(TP0_FQN, p0.parameterizedType.typeParameter.getOwner().getLeft().fullyQualifiedName);

        TypeParameter p1tp = getOrCreate.returnType().parameters.get(0).typeParameter;
        assertNotNull(p1tp);
        assertEquals(TP0_FQN, p1tp.getOwner().getLeft().fullyQualifiedName);
    }


    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(TypeParameter_2.class);
        TypeInfo typeInfo = typeMap.get(TypeParameter_2.class);
        assertNotNull(typeInfo);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("method", 1);
        TypeParameter tp = methodInfo.methodInspection.get().getTypeParameters().get(0);
        assertEquals("T as #0 in org.e2immu.analyser.resolver.testexample.TypeParameter_2.method(T extends org.e2immu.analyser.resolver.testexample.TypeParameter_2.WithId[])", tp.toString());
        assertEquals(1, tp.getTypeBounds().size());
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas
                && eas.expression instanceof LocalVariableCreation lvc) {
            assertEquals("result", lvc.localVariableReference.simpleName());
            ParameterizedType t = lvc.localVariableReference.parameterizedType;
            assertEquals("Type param T[]", t.toString());
            assertEquals(1, t.arrays);
            assertEquals(tp, t.typeParameter);
            // now we know that the type bound is properly in place
        } else fail();
        if (block.structure.statements().get(1) instanceof ForStatement forStatement
                && forStatement.structure.block().structure.statements().get(0) instanceof IfElseStatement ifElseStatement
                && ifElseStatement.structure.block().structure.statements().get(0) instanceof ExpressionAsStatement eas
                && eas.expression instanceof Assignment assignment) {
            assertEquals("result[i]", assignment.variableTarget.toString());
            ParameterizedType t = assignment.variableTarget.parameterizedType();
            assertEquals("Type param T", t.toString());
            assertEquals(0, t.arrays);
            assertEquals(tp, t.typeParameter);
            assertNotNull(t.typeParameter);
            List<ParameterizedType> typeBounds = t.typeParameter.getTypeBounds();
            assertEquals(1, typeBounds.size());
            // result[i] is of type T, with a proper type bound, so we should be able to find .i
        } else fail();
    }

    @Test
    public void test_3() throws IOException {
       inspectAndResolve(TypeParameter_3.class);
    }

    @Test
    public void test_4() throws IOException {
        inspectAndResolve(TypeParameter_4.class);
    }
}
