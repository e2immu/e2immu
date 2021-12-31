
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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_36_Cast extends CommonTestRunner {


    public Test_36_Cast() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Cast_0".equals(d.typeInfo().simpleName)) {
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Cast_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Cast_1".equals(d.typeInfo().simpleName)) {
                assertTrue(d.typeAnalysis().getTransparentTypes().isEmpty(),
                        () -> "Have " + d.typeAnalysis().getTransparentTypes().toString());
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }

            if ("Counter".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("incrementedT".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ? "<m:increment>" : "instance type int";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    String expectValue = d.iteration() == 0 ? "<f:t>" : "instance type T";
                    assertEquals(expectValue, d.currentValue().toString());
                    String expectLinked = d.iteration() == 0 ? "return incrementedT:-1,this.t:0" : "this.t:0";
                    assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    assertDv(d, 1, Level.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("getTAsString".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                assertEquals("t/*(String)*/", d.currentValue().toString());
                assertTrue(d.currentValue() instanceof PropertyWrapper pw &&
                        pw.castType().equals(d.evaluationContext().getPrimitives().stringParameterizedType()));
            }
            if ("getTAsCounter".equals(d.methodInfo().name) && d.variable() instanceof ReturnVariable) {
                assertEquals("t/*(Counter)*/", d.currentValue().toString());
                assertTrue(d.currentValue() instanceof PropertyWrapper pw &&
                        "Counter".equals(Objects.requireNonNull(pw.castType().typeInfo).simpleName));
                assertDv(d, 2, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("incrementedT".equals(d.methodInfo().name)) {
                assertDv(d, 1, Level.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("Cast_1".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 2, Level.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("Cast_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Cast_2".equals(d.typeInfo().simpleName)) {
                assertEquals("T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Cast_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }
}
