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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeParameter;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.TypeParameter_0;
import org.e2immu.analyser.resolver.testexample.TypeParameter_1;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestTypeParameter extends CommonTest {

    @Test
    public void test1() throws IOException {
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
    public void test2a() throws IOException {
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
    public void test2b() throws IOException {
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
}
