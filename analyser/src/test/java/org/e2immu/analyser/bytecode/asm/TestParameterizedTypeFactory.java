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

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.Objects;

public class TestParameterizedTypeFactory {
    private TypeContext typeContext = new TypeContext();

    @Test
    public void testInt() {
        Assert.assertEquals(Primitives.PRIMITIVES.intParameterizedType,
                ParameterizedTypeFactory.from(typeContext, "I").parameterizedType);
    }

    @Test
    public void testString() {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, "[Ljava/lang/String;").parameterizedType;
        Assert.assertEquals(Primitives.PRIMITIVES.stringTypeInfo,
                pt.typeInfo);
        Assert.assertEquals(1, pt.arrays);
    }

    @Test
    public void testStringAndInt() {
        String desc = "[Ljava/lang/String;I";
        ParameterizedTypeFactory.Result res = ParameterizedTypeFactory.from(typeContext, desc);
        Assert.assertEquals(Primitives.PRIMITIVES.stringTypeInfo,
                res.parameterizedType.typeInfo);
        Assert.assertEquals(1, res.parameterizedType.arrays);
        Assert.assertEquals('I', desc.charAt(res.nextPos));
    }

    @Test
    public void testCharArray() {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, "[C").parameterizedType;
        Assert.assertEquals(Primitives.PRIMITIVES.charTypeInfo,
                pt.typeInfo);
        Assert.assertEquals(1, pt.arrays);
    }

    @Test
    public void testGenerics() {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, "Ljava/util/List<Ljava/lang/String;>;").parameterizedType;
        Assert.assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        Assert.assertEquals("java.lang.String",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
    }


    @Test
    public void testWildcard() {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, "Ljava/lang/Class<*>;").parameterizedType;
        Assert.assertEquals("java.lang.Class",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        Assert.assertEquals("java.lang.Class<?>", pt.detailedString());
    }

    @Test
    public void testWildcardExtends() {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, "Ljava/lang/Class<+Ljava/lang/Number;>;").parameterizedType;
        Assert.assertEquals("java.lang.Class",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        Assert.assertEquals(1, pt.parameters.size());
        Assert.assertEquals("java.lang.Class<? extends java.lang.Number>", pt.detailedString());
    }

    @Test
    public void testGenerics2() {
        String signature = "Ljava/util/List<Ljava/lang/String;Ljava/lang/Integer;>;";
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, signature).parameterizedType;
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
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, signature).parameterizedType;
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
