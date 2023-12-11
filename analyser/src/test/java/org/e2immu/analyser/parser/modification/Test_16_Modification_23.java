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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_16_Modification_23 extends CommonTestRunner {

    public Test_16_Modification_23() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method1".equals(d.methodInfo().name)) {
                if ("middle".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashMap<>(in)/*this.size()==in.size()*/",
                                d.currentValue().toString());
                    }
                    // now comes a method call modifying the keySet, which is dependent on middle
                    if ("2".equals(d.statementId()) || "3".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<mod:Set<String>>"
                                : "instance 2 type HashMap<String,Integer>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("keySet".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("middle.keySet()/*@NotNull this.size()==in.size()*/",
                                d.currentValue().toString());
                        assertEquals("in:4,middle:2", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<mod:Set<String>>" : "middle.keySet()/*@NotNull*/";
                        assertEquals(expected, d.currentValue().toString());
                        String linked = d.iteration() == 0 ? "in:-1,middle:-1" : "in:4,middle:2";
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
            }
            if ("method2".equals(d.methodInfo().name)) {
                if ("middle".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("new HashMap<>(in)/*this.size()==in.size()*/",
                                d.currentValue().toString());
                    }
                    // now comes a method call modifying the keySet, which is dependent on middle
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<mod:Set<String>>"
                                : "instance 1 type HashMap<String,Integer>";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("2".equals(d.statementId())) {
                        assertCurrentValue(d, 1, "middle$1.size()");
                    }
                }
            }
        };


        testClass("Modification_23", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
