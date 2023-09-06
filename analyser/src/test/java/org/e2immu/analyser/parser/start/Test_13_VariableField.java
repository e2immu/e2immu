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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
FIXME want extra examples of

- VF assigned to other VF
- VF with companion methods
- VF local assignment changes to instance after time increase

continuation of some examples that are in Basics 2, 3, 6, 7, 8, 14, 15
 */
public class Test_13_VariableField extends CommonTestRunner {

    public Test_13_VariableField() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        final String STRING = "org.e2immu.analyser.parser.start.testexample.VariableField_0.string";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getString".equals(d.methodInfo().name)) {

                // evaluation of the return value, which is 'string' in the current state
                if ("1".equals(d.statementId())) {
                    String expectValue = d.iteration() == 0 ? "b&&<m:startsWith>?\"abc\"+<f:string>:<f:string>"
                            : "b&&string$2.startsWith(\"abc\")?\"abc\"+string$2:string$2";
                    assertEquals(expectValue, d.evaluationResult().getExpression().toString());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("getString".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertEquals("true", d.absoluteState().toString());
                }
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.absoluteState().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("getString".equals(d.methodInfo().name)) {

                // first read copy

                if (d.variableName().endsWith("string$0")) {
                    assertEquals(STRING + "$0", d.variableName());
                    if ("0.0.0".equals(d.statementId()) || "0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    } else if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertEquals("b?nullable instance type String:<not yet assigned>", d.currentValue().toString());
                    } else fail("Statement " + d.statementId());
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));

                    // second read copy

                } else if (d.variableName().endsWith("string$1")) {
                    assertEquals(STRING + "$1", d.variableName());
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type String", d.currentValue().toString());
                    } else if ("0.0.0".equals(d.statementId())) {
                        // only seen in iteration 1, the return value has no delays anymore then !!
                        String expected = "string$0.startsWith(\"abc\")?nullable instance type String:<not yet assigned>";
                        assertEquals(expected, d.currentValue().toString());
                    } else if ("0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        // "0" only seen in iteration 1, no delays in the return value anymore
                        String expected = "string$0.startsWith(\"abc\")&&b?nullable instance type String:<not yet assigned>";
                        assertEquals(expected, d.currentValue().toString());
                    } else fail("Statement " + d.statementId());

                    // inherited from the previous read copy:
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));

                } else if (d.variableName().endsWith("string$2")) {
                    assertEquals("1", d.statementId());

                } else if (d.variableName().contains("string")) {
                    assertEquals(STRING, d.variableName());
                    // according to the new rules, this one should not have CNN set to Effectively Not Null
                }

                if (d.variable() instanceof ReturnVariable) {
                    if ("0.0.0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ?
                                "<m:startsWith>?\"abc\"+<f:string>:<return value>" :
                                "string$0.startsWith(\"abc\")?\"abc\"+string$1:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ?
                                "b&&<m:startsWith>?\"abc\"+<f:string>:<return value>" :
                                "b&&string$0.startsWith(\"abc\")?\"abc\"+string$1:<return value>";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };

        // potential null pointer
        testClass("VariableField_0", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }

}
