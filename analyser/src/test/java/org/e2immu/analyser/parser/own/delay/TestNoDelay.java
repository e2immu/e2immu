
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
import org.e2immu.analyser.analyser.delay.NotDelayed;
import org.e2immu.analyser.analyser.delay.ProgressWrapper;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.junit.jupiter.api.Assertions.*;

public class TestNoDelay extends CommonTestRunner {

    public static final String MIN_IN_CAUSES_OF_DELAY = "org.e2immu.analyser.analyser.CausesOfDelay.$1.max(org.e2immu.analyser.analyser.DV)";

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if (MIN_IN_CAUSES_OF_DELAY.equals(d.methodInfo().fullyQualifiedName)) {
                if (d.variable() instanceof ReturnVariable) {
                    if ("1".equals(d.statementId())) {
                        assertEquals("other.isInitialDelay()?this:other", d.currentValue().toString());
                        assertDv(d, MultiLevel.MUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "other".equals(pi.name)) {
                    assertDv(d, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_IMMUTABLE);
                }
                if (d.variable() instanceof This) {
                    assertDv(d, 14, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("EMPTY".equals(d.fieldInfo().name)) {
                assertEquals("org.e2immu.analyser.analyser.CausesOfDelay.EMPTY", d.fieldInfo().fullyQualifiedName);
                assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.EXTERNAL_NOT_NULL);
                if (d.fieldAnalysis().getValue() instanceof ConstructorCall cc) {
                    assertEquals("org.e2immu.analyser.analyser.CausesOfDelay.$1", cc.anonymousClass().fullyQualifiedName);
                } else fail();
                assertLinked(d, d.fieldAnalysis().getLinkedVariables(), it(0, ""));

                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV,
                        d.fieldAnalysis().getValue().getProperty(EvaluationResultImpl.from(d.evaluationContext()), Property.NOT_NULL_EXPRESSION, true));
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d, MultiLevel.NOT_IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
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
                    case "DV", "AnalysisStatus", "AbstractDelay" ->
                            assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION,
                                    d.methodInfo().fullyQualifiedName);
                    case "NoDelay" -> assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    case "NotDelayed" ->
                            assertDv(d, 2, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    case "ProgressWrapper" ->
                            assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                    default -> fail(d.methodInfo().fullyQualifiedName);
                }
            }
            if (MIN_IN_CAUSES_OF_DELAY.equals(d.methodInfo().fullyQualifiedName)) {
                String expected = d.iteration() == 0 ? "<m:max>" : "/*inline max*/other.isInitialDelay()?this:other";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue()
                        .toString());
                assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("$1".equals(d.typeInfo().simpleName) &&  // the anonymous class of EMPTY
                    "CausesOfDelay".equals(d.typeInfo().packageNameOrEnclosingType.getRight().simpleName)) {
                assertHc(d, 0, "");
                assertDv(d, 13, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
            if ("AbstractDelay".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
                assertDv(d, 12, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("CausesOfDelay".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
            if ("DV".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
            if ("AnalysisStatus".equals(d.typeInfo().simpleName)) {
                assertHc(d, 0, "");
            }
        };

        List<Class<?>> classes = List.of(AbstractDelay.class, CausesOfDelay.class, CauseOfDelay.class, DV.class,
                NoDelay.class, NotDelayed.class, ProgressWrapper.class, AnalysisStatus.class);
        testSupportAndUtilClasses(classes, 0, 5, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
