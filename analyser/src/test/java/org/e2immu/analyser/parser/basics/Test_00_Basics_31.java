
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
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_31 extends CommonTestRunner {

    public Test_00_Basics_31() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method5".equals(d.methodInfo().name)) {
                if ("res".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals("<m:substring>", d.currentValue().toString());
                        assertEquals("[c]", d.currentValue().variables().toString());
                    }
                }
                if (d.variable() instanceof FieldReference fr && "c".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:c>";
                            case 1 ->
                                    "<vp:c:initial:this.c@Method_method1_0-C;initial:this.c@Method_method2_0-C;initial:this.c@Method_method3_0-C;initial:this.c@Method_method4_0-C;initial:this.c@Method_method5_0-C;initial:this.s@Method_method3_0-C;values:this.c@Field_c>";
                            default ->
                                    "<vp:c:break_init_delay:this.c@Method_method4_0-C;break_init_delay:this.c@Method_method5_0-C;initial:this.c@Method_method5_0-C;values:this.c@Field_c>";
                            //    default -> "";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<m:substring>" : "c$0.substring(11)";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if(d.variable() instanceof ReturnVariable) {
                    if("2".equals(d.statementId())) {
                        assertEquals("<m:substring>", d.currentValue().toString());
                        assertLinked(d, it(0, "res:0,this.c:-1,this:-1"));
                    }
                }
            }
        };
        testClass("Basics_31", 0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build());
    }


}
