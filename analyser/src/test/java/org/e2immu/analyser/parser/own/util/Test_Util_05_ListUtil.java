
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_05_ListUtil extends CommonTestRunner {

    @Test
    public void test() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("compare".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    // because hasNext is a modifying method
                    String expect = d.iteration() == 0 ? "!<m:hasNext>" : "!instance type boolean";
                    assertEquals(expect, d.evaluationResult().value().toString());
                }
            }
            if ("immutableConcat".equals(d.methodInfo().name)) {
                if ("1.0.0.0.0".equals(d.statementId())) {
                    assertEquals(4, d.evaluationResult().changeData().size());
                    // contains list, lists
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("immutableConcat".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "lists".equals(pi.name)) {
                    if ("1.0.0.0.0".equals(d.statementId()) || "1.0.0".equals(d.statementId()) || "1".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("list".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        if (d.iteration() > 0) {
                            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, eval.getProperty(Property.CONTEXT_NOT_NULL));
                        }
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
                if ("t".equals(d.variableName())) {
                    if ("1.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type T", d.currentValue().toString());
                        assertTrue(d.variableInfo().isAssigned());
                    }
                    if ("1".equals(d.statementId())) {
                        fail(); // should not exist beyond the loop!
                    }
                }
            }
            if ("compare".equals(d.methodInfo().name)) {
                if ("it2".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("values2.iterator()", d.currentValue().toString());
                        assertEquals(MultiLevel.MUTABLE_DV, d.getProperty(Property.IMMUTABLE));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<vl:it2>" : "instance type Iterator<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "values1.isEmpty()?values2.iterator():<vl:it2>"
                                : "values1.isEmpty()?values2.iterator():instance type Iterator<T>";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                    }
                }
            }
            if ("joinLists".equals(d.methodInfo().name)) {
                if ("it2".equals(d.variableName())) {
                    String expected = d.iteration() == 0 ? "<vl:it2>" : "instance type Iterator<L>";
                    if ("2.0.0".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("2.0.1".equals(d.statementId())) {
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("concatImmutable".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("!list1.isEmpty()&&!list2.isEmpty()", d.conditionManagerForNextStatement().state().toString());
                }
            }
        };

        testSupportAndUtilClasses(List.of(ListUtil.class), 0, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
