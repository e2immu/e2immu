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
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/*
See explanation in the source of Modification_26.
 */
public class Test_16_Modification_26 extends CommonTestRunner {

    public Test_16_Modification_26() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("instance type Entry<A,List<B>>", d.currentValue().toString());
                        assertEquals("src:2", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) { // else block
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("destination:4,inDestination:4,src:2", eval.getLinkedVariables().toString());
                        assertEquals(DV.TRUE_DV, eval.getProperty(Property.CONTEXT_MODIFIED)); // TODO wrong

                        assertEquals("destination:4,inDestination:4,src:2", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && pi.name.equals("destination")) {
                    assertEquals("nullable instance type Map<A,List<B>>", d.currentValue().toString());
                    if ("0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) {
                        assertEquals("e:4,src:4", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("e:4,inDestination:4,src:4", eval.getLinkedVariables().toString());
                        assertEquals("e:4,inDestination:4,src:4", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("inDestination".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("destination.get(e.getKey())", d.currentValue().toString());
                        assertEquals("destination:3", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) { // else block
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals("destination.get(e.getKey())",
                                eval.getValue().toString());
                        assertEquals("destination:3,e:4,src:4", eval.getLinkedVariables().toString());
                        assertEquals(DV.TRUE_DV, eval.getProperty(Property.CONTEXT_MODIFIED));

                        assertEquals("destination:3,e:4,src:4", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("change".equals(d.variableName())) {
                    if ("1.0.1.0.1".equals(d.statementId())) {
                        assertEquals("true", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, d.getProperty(Property.NOT_NULL_EXPRESSION));
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    // 2nd branch, merge of an if-statement
                    if ("1.0.1.1.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "instance type boolean||<vl:change>"
                                : "instance type boolean||instance type boolean";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    // merge of the two above
                    if ("1.0.1".equals(d.statementId())) {
                        String expected = d.iteration() == 0
                                ? "instance type boolean||<vl:change>||null==destination.get(e.getKey())"
                                : "instance type boolean||instance type boolean||null==destination.get(e.getKey())";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if ("change$1".equals(d.variableName())) {
                    fail("old code, these variables don't exist anymore");
                }
                if (d.variable() instanceof ParameterInfo pi && "src".equals(pi.name)) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertEquals("nullable instance type Map<A,List<B>>/*@Identity*/", d.currentValue().toString());
                        assertEquals("e:2", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.0.0".equals(d.statementId())) { // if block
                        assertEquals("destination:4,e:2", d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("1.0.1.1.0".equals(d.statementId())) { // else block
                        assertEquals("destination:4,e:2,inDestination:4",
                                d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // IMPROVE wrong
                    }
                    if ("1.0.1".equals(d.statementId())) { // if-else construct
                        assertEquals("destination:4,e:2,inDestination:4",
                                d.variableInfo().getLinkedVariables().toString());
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        // IMPROVE error should go, src is @NotModified rather than @Modified
        testClass("Modification_26", 1, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
