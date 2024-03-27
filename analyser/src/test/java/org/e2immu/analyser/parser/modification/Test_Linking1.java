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

import org.e2immu.analyser.analyser.ChangeData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.EvaluationResultVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Linking1 extends CommonTestRunner {

    public Test_Linking1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (d.variable() instanceof ReturnVariable) {
                switch (d.methodInfo().name) {
                    case "m0" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, "supplier:4"));
                    }
                    case "m1" -> {
                        assertCurrentValue(d, 2, "supplier.get()");
                        assertLinked(d, it(0, 1, "supplier:-1"), it(2, "supplier:2"));
                    }
                    case "m2" -> {
                        assertCurrentValue(d, 0, "supplier.get()");
                        assertLinked(d, it(0, ""));
                    }
                    case "m3" -> {
                        assertCurrentValue(d, 3, "stream.filter(/*inline test*/3==m.i$0)");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:2"));
                    }
                    case "m4" -> {
                        assertCurrentValue(d, 3, "stream.filter(/*inline test*/3==m.i$0).findFirst()");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:2"));
                    }
                    case "m5" -> {
                        assertCurrentValue(d, 3, "stream.filter(/*inline test*/3==m.i$0).findFirst().orElseThrow()");
                        assertLinked(d, it(0, 1, "stream:-1"), it(2, "stream:2"));
                    }
                    case "m6" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i)");
                        assertLinked(d, it(0, "stream:2"));
                    }
                    case "m7" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i).findFirst()");
                        assertLinked(d, it(0, ""));
                    }
                    case "m8" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/3==i).findFirst().orElseThrow()");
                        assertLinked(d, it(0, ""));
                    }
                    case "m9" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(i))");
                        assertLinked(d, it(0, "stream:2"));
                    }
                    case "m10" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(i)).findFirst()");
                        assertLinked(d, it(0, "stream:4"));
                    }
                    case "m11" -> {
                        assertCurrentValue(d, 1, "stream.filter(/*inline test*/predicate.test(i)).findFirst().orElseThrow()");
                        assertLinked(d, it(0, "stream:4"));
                    }
                    default -> {
                    }
                }
            }
        };

        // finalizer on a parameter
        testClass("Linking_1", 6, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


}
