
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

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_26 extends CommonTestRunner {

    public Test_00_Basics_26() {
        super(false);
    }

    /*
    tests availability of the value after a value assignment inside an expression
     */
    @Test
    public void test() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            String value = d.evaluationResult().getExpression().toString();
            if ("method1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("null==in?null:\"Not null: \"+in.toUpperCase()+\" == \"+in.toUpperCase()",
                            value);
                }
            }

            if ("method2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("null==in?null:\"Not null: \"+in.toUpperCase()+\" == \"+in", value);
                }
            }

            if ("method3".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("in.contains(in.toUpperCase())", value);
                }
            }

            if ("method4".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("in.substring(-5+in.length(),in.length())", value);
                }
            }
        };

        testClass("Basics_26", 0, 0, new DebugConfiguration.Builder()
                        .addEvaluationResultVisitor(evaluationResultVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }

}
