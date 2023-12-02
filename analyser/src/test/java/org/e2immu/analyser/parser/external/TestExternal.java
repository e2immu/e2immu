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

package org.e2immu.analyser.parser.external;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class TestExternal extends CommonTestRunner {

    public TestExternal() {
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

        testClass("External_0", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // starting error: Property context-not-null, current nullable:1, new not_null:5 overwriting property value CLV
    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0"));
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0,h:2"));
                    }
                }
            }
            if ("report".equals(d.methodInfo().name)) {
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "filter:0,h:2"));
                    }
                }
                if ("p".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d,  MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "f:0,h:2"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "process".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "p:0"));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        // TODO three null-pointer warnings because we don't follow the link?? IMPROVE that should only be 1
        testClass("External_1", 1, 3, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // starting error: java.lang.IllegalStateException: Cannot change statically assigned for variable org.e2immu.analyser.parser.external.testexample.External_3.postAccumulate(org.xml.sax.XMLFilter,org.e2immu.analyser.parser.external.testexample.External_3.ProcessElement):1:process
    //old: p:-1
    //new: p:0
    @Test
    public void test_2() throws IOException {

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        // TODO should only be 1 null-pointer warning
        testClass("External_2", 1, 2, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

}
