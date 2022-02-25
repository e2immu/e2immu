
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
a series of tests to detect an infinite delayed loop 20210311
 */
public class Test_38_FirstThen extends CommonTestRunner {

    public Test_38_FirstThen() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("FirstThen_0".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo first && "first".equals(first.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.CONTEXT_NOT_NULL));
                    assertEquals("nullable instance type S/*@Identity*/", d.currentValue().toString());
                    assertEquals("first:0,this.first:1", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("first".equals(d.fieldInfo().name)) {
                assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
                if (d.iteration() > 0) {
                    String expectValues = "[METHOD:null, CONSTRUCTION:first/*@NotNull*/]";
                    assertEquals(expectValues, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FirstThen_0".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param S", d.typeAnalysis().getTransparentTypes().toString());
            }
        };

        testClass("FirstThen_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // FIXME infinite delay loop due to changes in methodAnalysis.precondition
    @Test
    public void test_1() throws IOException {
        testClass("FirstThen_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
