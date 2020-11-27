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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeParameterImpl;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Objects;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;

public class TestParameterizedTypeFactory {
    @BeforeClass
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.BYTECODE_INSPECTOR);
    }

    private final TypeContext typeContext = new TypeContext(new TypeMapImpl.Builder());
    private final FindType findType = (fqn, path) -> typeContext.typeMapBuilder.getOrCreateFromPath(path,
            TRIGGER_BYTECODE_INSPECTION);

    @Test
    public void testInt() {
        Assert.assertEquals(typeContext.getPrimitives().intParameterizedType,
                create("I").parameterizedType);
    }

    private ParameterizedTypeFactory.Result create(String signature) {
        return ParameterizedTypeFactory.from(typeContext, findType, signature);
    }

    @Test
    public void testString() {
        ParameterizedType pt = create("[Ljava/lang/String;").parameterizedType;
        Assert.assertEquals(typeContext.getPrimitives().stringTypeInfo,
                pt.typeInfo);
        Assert.assertEquals(1, pt.arrays);
    }

    @Test
    public void testStringAndInt() {
        String desc = "[Ljava/lang/String;I";
        ParameterizedTypeFactory.Result res = create(desc);
        Assert.assertEquals(typeContext.getPrimitives().stringTypeInfo,
                res.parameterizedType.typeInfo);
        Assert.assertEquals(1, res.parameterizedType.arrays);
        Assert.assertEquals('I', desc.charAt(res.nextPos));
    }

    @Test
    public void testCharArray() {
        ParameterizedType pt = create("[C").parameterizedType;
        Assert.assertEquals(typeContext.getPrimitives().charTypeInfo,
                pt.typeInfo);
        Assert.assertEquals(1, pt.arrays);
    }

    @Test
    public void testGenerics() {
        ParameterizedType pt = create("Ljava/util/List<Ljava/lang/String;>;").parameterizedType;
        Assert.assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        Assert.assertEquals("java.lang.String",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
    }


    @Test
    public void testWildcard() {
        ParameterizedType pt = create("Ljava/lang/Class<*>;").parameterizedType;
        Assert.assertEquals("java.lang.Class",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        Assert.assertEquals("java.lang.Class<?>", pt.detailedString());
    }

    @Test
    public void testWildcardExtends() {
        ParameterizedType pt = create("Ljava/lang/Class<+Ljava/lang/Number;>;").parameterizedType;
        Assert.assertEquals("java.lang.Class",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        Assert.assertEquals("java.lang.Class<? extends java.lang.Number>", pt.detailedString());
    }

    @Test
    public void testGenerics2() {
        String signature = "Ljava/util/List<Ljava/lang/String;Ljava/lang/Integer;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        Assert.assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(2, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        Assert.assertEquals("java.lang.String",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
        ParameterizedType tp2 = pt.parameters.get(1);
        Assert.assertEquals("java.lang.Integer",
                Objects.requireNonNull(tp2.typeInfo).fullyQualifiedName);
    }

    @Test
    public void testGenericsMap() {
        String signature = "Ljava/util/List<Ljava/util/Map<Ljava/lang/String;Ljava/lang/Double;>;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        Assert.assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        Assert.assertEquals("java.util.Map",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
        Assert.assertEquals(2, tp1.parameters.size());
        Assert.assertEquals("java.lang.Double",
                Objects.requireNonNull(tp1.parameters.get(1).typeInfo).fullyQualifiedName);
    }
}
