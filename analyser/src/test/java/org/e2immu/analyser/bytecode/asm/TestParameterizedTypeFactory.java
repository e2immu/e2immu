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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.TRIGGER_BYTECODE_INSPECTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestParameterizedTypeFactory {
    @BeforeAll
    public static void beforeClass() {
        Logger.activate(Logger.LogTarget.BYTECODE_INSPECTOR);
    }

    private final TypeContext typeContext = new TypeContext(new TypeMapImpl.Builder(new Resources()));
    private final FindType findType = (fqn, path) -> typeContext.typeMapBuilder.getOrCreateFromPath(path,
            TRIGGER_BYTECODE_INSPECTION);

    @Test
    public void testInt() {
        assertEquals(typeContext.getPrimitives().intParameterizedType(),
                create("I").parameterizedType);
    }

    private ParameterizedTypeFactory.Result create(String signature) {
        return ParameterizedTypeFactory.from(typeContext, findType, signature);
    }

    @Test
    public void testString() {
        ParameterizedType pt = create("[Ljava/lang/String;").parameterizedType;
        assertEquals(typeContext.getPrimitives().stringTypeInfo(),
                pt.typeInfo);
        assertEquals(1, pt.arrays);
    }

    @Test
    public void testStringAndInt() {
        String desc = "[Ljava/lang/String;I";
        ParameterizedTypeFactory.Result res = create(desc);
        assertEquals(typeContext.getPrimitives().stringTypeInfo(),
                res.parameterizedType.typeInfo);
        assertEquals(1, res.parameterizedType.arrays);
        assertEquals('I', desc.charAt(res.nextPos));
    }

    @Test
    public void testCharArray() {
        ParameterizedType pt = create("[C").parameterizedType;
        assertEquals(typeContext.getPrimitives().charTypeInfo(),                pt.typeInfo);
        assertEquals(1, pt.arrays);
    }

    @Test
    public void testGenerics() {
        ParameterizedType pt = create("Ljava/util/List<Ljava/lang/String;>;").parameterizedType;
        assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(1, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        assertEquals("java.lang.String",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
    }


    @Test
    public void testWildcard() {
        ParameterizedType pt = create("Ljava/lang/Class<*>;").parameterizedType;
        assertEquals("java.lang.Class",                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(1, pt.parameters.size());
        ParameterizedType tp0 = pt.parameters.get(0);
        assertSame(ParameterizedType.WildCard.UNBOUND, tp0.wildCard);
    }

    @Test
    public void testWildcardSuper() {
        ParameterizedType pt = create("Ljava/lang/Class<-Ljava/lang/Number;>;").parameterizedType;
        assertEquals("java.lang.Class", Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(1, pt.parameters.size());
        ParameterizedType tp0 = pt.parameters.get(0);
        assertSame(ParameterizedType.WildCard.SUPER, tp0.wildCard);
    }

    @Test
    public void testWildcardExtends() {
        ParameterizedType pt = create("Ljava/lang/Class<+Ljava/lang/Number;>;").parameterizedType;
        assertEquals("java.lang.Class", Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(1, pt.parameters.size());
        ParameterizedType tp0 = pt.parameters.get(0);
        assertSame(ParameterizedType.WildCard.EXTENDS, tp0.wildCard);
    }

    @Test
    public void testGenerics2() {
        String signature = "Ljava/util/List<Ljava/lang/String;Ljava/lang/Integer;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(2, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        assertEquals("java.lang.String",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
        ParameterizedType tp2 = pt.parameters.get(1);
        assertEquals("java.lang.Integer",
                Objects.requireNonNull(tp2.typeInfo).fullyQualifiedName);
    }

    @Test
    public void testGenericsMap() {
        String signature = "Ljava/util/List<Ljava/util/Map<Ljava/lang/String;Ljava/lang/Double;>;>;";
        ParameterizedType pt = create(signature).parameterizedType;
        assertEquals("java.util.List",
                Objects.requireNonNull(pt.typeInfo).fullyQualifiedName);
        assertEquals(1, pt.parameters.size());
        ParameterizedType tp1 = pt.parameters.get(0);
        assertEquals("java.util.Map",
                Objects.requireNonNull(tp1.typeInfo).fullyQualifiedName);
        assertEquals(2, tp1.parameters.size());
        assertEquals("java.lang.Double",
                Objects.requireNonNull(tp1.parameters.get(1).typeInfo).fullyQualifiedName);
    }
}
