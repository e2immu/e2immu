
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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.EventuallyFinal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Support_07_EventuallyFinal extends CommonTestRunner {

    public Test_Support_07_EventuallyFinal() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyFinal".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param T]", d.typeAnalysis().getTransparentTypes().toString());
                int expectImm = d.iteration() == 0 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                assertEquals(expectImm, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImm, d.fieldAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("setFinal".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo value && "value".equals(value.name)) {
                    assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, d.getProperty(VariableProperty.IMMUTABLE));
                    int expectExtImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.NOT_INVOLVED;
                    assertEquals(expectExtImm, d.getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                }
                if (d.variable() instanceof FieldReference fr && "value".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        String expectValue = d.iteration() == 0 ? "<s:T>" : "value";
                        assertEquals(expectValue, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof This) {
                    int expectImm = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EVENTUALLY_E2IMMUTABLE;
                    assertEquals(expectImm, d.variableInfoContainer().getPreviousOrInitial()
                            .getProperty(VariableProperty.IMMUTABLE), "Statement " + d.statementId());
                    assertEquals(expectImm, d.getProperty(VariableProperty.IMMUTABLE),
                            "Statement " + d.statementId());
                }
            }
        };

        testSupportAndUtilClasses(List.of(EventuallyFinal.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
