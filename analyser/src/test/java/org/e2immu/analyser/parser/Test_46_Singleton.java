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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_46_Singleton extends CommonTestRunner {

    public Test_46_Singleton() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        testClass("Singleton_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Singleton_1".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "created".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<f:created>" : "instance type boolean";
                        // FIXME in iteration 2, value 'true'
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
            }
        };
        testClass("Singleton_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("Singleton_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // counter-example to the technique of test 0
    @Test
    public void test_3() throws IOException {
        testClass("Singleton_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
