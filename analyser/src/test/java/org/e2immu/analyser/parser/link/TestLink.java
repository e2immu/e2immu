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

package org.e2immu.analyser.parser.link;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestLink extends CommonTestRunner {

    public TestLink() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                // 'start' stays out of the merge in 2.0.0
                if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertLinked(d, initial.getLinkedVariables(), it(0, "j:0"));
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        assertEquals("ExpressionAsStatement{class org.e2immu.analyser.model.expression.Assignment: j=i+1}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                    if ("2.0.0.0.3".equals(d.statementId()) || "2.0.0.0.4".equals(d.statementId())) {
                        assertEquals("BreakStatement{label=null}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("Link_0", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


}
