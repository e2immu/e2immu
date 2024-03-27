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

package org.e2immu.analyser.parser.modification;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.modification.testexample.Modification_0B;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Linking0 extends CommonTestRunner {

    public Test_Linking0() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            switch (d.methodInfo().name) {
                case "m1" -> {
                    String expectedLv = d.iteration() < 2 ? "list:-1" : "list:2";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                    ChangeData cd = d.findValueChangeByToString("m1");
                    assertEquals(expectedLv, cd.linkedVariables().toString());
                }
                case "m7" -> {
                    String expectedLv = d.iteration() < 2 ? "list:-1" : "list:2";
                    assertEquals(expectedLv, d.evaluationResult().linkedVariablesOfExpression().toString());
                    ChangeData cd = d.findValueChangeByToString("m7");
                    assertEquals(expectedLv, cd.linkedVariables().toString());
                }
                default -> {
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "m0" -> {
                        assertCurrentValue(d, 0, "list.get(0)");
                        assertLinked(d, it(0, ""));
                    }
                    case "m1" -> {
                        assertCurrentValue(d, 2, "list.get(0)");
                        assertLinked(d, it(0, 1, "list:-1"), it(2, "list:2"));
                    }
                    case "m2" -> {
                        assertCurrentValue(d, 0, "list.get(0)");
                        assertLinked(d, it(0, "list:4"));
                    }
                    case "m3", "m4", "m5" -> {
                        assertCurrentValue(d, 0, "list.subList(0,1)");
                        assertLinked(d, it(0, "list:2"));
                    }
                    case "m6" -> {
                        assertCurrentValue(d, 0, "new ArrayList<>(list)");
                        assertLinked(d, it(0, ""));
                    }
                    case "m7" -> {
                        assertCurrentValue(d, 0, "new ArrayList<>(list)");
                        assertLinked(d, it(0, "list:2"));
                    }
                    case "m8" -> {
                        assertCurrentValue(d, 0, "new ArrayList<>(list)");
                        assertLinked(d, it(0, "list:4"));
                    }
                    default -> {
                    }
                }
            }
        };

        testClass("Linking_0", 0, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


}
