
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Lazy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/*
Breaking the delays in "get()":
- in iteration 1, statement 1, EvaluationResult, breakSelfReferenceDelay
- this travels to SAApply, where a DelayedWrappedExpression is injected
- from statement 1 to statement 2 it carries over because the delayed state is suppressed at the end of SASubBlock
- the field analyser has real values in iteration 1, but no IMMUTABLE yet
- so the breaking has to repeat itself in iteration 2
- the field analyser decides on IMMUTABLE in iteration 2
- iteration 3 sees normal values
- the state goes from suppressed to real
 */
public class Test_Support_05_Lazy extends CommonTestRunner {

    public Test_Support_05_Lazy() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("Lazy".equals(d.methodInfo().name)) {
            if (d.variable() instanceof ParameterInfo p && "supplierParam".equals(p.name)) {
                assertDv(d, DV.TRUE_DV, Property.IGNORE_MODIFICATIONS);
            }
            if (d.variable() instanceof FieldReference fr && "supplier".equals(fr.fieldInfo.name)) {
                assertEquals("1", d.statementId());
                assertDv(d, DV.TRUE_DV, Property.IGNORE_MODIFICATIONS);
            }
        }
        if ("get".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference s && "supplier".equals(s.fieldInfo.name)) {
                assertFalse(d.variableInfo().isAssigned());
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0.0.0".equals(d.statementId())) {
                    String causes = switch (d.iteration()) {
                        case 0 -> "initial:this.t@Method_get_0.0.0";
                        case 1, 2 -> "initial@Field_t";
                        default -> "";
                    };
                    assertCurrentValue(d, 3, causes, "t$0");
                }
                if ("2".equals(d.statementId())) {
                    String value = switch (d.iteration()) {
                        case 0 -> "<f:t>";
                        case 1 -> "<wrapped:t>";
                        case 2 -> "<s:T>";
                        default -> "t$1-E$0";
                    };
                    assertEquals(value, d.currentValue().toString());
                    assertEquals(d.iteration() >= 3, d.currentValue().isDone());
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
            if (d.variable() instanceof FieldReference t && "supplier".equals(t.fieldInfo.name)) {
                assertCurrentValue(d, 1, "initial@Field_supplier", "instance type Supplier<T>");
            }

            if (d.variable() instanceof FieldReference t && "t".equals(t.fieldInfo.name)) {
                if ("0.0.0".equals(d.statementId())) {
                    assertCurrentValue(d, 3, "initial@Field_t", "nullable instance type T");
                }
                if ("0".equals(d.statementId())) {
                    String causes = switch (d.iteration()) {
                        case 0, 1, 2 -> "initial@Field_t";
                        default -> "";
                    };
                    assertCurrentValue(d, 3, causes, "nullable instance type T");
                }
                String expect = switch (d.iteration()) {
                    case 0 -> "<m:requireNonNull>";
                    case 1 -> "<wrapped:t>";
                    case 2 -> "<s:T>"; // break init delay gone already
                    default -> "nullable instance type T/*@NotNull*/";
                };
                if ("1".equals(d.statementId())) {
                    // should this not be supplier.get()? no, get() is modifying
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 3, DV.FALSE_DV, Property.IDENTITY);
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 3, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
        }
    };


    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("get".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                // delay gets broken from iteration 1; state is forced to true
                String expect = switch (d.iteration()) {
                    case 0 -> "null==<f:t>";
                    case 1, 2 -> "true";
                    default -> "null==t$0";
                };
                assertEquals("true", d.state().toString());
            }
            if ("2".equals(d.statementId())) {
                // important: if the state says something about t, then after assignment to t this should be
                // removed!
                assertEquals("true", d.state().toString());
                assertEquals("", d.statementAnalysis().stateData().equalityAccordingToStateStream()
                        .map(Object::toString).collect(Collectors.joining(",")));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("t".equals(d.fieldInfo().name)) {
            assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
            String expected = d.iteration() == 0 ? "initial:this.supplier@Method_get_1;values:this.t@Field_t"
                    : "null,nullable instance type T/*@NotNull*/";
            assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            assertEquals(d.iteration() > 0, d.fieldAnalysis().valuesDelayed().isDone());

            assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            assertDv(d, 2, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.EXTERNAL_IMMUTABLE);
        }
        if ("supplier".equals(d.fieldInfo().name)) {
            assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.IGNORE_MODIFICATIONS));
            assertEquals("supplierParam", d.fieldAnalysis().getValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (!"Lazy".equals(d.methodInfo().typeInfo.simpleName)) return;
        if ("get".equals(d.methodInfo().name)) {
            String expect = d.iteration() <= 2 ? "Precondition[expression=<precondition>, causes=[]]"
                    : "Precondition[expression=null==t, causes=[state]]";
            assertEquals(expect, d.methodAnalysis().getPreconditionForEventual().toString());
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Lazy".equals(d.typeInfo().simpleName)) {
            assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testSupportAndUtilClasses(List.of(Lazy.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
