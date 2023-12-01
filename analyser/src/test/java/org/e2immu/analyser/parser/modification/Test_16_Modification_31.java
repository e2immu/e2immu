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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class Test_16_Modification_31 extends CommonTestRunner {

    public Test_16_Modification_31() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "i".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 0, "nullable instance type I/*@Identity*/");
                    }
                    if ("2".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertCurrentValue(d, 0, "instance 2 type I/*@Identity*/");
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("t".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "j.summary()");
                    }
                    if ("3".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "j$2.summary()");
                    }
                }

            }
            if ("method3".equals(d.methodInfo().name)) {
                if ("u".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "k.summary()");
                    }
                }
                if ("v".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "k$1.summary()");
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("i.summary()>=0", d.absoluteState().toString());
                }
                if ("2".equals(d.statementId())) {
                     assertEquals("i.summary()>=0", d.absoluteState().toString());
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("j.summary()>=0", d.absoluteState().toString());
                }
                if ("2".equals(d.statementId())) {
                     assertEquals("j.summary()>=0", d.absoluteState().toString());
                }
            }
        };

        testClass("Modification_31", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
