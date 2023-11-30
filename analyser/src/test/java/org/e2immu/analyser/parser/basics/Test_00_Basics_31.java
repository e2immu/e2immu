
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

package org.e2immu.analyser.parser.basics;


import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_31 extends CommonTestRunner {

    public Test_00_Basics_31() {
        super(true);
    }

    // most simple self-references
    @Test
    public void test() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        testClass("Basics_31", 0, 0, new DebugConfiguration.Builder()
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }

    // indirect references, not obviously self-ref
    @Test
    public void testB() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("", d.delaySequence());
        testClass("Basics_31B", 0, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }

    // more complex self-references
    @Test
    public void testC() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("", d.delaySequence());
        testClass("Basics_31C", 0, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }

    @Test
    public void testD() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());
        // FIXME not null
        testClass("Basics_31D", 0, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }
    @Test
    public void testE() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());
        // FIXME not null
        testClass("Basics_31E", 0, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }
}
