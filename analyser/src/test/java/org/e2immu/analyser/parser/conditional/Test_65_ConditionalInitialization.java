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

package org.e2immu.analyser.parser.conditional;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_65_ConditionalInitialization extends CommonTestRunner {

    public Test_65_ConditionalInitialization() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("ConditionalInitialization_0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "<vp:Set<String>:initial@Class_ConditionalInitialization_0>"
                                : "Set.of(\"a\",\"b\")";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expect = switch (d.iteration()) {
                            case 0 -> "<m:isEmpty>?<vp:Set<String>:initial@Class_ConditionalInitialization_0>:<f:set>";
                            case 1 -> "<wrapped:set>";// result of breaking init delay
                            default -> "ConditionalInitialization_0.set.isEmpty()?Set.of(\"a\",\"b\"):instance type Set<String>";
                        };
                        assertEquals(expect, d.currentValue().toString());
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                if(d.iteration()==0) {
                    assertTrue(d.fieldAnalysis().valuesDelayed().isDelayed());
                } else {
                    String expected = "Set.of(\"a\",\"b\"),new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/";
                    assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
                }
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ConditionalInitialization_0".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testClass("ConditionalInitialization_0", 0, 0, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("ConditionalInitialization_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vp:Set<String>:initial@Class_ConditionalInitialization_1>"
                                : "Set.of(\"a\",\"b\")";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "b?<vp:Set<String>:initial@Class_ConditionalInitialization_1>:<f:set>"
                                : "b?Set.of(\"a\",\"b\"):new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
            if ("setSet".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertEquals("setParam", d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "c?setParam:<f:set>";
                            case 1 -> "<wrapped:set>";
                            default -> "c?setParam:nullable instance type Set<String>";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expected = d.iteration() == 0
                        ? "initial:java.lang.System.out@Method_ConditionalInitialization_1_0.1.0;initial:this.set@Method_ConditionalInitialization_1_0.1.0;initial@Class_ConditionalInitialization_1;initial@Field_set;values:this.set@Field_set"
                        : "b?Set.of(\"a\",\"b\"):new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,setParam";
                assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
            }
        };

        // field occurs in all constructors or at least one static block

        testClass("ConditionalInitialization_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setSet".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId()) && d.iteration() > 1) {
                    assertNotNull(d.haveError(Message.Label.ASSIGNMENT_TO_SELF));
                }
            }
        };
        // warning: unused parameter; error: assignment to self
        // field occurs in all constructors or at least one static block

        testClass("ConditionalInitialization_2", 1, 2, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expect = "new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,null";
                assertEquals(expect, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertEquals(MultiLevel.NULLABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.MUTABLE_DV, d.fieldAnalysis().getProperty(Property.EXTERNAL_IMMUTABLE));
            }
        };

        // field occurs in all constructors or at least one static block
        testClass("ConditionalInitialization_3", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {

        testClass("ConditionalInitialization_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
