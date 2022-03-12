
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_00_Basics_13 extends CommonTestRunner {

    public Test_00_Basics_13() {
        super(false);
    }

    /*
    linked variables is empty all around because String is @E2Immutable
     */
    @Test
    public void test_13() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("test".equals(d.methodInfo().name)) {
                DV enn = d.getProperty(EXTERNAL_NOT_NULL);
                DV nne = d.getProperty(NOT_NULL_EXPRESSION);
                DV cnn = d.getProperty(CONTEXT_NOT_NULL);
                DV cm = d.getProperty(CONTEXT_MODIFIED);
                String value = d.currentValue().toString();
                String linkedVariables = d.variableInfo().getLinkedVariables().toString();

                if (d.variable() instanceof ParameterInfo in1 && "in1".equals(in1.name)) {
                    if ("0".equals(d.statementId())) {
                        // means: there are no fields, we have no opinion, right from the start ->
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("nullable instance type String/*@Identity*/", value);
                        assertEquals("a:0,in1:0", linkedVariables); // symmetrical!
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                    }
                }
                if (d.variable() instanceof ParameterInfo in2 && "in2".equals(in2.name)) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("", linkedVariables);
                        assertEquals("nullable instance type String", value);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("<return value>", value);
                        assertEquals("", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                }
                if ("a".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("in1", value);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals("a:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertTrue(d.variableInfo().valueIsSet());
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals("a:0,in2:0", linkedVariables);
                    }
                }
                if ("b".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("a:0,b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NULLABLE_DV, nne);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("2".equals(d.statementId())) {
                        assertEquals("b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertTrue(d.variableInfo().valueIsSet());
                    }
                    if ("3".equals(d.statementId())) {
                        assertEquals("b:0,in1:0", linkedVariables);
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                    }
                    if ("4".equals(d.statementId())) {
                        assertEquals(MultiLevel.NULLABLE_DV, cnn);
                        assertEquals(DV.FALSE_DV, cm);
                        assertEquals("in1", value);
                        assertEquals("b:0,in1:0,return test:0", linkedVariables);
                        assertEquals(MultiLevel.NOT_INVOLVED_DV, enn);
                    }
                }
            }
        };
        testClass("Basics_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
