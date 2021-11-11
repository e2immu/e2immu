
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.FlipSwitch;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Support_01_FlipSwitch extends CommonTestRunner {

    public Test_Support_01_FlipSwitch() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("isSet".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("set".equals(d.methodInfo().name)) {
                if (d.variable() instanceof LocalVariableReference lvr &&
                        lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy &&
                        "isSet".equals(copy.localCopyOf().fieldInfo.name)) {
                    assertEquals("isSet$0", d.variableInfo().variable().simpleName());
                }
                if (d.variable() instanceof FieldReference fr && "isSet".equals(fr.fieldInfo.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        int expectEnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                        assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    if ("0".equals(d.statementId())) {
                        assertEquals(MultiLevel.NOT_INVOLVED, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                    }
                    assertEquals("this.isSet:0", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("FlipSwitch".equals(d.typeInfo().simpleName)) {
                String expectE2 = d.iteration() <= 1 ? "{}" : "{isSet=!isSet}";
                assertEquals(expectE2, d.typeAnalysis().getApprovedPreconditionsE2().toString());
            }
        };

        testSupportAndUtilClasses(List.of(FlipSwitch.class), 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .build());
    }

}
