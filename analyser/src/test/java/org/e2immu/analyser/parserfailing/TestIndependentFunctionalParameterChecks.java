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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestIndependentFunctionalParameterChecks extends CommonTestRunner {

    public TestIndependentFunctionalParameterChecks() {
        super(true);
    }

    // the @NotNull1 on stream() is only known after the first iteration
    // it should not yet cause an error in the first.
    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if("getFirst".equals(d.methodInfo().name) && d.iteration() == 0) {
            assertNotNull(d.haveError(Message.NULL_POINTER_EXCEPTION)); // TODO
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("stream".equals(d.methodInfo().name)) {
            int notNull = d.methodAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION);
            if (d.iteration() == 0) {
                assertEquals(Level.DELAY, notNull);
            } else {
                assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("IndependentFunctionalParameterChecks", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
