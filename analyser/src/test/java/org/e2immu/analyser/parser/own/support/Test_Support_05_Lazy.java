
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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Lazy;
import org.junit.jupiter.api.Disabled;
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
                if ("0".equals(d.statementId())) {
                    assertDv(d, MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                }
            }
            if (d.variable() instanceof FieldReference fr && "supplier".equals(fr.fieldInfo.name)) {
                assertEquals("1", d.statementId());
                assertDv(d, MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
            }
        }
        if ("get".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference s && "supplier".equals(s.fieldInfo.name)) {
                assertFalse(d.variableInfo().isAssigned());
                assertDv(d, 10, MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
            }
            if (d.variable() instanceof ReturnVariable) {
                if ("0.0.0".equals(d.statementId())) {
                    assertCurrentValue(d, 10, "t$0");
                }
                if ("2".equals(d.statementId())) {
                    String value = switch (d.iteration()) {
                        case 0 -> "<f:t>";
                        case 1 -> "<f*:t>";
                        case 2 ->
                                "<null-check>?<m:requireNonNull>:<vp:t:break_init_delay:this.t@Method_get_2-C;ext_not_null@Field_supplier;initial:this.t@Method_get_0-C;values:this.t@Field_t>";
                        case 3, 4, 5, 6, 7 ->
                                "<null-check>?<m:requireNonNull>:<vp:t:break_init_delay:this.t@Method_get_0-C;ext_not_null@Field_supplier;initial:this.supplier@Method_get_1-C;initial:this.t@Method_get_0-C;values:this.t@Field_t>";
                        case 8, 9 -> "<wrapped:t>";
                        default -> "supplier.get()/*@NotNull*/";
                    };
                    assertEquals(value, d.currentValue().toString());
                    assertEquals(d.iteration() >= 10, d.currentValue().isDone());
                    assertDv(d, 10, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
            if (d.variable() instanceof FieldReference t && "supplier".equals(t.fieldInfo.name)) {
                assertCurrentValue(d, 10, "instance type Supplier<T>/*@IgnoreMods*/");
                assertDv(d, 10, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }

            if (d.variable() instanceof FieldReference t && "t".equals(t.fieldInfo.name)) {
                assertEquals("this", t.scopeVariable.simpleName());
                assertEquals("this", t.scope.toString());

                if ("0.0.0".equals(d.statementId())) {
                    assertCurrentValue(d, 10, "nullable instance type T");
                }
                if ("0".equals(d.statementId())) {
                    assertCurrentValue(d, 10, "nullable instance type T");
                }
                if ("1".equals(d.statementId())) {
                    // should this not be supplier.get()? no, get() is modifying
                    String expect = switch (d.iteration()) {
                        case 0, 1, 2, 3, 4, 5, 6, 7 -> "<m:requireNonNull>";
                        case 8, 9 -> "<wrapped:t>";
                        default -> "supplier.get()/*@NotNull*/";
                    };
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 10, DV.FALSE_DV, Property.IDENTITY);
                }
                if ("2".equals(d.statementId())) {
                    String expect = switch (d.iteration()) {
                        case 0, 1, 2, 3, 4, 5, 6, 7 -> "<m:requireNonNull>";
                        case 8, 9 -> "<wrapped:t>";
                        default -> "supplier.get()/*@NotNull*/";
                    };
                    assertEquals(expect, d.currentValue().toString());
                    assertDv(d, 10, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_EXPRESSION);
                }
            }
        }
        if ("hasBeenEvaluated".equals(d.methodInfo().name)) {
            if (d.variable() instanceof This) {
                assertEquals("", d.variableInfo().getLinkedVariables().toString());
            }
        }
    };


    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("get".equals(d.methodInfo().name)) {
            String state = switch (d.iteration()) {
                case 0, 1, 2, 3, 4, 6, 7, 8, 9 -> "<null-check>";
                case 5 -> "<simplification>";
                default -> "true";
            };
            if ("1".equals(d.statementId())) {
                assertEquals(state, d.state().toString());
            }
            if ("2".equals(d.statementId())) {
                // important: if the state says something about t, then after assignment to t this should be removed!
                assertEquals(state, d.state().toString());
                assertEquals("<f:t>=null", d.statementAnalysis().stateData().equalityAccordingToStateStream()
                        .map(Object::toString).collect(Collectors.joining(",")));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("t".equals(d.fieldInfo().name)) {
            assertEquals(DV.FALSE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
            String expected = switch (d.iteration()) {
                case 0 ->
                        "initial:this.supplier@Method_get_1-C;initial:this.t@Method_get_2-C;values:this.t@Field_t";
                case 1 ->
                        "break_init_delay:this.t@Method_get_2-C;ext_not_null@Field_supplier;initial:this.t@Method_get_0-C;values:this.t@Field_t";
                case 2, 3, 4, 5, 6, 7, 8 ->
                        "break_init_delay:this.t@Method_get_0-C;ext_not_null@Field_supplier;initial:this.supplier@Method_get_1-C;initial:this.t@Method_get_0-C;values:this.t@Field_t";
                default -> "null,supplier.get()/*@NotNull*/";
            };
            assertEquals(expected, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
            assertEquals(d.iteration() >= 9, d.fieldAnalysis().valuesDelayed().isDone());

            assertDv(d, 9, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            assertDv(d, 9, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
        }
        if ("supplier".equals(d.fieldInfo().name)) {
            assertEquals(DV.TRUE_DV, d.fieldAnalysis().getProperty(Property.FINAL));
            assertDv(d, MultiLevel.IGNORE_MODS_DV, Property.EXTERNAL_IGNORE_MODIFICATIONS);
            assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.EXTERNAL_IMMUTABLE);
            assertEquals("supplierParam", d.fieldAnalysis().getValue().toString());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (!"Lazy".equals(d.methodInfo().typeInfo.simpleName)) return;
        if ("get".equals(d.methodInfo().name)) {
            String expect = d.iteration() < 10 ? "Precondition[expression=<precondition>, causes=[]]"
                    : "Precondition[expression=null==t, causes=[state]]";
            assertEquals(expect, d.methodAnalysis().getPreconditionForEventual().toString());
            assertDv(d, 10, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Lazy".equals(d.typeInfo().simpleName)) {
            assertEquals("T", d.typeAnalysis().getHiddenContentTypes().toString());
        }
    };

    BreakDelayVisitor breakDelayVisitor = d -> {
        assertEquals("----MF-MF----", d.delaySequence());
    };

    @Test
    public void test() throws IOException {
        // null pointer caused by supplier.get()
        testSupportAndUtilClasses(List.of(Lazy.class), 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test0() throws IOException {
        testSupportAndUtilClasses(List.of(Lazy.class), 0, 0,
                new DebugConfiguration.Builder().build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

   // @Disabled("fails on command line, correct in IntelliJ")
    @Test
    public void test1() throws IOException {
        // supplier.get() null context on to supplier, on to parameter
        testSupportAndUtilClasses(List.of(Lazy.class), 0, 0, new DebugConfiguration.Builder()
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeContextPropertiesOverAllMethods(true).build());
    }

}
