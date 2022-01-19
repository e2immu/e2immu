
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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_25_FieldReference extends CommonTestRunner {

    public Test_25_FieldReference() {
        super(false);
    }

    @Test
    public void test0() throws IOException {
        testClass("FieldReference_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // crucial here: seen in "0.0" vs seen in "0", i.e., cd is a local variable; it should never get to the
    // merge block of "0"; neither should its field access cd.properties.
    // the method ensuring this is "SAEvaluationContext.replaceLocalVariables"
    @Test
    public void test1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if ("cd".equals(d.variableName())) {
                    assertTrue(d.statementId().startsWith("0.0"), "Seen in " + d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "properties".equals(fr.fieldInfo.name)) {
                    if ("cd".equals(fr.scope.toString())) {
                        assertEquals("cd", fr.scope.toString());
                        assertTrue(d.statementId().startsWith("0.0"), "Seen in " + d.statementId());
                        if ("0.0.1.0.1".equals(d.statementId())) {
                            String expected = d.iteration() == 0 ? "<f:properties>" : "nullable instance type Map<String,Integer>";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if ("changeData.get(\"abc\")".equals(fr.scope.toString())) {
                        // this copy is allowed to live on! it cannot exist in iteration 0
                        assertTrue(d.iteration() > 0);
                        String expected = "nullable instance type Map<String,Integer>";
                        assertEquals(expected, d.currentValue().toString());
                    } else {
                        assertEquals("<replace:ChangeData>", fr.scope.toString());
                        // FIXME what to do with this lingering variable?
                    }
                }
            }
        };
        // potential null pointer exceptions
        testClass("FieldReference_1", 0, 3, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
