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


import org.e2immu.analyser.annotationxml.model.Annotation;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.resolver.testexample.Annotations_0;
import org.e2immu.analyser.resolver.testexample.AnonymousType_0;
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
}
