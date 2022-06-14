
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

package org.e2immu.analyser.parser.loops;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_01_Loops_20 extends CommonTestRunner {

    private static final String PATH_SPLIT = "path.split(\"/\")";

    public Test_01_Loops_20() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("loadBytes".equals(d.methodInfo().name)) {
                if ("prefix".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertEquals(PATH_SPLIT, d.currentValue().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("1".equals(d.statementId())) {
                        VariableInfo vi = d.variableInfoContainer().best(Stage.EVALUATION);
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, vi.getProperty(Property.NOT_NULL_EXPRESSION));
                    }
                    if ("1.0.0".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());
                        assertCurrentValue(d, 0, PATH_SPLIT);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    assertEquals("Type java.lang.String[]", d.currentValue().returnType().toString());
                }
                if ("s".equals(d.variableName())) {
                    if ("1.0.0".equals(d.statementId())) {
                        assertTrue(d.variableInfoContainer().variableNature() instanceof VariableNature.LoopVariable);
                        //     assertCurrentValue(d, 1, CAUSES_OF_DELAY+"|not_null:prefix@Method_loadBytes_1", "instance type String");
                    }
                }
                if (d.variable() instanceof FieldReference fr && "out".equals(fr.fieldInfo.name)) {
                    String expect = d.iteration() == 0 ? "<f:out>" : "out";
//                    assertEquals(expect, d.currentValue().toString());
                }
            }
        };
        testClass("Loops_20", 0, 0, new DebugConfiguration.Builder()
             //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
