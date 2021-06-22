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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
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
                if(d.variable() instanceof FieldReference fr && "set".equals(fr.fieldInfo.name)) {
                    if("0.0.0".equals(d.statementId())) {
                        assertEquals("Set.of(\"a\",\"b\")", d.currentValue().toString());
                    }
                }
                if (d.variableName().contains("$CI$")) {
                    assertEquals("org.e2immu.analyser.testexample.ConditionalInitialization_0.set$CI$0.0.0-E",
                            d.variableName());
                    if ("1.0.0".equals(d.statementId()) || "0.0.0".equals(d.statementId()))
                        fail("Should not exist here");
                    // holds for "0", "1"
                    assertEquals("Set.of(\"a\",\"b\")", d.currentValue().toString());
                    assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                assertEquals("Set.of(\"a\",\"b\"),new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/",
                        ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectExtImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ConditionalInitialization_0".equals(d.typeInfo().simpleName)) {
                assertEquals(MultiLevel.MUTABLE, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("ConditionalInitialization_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("ConditionalInitialization_1".equals(d.methodInfo().name)) {
                if (d.variableName().contains("$CI$")) {
                    assertEquals("org.e2immu.analyser.testexample.ConditionalInitialization_1.set$CI$0.0.0-E",
                            d.variableName());
                    if ("0.1.0".equals(d.statementId())) fail("Should not exist here");
                    // holds for "0"
                    assertEquals("Set.of(\"a\",\"b\")", d.currentValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expect = "Set.of(\"a\",\"b\"),new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,setParam";
                assertEquals(expect, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectExtImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.MUTABLE;
                assertEquals(expectExtImm, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        // field occurs in all constructors or at least one static block

        testClass("ConditionalInitialization_1", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("setSet".equals(d.methodInfo().name)) {
                if ("0.0.0".equals(d.statementId())) {
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
        // overwrite the value...
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("ConditionalInitialization_3".equals(d.methodInfo().name)) {
                if (d.variableName().contains("$CI$")) {
                    assertEquals("org.e2immu.analyser.testexample.ConditionalInitialization_3.set$CI$0.0.0-E",
                            d.variableName());
                    assertEquals("Set.of(\"a\",\"b\")", d.currentValue().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                String expect = "new HashSet<>()/*AnnotatedAPI.isKnown(true)&&0==this.size()*/,null";
                assertEquals(expect, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());

                assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                assertEquals(MultiLevel.MUTABLE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
            }
        };

        // field occurs in all constructors or at least one static block
        testClass("ConditionalInitialization_3", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_4() throws IOException {

        testClass("ConditionalInitialization_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
