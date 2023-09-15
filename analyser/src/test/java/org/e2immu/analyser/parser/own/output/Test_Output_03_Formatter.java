
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

package org.e2immu.analyser.parser.own.output;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Output_03_Formatter extends CommonTestRunner {

    public Test_Output_03_Formatter() {
        super(true);
    }

    // without the whole formatter package
    @Test
    public void test_0() throws IOException {
        testSupportAndUtilClasses(List.of(ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                0, 0, new DebugConfiguration.Builder()
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

    //Forward.combine(nullable instance type ElementarySpace,list.get(pos$8)/*(Space)*/.elementarySpace(nullable instance type FormattingOptions/*@Identity*/)),
    //Forward.combine(nullable instance type ElementarySpace,list.get(pos$8)/*(Space)*/.elementarySpace(options))
    // the real deal

    @Disabled("Bad modification decision on immutable object")
    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if ("6.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 7 ? "<m:pop>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 7, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 8, d.status().isDone());
                    assertEquals(d.iteration() >= 8, d.externalStatus().isDone());
                }
                if ("7.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 7 ? "<m:write>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 7, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 8, d.status().isDone());
                    assertEquals(d.iteration() >= 8, d.externalStatus().isDone());
                }
                if ("7".equals(d.statementId())) {
                    String expected = d.iteration() < 7 ? "!<vl:writeNewLine>"
                            : "null==`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`&&-1+end$5>=pos$5&&(!scope-newLineDouble:5.writeNewLine||null==`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`)&&(null==`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`||null!=``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`)";

                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 7, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 8, d.status().isDone());
                    assertEquals(d.iteration() >= 9, d.externalStatus().isDone());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if ("writeNewLine".equals(d.variableName())) {
                    if ("5".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 ->
                                    "(<null-check>?<s:boolean>:!<null-check>&&(<dv:scope-newLineDouble:5.writeNewLine>||!<null-check>))||<v:pos>>=<v:end>";
                            case 1, 2, 3, 4, 5, 6 ->
                                    "(<null-check>?<s:boolean>:!<null-check>&&(<dv:scope-newLineDouble:5.writeNewLine>||!<null-check>))||pos$5>=end$5";
                            default ->
                                    "(scope-newLineDouble:5.writeNewLine||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`||null==``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`||pos$5>=end$5)&&(null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`||pos$5>=end$5)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                    }
                    if ("5.0.6.1.0.1.4.0.1".equals(d.statementId())) {
                        String expected = d.iteration() < 7 ? "<f:newLineDouble.writeNewLine>"
                                : "(this.handleGuide(``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`,tabs$5,writer)).writeNewLine";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(d.iteration() >= 7, d.currentValue().isDone());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "NOT_END".equals(fr.fieldInfo.name)) {
                    if ("5".equals(d.statementId()) || "6".equals(d.statementId()) || "6.0.0".equals(d.statementId())) {
                        assertDv(d, 7, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "writer".equals(pi.name)) {
                    if ("5.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, 7, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertDv(d, 7, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("pop".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("tab".equals(d.variableName())) {
                    if ("0.0.3".equals(d.statementId())) {
                        String expected = d.iteration() < 3 ? "<m:pop>" : "nullable instance type Tab";
                        assertEquals(expected, d.currentValue().toString());
                    }
                    assertNotEquals("0", d.statementId());
                }
                if (d.variable() instanceof FieldReference fr && "writer".equals(fr.fieldInfo.name)) {
                    if ("tab".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            fail();
                        }
                        if ("0.0.3".equals(d.statementId())) {
                            String expected = d.iteration() < 3 ? "<f:tab.writer>" : "instance type Writer";
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else if ("tabs.peek()".equals(fr.scope.toString())) {
                        if ("0.0.3".equals(d.statementId())) {
                            assertEquals("nullable instance type Writer", d.currentValue().toString());
                        }
                    } else if ("nullable instance type Tab".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            assertEquals("nullable instance type Writer", d.currentValue().toString());
                        }
                    } else if ("scope-tab:0".equals(fr.scope.toString())) {
                        if ("0".equals(d.statementId())) {
                            String expected = switch (d.iteration()) {
                                case 0 -> "tabs.isEmpty()?<f:scope-tab:0.writer>:<dv:scope-tab:0.writer>";
                                case 1, 2 -> "tabs.isEmpty()?instance type Writer:<dv:scope-tab:0.writer>";
                                default -> "instance type Writer";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("scope " + fr.scope);
                }
            }
            if ("handleExceeds".equals(d.methodInfo().name)) {
                if ("exceeds9".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("`lookAhead.exceeds`", d.currentValue().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                    if ("3.1.0".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<v:exceeds9>" : "`lookAhead.exceeds`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                    if ("3.1.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 1 ? "<v:exceeds9>" : "`lookAhead.exceeds`";
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("handleExceeds".equals(d.methodInfo().name)) {
                if ("2".equals(d.statementId())) {
                    assertEquals("CM{state=null!=lookAhead.exceeds();parent=CM{ignore=exceeds9;parent=CM{}}}",
                            d.statementAnalysis().stateData().getConditionManagerForNextStatement().toString());
                }
                if ("3.0.0".equals(d.statementId())) {
                    assertEquals("null!=lookAhead.current()", d.condition().toString());
                }
                if ("3.1.0".equals(d.statementId())) {
                    assertEquals("null==lookAhead.current()", d.condition().toString());
                    assertEquals("null==lookAhead.current()&&null!=lookAhead.exceeds()",
                            d.absoluteState().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("NOT_END".equals(d.fieldInfo().name)) {
                assertDv(d, 8, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
            if ("writer".equals(d.fieldInfo().name) && "Tab".equals(d.fieldInfo().owner.simpleName)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
                assertEquals("new StringWriter()", ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).sortedValuesString());
                assertTrue(d.fieldAnalysis().valuesDelayed().isDone());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("pop".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("swap".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 3, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("writer".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() == 0 ? "<m:writer>" : "tabs.isEmpty()?writer:(tabs.peek()).writer$0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());

                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                assertDv(d.p(0), 1, MultiLevel.NULLABLE_DV, Property.NOT_NULL_PARAMETER);
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                assertDv(d.p(1), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("NewLineDouble".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> {
            if ("Formatter".equals(d.typeInfo().simpleName)) {
                assertEquals("------M-M--", d.delaySequence());
            }
        };

        testSupportAndUtilClasses(List.of(Formatter.class,
                        Forward.class, Lookahead.class, CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                0, 28, new DebugConfiguration.Builder()
                        //     .addEvaluationResultVisitor(evaluationResultVisitor)
                        //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
