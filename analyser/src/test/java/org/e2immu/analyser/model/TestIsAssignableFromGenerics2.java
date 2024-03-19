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

package org.e2immu.analyser.model;

import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.e2immu.analyser.model.Inspector.BY_HAND;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.*;
import static org.junit.jupiter.api.Assertions.*;

/*
https://docs.oracle.com/javase/tutorial/java/generics/index.html
 */
public class TestIsAssignableFromGenerics2 {

    Primitives primitives;
    InspectionProvider IP;
    TypeInfo a, b;

    @BeforeEach
    public void before() {
        primitives = new PrimitivesImpl();
        IP = InspectionProvider.DEFAULT;
        String PACKAGE = "org.e2immu";
        primitives.objectTypeInfo().typeInspection.set(new TypeInspectionImpl.Builder(primitives.objectTypeInfo(), BY_HAND)
                .setTypeNature(TypeNature.CLASS)
                .setAccess(Inspection.Access.PUBLIC)
                .build(null));

        a = new TypeInfo(PACKAGE, "A");
        {
            TypeParameterImpl at = new TypeParameterImpl(a, "T", 0);
            ParameterizedType typeBound = new ParameterizedType(a, List.of(new ParameterizedType(at, 0, EXTENDS)));
            at.setTypeBounds(List.of(typeBound));

            TypeInspection.Builder aInspection = new TypeInspectionImpl.Builder(a, BY_HAND)
                    .noParent(primitives)
                    .addTypeParameter(at);
            a.typeInspection.set(aInspection
                    .setTypeNature(TypeNature.CLASS)
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }

        b = new TypeInfo(PACKAGE, "B");
        {
            TypeParameterImpl bs = new TypeParameterImpl(b, "S", 0);
            ParameterizedType typeBound = new ParameterizedType(b, List.of(new ParameterizedType(bs, 0, EXTENDS)));
            bs.setTypeBounds(List.of(typeBound));

            TypeInspection.Builder bInspection = new TypeInspectionImpl.Builder(b, BY_HAND)
                    .noParent(primitives)
                    .addTypeParameter(bs);
            ParameterizedType bPt = new ParameterizedType(bs, 0, NONE);
            ParameterizedType at = new ParameterizedType(a, List.of(bPt));
            b.typeInspection.set(bInspection
                    .setParentClass(at)
                    .setTypeNature(TypeNature.CLASS)
                    .setAccess(Inspection.Access.PUBLIC)
                    .build(null));
        }
    }

    @Test
    public void testConstructionA() {
        ParameterizedType ptA = a.asParameterizedType(InspectionProvider.DEFAULT);
        assertEquals("Type org.e2immu.A<T>", ptA.toString());
        assertEquals(1, ptA.parameters.get(0).typeParameter.getTypeBounds().size());
        assertSame(a, ptA.parameters.get(0).typeParameter.getTypeBounds().get(0).typeInfo);
    }

    @Test
    public void testConstructionB() {
        ParameterizedType ptB = b.asParameterizedType(InspectionProvider.DEFAULT);
        assertEquals("Type org.e2immu.B<S>", ptB.toString());
        assertEquals(1, ptB.parameters.get(0).typeParameter.getTypeBounds().size());
        assertSame(b, ptB.parameters.get(0).typeParameter.getTypeBounds().get(0).typeInfo);
        assertEquals("Type org.e2immu.A<S>", b.typeInspection.get().parentClass().toString());
    }

    @Test
    public void test() {
        // A <- B is possible
        // B <- A is not possible
        ParameterizedType ptA = a.asParameterizedType(InspectionProvider.DEFAULT);
        ParameterizedType ptB = b.asParameterizedType(InspectionProvider.DEFAULT);
        assertTrue(ptA.isAssignableFrom(InspectionProvider.DEFAULT, ptB));
        assertFalse(ptB.isAssignableFrom(InspectionProvider.DEFAULT, ptA));
    }

}
