
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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.OutputElement;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_Output_02_Symbol extends CommonTestRunner {

    public Test_Output_02_Symbol() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("length".equals(d.methodInfo().name) && "Symbol".equals(d.methodInfo().typeInfo.simpleName)) {

                // call to OutputElement.length(...), which has both a default implementation, and other implementations
                if (d.variable() instanceof ParameterInfo pi && "options".equals(pi.name)) {
                    String expected = d.iteration() == 0 ? "<p:options>" : "nullable instance type FormattingOptions/*@Identity*/";
                    assertEquals(expected, d.currentValue().toString());
                    assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if ("length".equals(d.methodInfo().name) && "OutputElement".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ParameterInfo pi && "options".equals(pi.name)) {
                    assertEquals("nullable instance type FormattingOptions/*@Identity*/", d.currentValue().toString());
                    assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            assertFalse(d.allowBreakDelay());

            if ("length".equals(d.methodInfo().name) && "OutputElement".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("right".equals(d.methodInfo().name) && "Symbol".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() == 0 ? "<m:right>" : "right";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                String pc = d.iteration() < 2 ? "<precondition>" : "true";
                assertEquals(pc, d.methodAnalysis().getPreconditionForEventual().expression().toString());
            }
        };

        // TODO solve errors at some point?
        testSupportAndUtilClasses(List.of(OutputElement.class, FormattingOptions.class, Symbol.class, Space.class),
                2, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
