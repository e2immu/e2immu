
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
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.output.formatter.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
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

    // the real deal
    @Test
    public void test_1() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if ("6.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 4 ? "<m:pop>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 5, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 5, d.status().isDone());
                    assertEquals(d.iteration() >= 4, d.externalStatus().isDone());
                }
                if ("7.0.0".equals(d.statementId())) {
                    String expected = d.iteration() <= 4 ? "<m:write>" : "<no return value>";
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 5, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 5, d.status().isDone());
                    assertEquals(d.iteration() >= 4, d.externalStatus().isDone());
                    assertTrue(d.iteration() < 6);
                }
                if ("7".equals(d.statementId())) {
                    String expected = switch (d.iteration()) {
                        case 0 -> "!<vl:writeNewLine>";
                        case 1, 2, 3 -> "!(end$5>pos$5?<null-check>?<s:boolean>:!<null-check>&&(<dv:scope-newLineDouble:5.writeNewLine>||!<null-check>):instance type boolean)";
                        default -> "!(end$5>pos$5?(scope-newLineDouble:5.writeNewLine||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`||null==``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`)&&(null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`):instance type boolean)";
                    };
                    assertEquals(expected, d.evaluationResult().value().toString());
                    assertEquals(d.iteration() >= 4, d.evaluationResult().causesOfDelay().isDone());
                    assertEquals(d.iteration() >= 4, d.status().isDone());
                    assertEquals(d.iteration() >= 5, d.externalStatus().isDone());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("write".equals(d.methodInfo().name)) {
                if ("writeNewLine".equals(d.variableName())) {
                    if ("5".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<v:end>><v:pos>?<null-check>?<s:boolean>:!<null-check>&&(<dv:scope-newLineDouble:5.writeNewLine>||!<null-check>):<vl:writeNewLine>";
                            case 1, 2, 3 -> "end$5>pos$5?<null-check>?<s:boolean>:!<null-check>&&(<dv:scope-newLineDouble:5.writeNewLine>||!<null-check>):instance type boolean";
                            default -> "end$5>pos$5?(scope-newLineDouble:5.writeNewLine||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`||null==``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`)&&(null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`||null!=`new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).exceeds`):instance type boolean";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(d.iteration() >= 4, d.currentValue().isDone());
                    }
                    if ("5.0.6.1.0.1.4.0.1".equals(d.statementId())) {
                        String expected = d.iteration() <= 3 ? "<f:newLineDouble.writeNewLine>"
                                : "(`Position.END`==`guide.position`?new NewLineDouble(``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`.endWithNewLine(),`writeNewLineBefore`,false,true):`Position.START`==`guide.position`?new NewLineDouble(``new CurrentExceeds(`currentForwardInfo`.get(),`exceeds`.get()).current`.guide`.startWithNewLine(),false,false,false):new NewLineDouble(true,`writeNewLineBefore`,true,false)).writeNewLine";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(d.iteration() >= 4, d.currentValue().isDone());
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
                        assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "NOT_END".equals(fr.fieldInfo.name)) {
                    if ("5".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("6".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("6.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("pop".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("tab".equals(d.variableName())) {
                    if ("0.0.3".equals(d.statementId())) {
                        String expected = d.iteration() < 2 ? "<m:pop>" : "nullable instance type Tab";
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
                            String expected = d.iteration() < 2 ? "<f:writer>" : "instance type Writer";
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
                                case 0 -> "<f:writer>";
                                case 1 -> "tabs.isEmpty()?instance type Writer:<f:writer>";
                                default -> "instance type Writer";
                            };
                            assertEquals(expected, d.currentValue().toString());
                        }
                    } else fail("scope " + fr.scope);
                }
            }
            if ("handleExceeds".equals(d.methodInfo().name)) {
                if ("exceeds".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        assertEquals("`lookAhead.exceeds`", d.currentValue().toString());
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.NOT_NULL_EXPRESSION);
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("handleExceeds".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    assertEquals("null!=`lookAhead.current`", d.condition().toString());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("NOT_END".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
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
                assertDv(d.p(0), 4, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("swap".equals(d.methodInfo().name) && "Formatter".equals(d.methodInfo().typeInfo.simpleName)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(2), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
            if ("writer".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().methodInspection.get().isStatic());
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d.p(1), 3, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() == 0 ? "<m:writer>" : "/*inline writer*/tabs.isEmpty()?writer:(tabs.peek()).writer$0";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("NewLineDouble".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };

        testSupportAndUtilClasses(List.of(Formatter.class,
                        Forward.class, Lookahead.class, CurrentExceeds.class, ForwardInfo.class, GuideOnStack.class,
                        ElementarySpace.class, OutputElement.class, FormattingOptions.class,
                        TypeName.class, Qualifier.class, Guide.class, Symbol.class, Space.class, Split.class),
                0, 26, new DebugConfiguration.Builder()
                      //  .addEvaluationResultVisitor(evaluationResultVisitor)
                      //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                     //   .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                     //   .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                      //  .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                      //  .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
