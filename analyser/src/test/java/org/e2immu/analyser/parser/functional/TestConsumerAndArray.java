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

package org.e2immu.analyser.parser.functional;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TestConsumerAndArray extends CommonTestRunner {

    public TestConsumerAndArray() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "array".equals(pi.name)) {
                    String expected = d.iteration() == 0 ? "<p:array>" : "nullable instance type T[]/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertEquals("consumer:3", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof DependentVariable dv && "array".equals(dv.arrayVariable().simpleName())) {
                    assertEquals("3", dv.indexExpression().toString());
                    String expectLink = "array:3,consumer:3";
                    assertEquals(expectLink, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        testClass("ConsumerAndArray_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


}
