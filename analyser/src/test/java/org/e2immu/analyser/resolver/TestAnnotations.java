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
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.*;
import org.e2immu.analyser.resolver.testexample.a.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class TestAnnotations extends CommonTest {

    /*
    which mask? in same2, the anonymous type $2 has a test() method.
    its return statement should refer to the parameter 'mask' of same2, not to the field mask.
     */
    @Test
    public void test_0() throws IOException {
        TypeMap typeMap = inspectAndResolve(Annotations_0.class);
        TypeInfo typeInfo = typeMap.get(Annotations_0.class);
        assertNotNull(typeInfo);
        MethodInfo method = typeInfo.findUniqueMethod("method", 0);
        List<AnnotationExpression> annotations = method.methodInspection.get().getAnnotations();
        assertEquals(1, annotations.size());
        AnnotationExpression a0 = annotations.get(0);
        assertEquals("false", a0.extract("value", ""));
    }

    @Test
    public void test_1() throws IOException {
        TypeMap typeMap = inspectAndResolve(TestImport.A, Annotations_1.class);
        TypeInfo typeInfo = typeMap.get(Annotations_1.class);
        assertNotNull(typeInfo);
        TypeInspection ti = typeInfo.typeInspection.get();
        AnnotationExpression ae = ti.getAnnotations().get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.a.Resources", ae.typeInfo().fullyQualifiedName);
        assertEquals(1, ae.expressions().size());
        MemberValuePair mvp = ae.expressions().get(0);
        if (mvp.value().get() instanceof ArrayInitializer ai) {
            assertEquals(2, ai.multiExpression.expressions().length);
            if (ai.multiExpression.expressions()[0] instanceof AnnotationExpression ae1) {
                assertEquals(3, ae1.expressions().size());
                MemberValuePair memberValuePair = ae1.expressions().get(2);
                assertEquals("Class<java.util.TreeMap>",
                        memberValuePair.value().get().returnType().fullyQualifiedName());
            } else fail();
        } else fail();
        assertEquals("@Resources({@Resource(name=\"xx\",lookup=\"yy\",type=TreeMap.class),@Resource(name=\"zz\",type=Integer.class)})",
                ae.toString());

        TypeInfo resource = typeMap.get(Resource.class);
        TypeInfo authenticationType = resource.typeInspection.get().subTypes()
                .stream().filter(st -> "AuthenticationType".equals(st.simpleName)).findFirst().orElseThrow();
        assertSame(TypeNature.ENUM, authenticationType.typeInspection.get().typeNature());
    }

    // more complicated: imports are involved; this causes an UnevaluatedAnnotationParameterValue to be created
    // in the AnnotationInspector
    @Test
    public void test_2() throws IOException {
        TypeMap typeMap = inspectAndResolve(TestImport.A, Annotations_2.class);
        TypeInfo typeInfo = typeMap.get(Annotations_2.class);
        assertNotNull(typeInfo);
        TypeInspection ti = typeInfo.typeInspection.get();
        AnnotationExpression ae = ti.getAnnotations().get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.a.Resources", ae.typeInfo().fullyQualifiedName);
        assertEquals(1, ae.expressions().size());
        MemberValuePair mvp = ae.expressions().get(0);
        if (mvp.value().get() instanceof ArrayInitializer ai) {
            assertEquals(2, ai.multiExpression.expressions().length);
            if (ai.multiExpression.expressions()[0] instanceof AnnotationExpression ae1) {
                assertEquals(3, ae1.expressions().size());
                MemberValuePair memberValuePair = ae1.expressions().get(2);
                assertEquals("Class<java.util.TreeMap>",
                        memberValuePair.value().get().returnType().fullyQualifiedName());
            } else fail();
        } else fail();
        assertEquals("@Resources({@Resource(name=Annotations_2.XX,lookup=\"yy\",type=TreeMap.class),@Resource(name=Annotations_2.ZZ,type=Integer.class)})",
                ae.toString());
    }

    // directly resolved by the AnnotationInspector, not via UnevaluatedAnnotationParameterValue
    @Test
    public void test_3() throws IOException {
        TypeMap typeMap = inspectAndResolve(TestImport.A, Annotations_3.class);
        TypeInfo typeInfo = typeMap.get(Annotations_3.class);
        assertNotNull(typeInfo);
        TypeInspection ti = typeInfo.typeInspection.get();
        AnnotationExpression ae = ti.getAnnotations().get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.a.Resource", ae.typeInfo().fullyQualifiedName);
        assertEquals(3, ae.expressions().size());
        assertEquals("@Resource(name=Annotations_3.XX,lookup=Annotations_3.ZZ,type=TreeMap.class)",
                ae.toString());
    }

    @Test
    public void test_4() throws IOException {
        TypeMap typeMap = inspectAndResolve(TestImport.A, Annotations_4.class);
        TypeInfo typeInfo = typeMap.get(Annotations_4.class);
        assertNotNull(typeInfo);
        TypeInspection ti = typeInfo.typeInspection.get();
        AnnotationExpression ae = ti.getAnnotations().get(0);
        assertEquals("org.e2immu.analyser.resolver.testexample.a.Resource", ae.typeInfo().fullyQualifiedName);
        assertEquals(3, ae.expressions().size());
        assertEquals("@Resource(name=Annotations_4.XX,lookup=Annotations_4.ZZ,authenticationType=AuthenticationType.CONTAINER)",
                ae.toString());
    }


    @Test
    public void test_5() throws IOException {
        TypeMap typeMap = inspectAndResolve(Annotations_5.class);
        TypeInfo typeInfo = typeMap.get(Annotations_5.class);
        assertNotNull(typeInfo);
        TypeInspection ti = typeInfo.typeInspection.get();
        assertTrue(ti.isAnnotation());
        assertEquals(2, ti.getAnnotations().size());
        AnnotationExpression a0 = ti.getAnnotations().get(0);
        assertEquals("java.lang.annotation.Target", a0.typeInfo().fullyQualifiedName);
        AnnotationExpression a1 = ti.getAnnotations().get(1);
        assertEquals("java.lang.annotation.Retention", a1.typeInfo().fullyQualifiedName);

        MethodInfo value = typeInfo.findUniqueMethod("value", 0);
        assertEquals("Type Class<?>", value.returnType().toString());
        assertTrue(value.methodInspection.get().getMethodBody().isEmpty());

        MethodInfo extra = typeInfo.findUniqueMethod("extra", 0);
        assertEquals("Type String", extra.returnType().toString());
        if(extra.methodInspection.get().getMethodBody().structure.statements().get(0) instanceof ReturnStatement rs) {
            assertEquals("\"!\"", rs.expression.toString());
        } else fail();
    }
}
