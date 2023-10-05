
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

package org.e2immu.analyser.parser.own.delay;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.AbstractDelay;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestNoDelay extends CommonTestRunner {

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Cause".equals(d.methodInfo().name)) {
                int n = d.methodInfo().methodInspection.get().getParameters().size();
                if (2 == n) {
                    if (d.variable() instanceof FieldReference fr && "LOW".equals(fr.fieldInfo.name)) {
                        VariableInfo vi1 = d.variableInfoContainer().getPreviousOrInitial();
                        assertTrue(d.variableInfoContainer().isInitial());
                        if (d.iteration() > 0)
                            assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, vi1.getProperty(Property.CONTEXT_IMMUTABLE));

                        assertTrue(d.variableInfoContainer().hasEvaluation());
                        if (d.iteration() > 0)
                            assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.CONTEXT_IMMUTABLE);
                    }
                }
            }
            if ("causesOfDelay".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EMPTY".equals(d.fieldInfo().name)) {
                assertEquals("org.e2immu.analyser.analyser.CausesOfDelay.EMPTY", d.fieldInfo().fullyQualifiedName);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Cause".equals(d.methodInfo().name)) {
                int n = d.methodInfo().methodInspection.get().getParameters().size();
                if (3 == n) {
                    assertDv(d.p(2), MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                }
            }
            if ("causesOfDelay".equals(d.methodInfo().name)) {
                switch (d.methodInfo().typeInfo.simpleName) {
                    case "DV" -> assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    case "NoDelay" -> assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    default -> fail(d.methodInfo().fullyQualifiedName);
                }
            }
        };
        List<Class<?>> classes = List.of(AbstractDelay.class, CausesOfDelay.class, CauseOfDelay.class, DV.class, NoDelay.class);
        testSupportAndUtilClasses(classes, 0, 0, new DebugConfiguration.Builder()
            //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
           //     .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
          //      .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
