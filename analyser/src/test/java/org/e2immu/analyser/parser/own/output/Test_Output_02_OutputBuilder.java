
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
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Output_02_OutputBuilder extends CommonTestRunner {

    public Test_Output_02_OutputBuilder() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("accumulator".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "separator".equals(pi.name)) {
                    assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                }
            }
            if ("accept".equals(d.methodInfo().name) && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                assertEquals("$2", d.methodInfo().typeInfo.packageNameOrEnclosingType.getRight().simpleName);
                if (d.variable() instanceof ParameterInfo pi && "separator".equals(pi.name)) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial(); // 0.0.0.0 evaluation
                        if (d.iteration() > 0) {
                            assertEquals(DV.FALSE_DV, prev.getProperty(Property.CONTEXT_MODIFIED));
                        }
                        String linked = switch (d.iteration()) {
                            case 0 -> "Space.NONE:-1,a:-1,b:-1,end:-1,guideGenerator:-1,start:-1";
                            case 1 -> "a:-1";
                            default -> "a:3";
                        };
                        assertEquals(linked, d.variableInfo().getLinkedVariables().toString());

                        assertDv(d, 2, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "NONE".equals(fr.fieldInfo().name)) {
                    if ("0".equals(d.statementId())) {
                        assertFalse(d.variableInfoContainer().hasEvaluation());

                        String expected = d.iteration() == 0
                                ? "!<m:notStart>||<m:isEmpty>?instance type Space/*new Space(new ElementarySpace(\"\"),new ElementarySpace(\"\"),instance type Split)*/:<f:NONE>"
                                : "instance type Space/*new Space(new ElementarySpace(\"\"),new ElementarySpace(\"\"),instance type Split)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertLinked(d, it0("a:-1,b:-1,end:-1,guideGenerator:-1,separator:-1,start:-1,this.countMid:-1"),
                                it(1, ""));
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertLinked(d, it0("a:-1,b:-1,end:-1,guideGenerator:-1,separator:-1,start:-1"),
                                it(1, ""));
                    }
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:NONE>"
                                : "instance type Space/*new Space(new ElementarySpace(\"\"),new ElementarySpace(\"\"),instance type Split)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals("", d.variableInfo().getLinkedVariables().toString());
                    }
                    if ("0.0.0.0.2".equals(d.statementId())) {
                        String expected = d.iteration() == 0 ? "<f:NONE>"
                                : "instance type Space/*new Space(new ElementarySpace(\"\"),new ElementarySpace(\"\"),instance type Split)*/";
                        assertEquals(expected, d.currentValue().toString());
                        assertLinked(d, it0("a:-1,b:-1,end:-1,guideGenerator:-1,separator:-1,start:-1"),
                                it(1, ""));
                    }
                }
            }
            if ("joining".equals(d.methodInfo().name)) {
                int n = d.methodInfo().methodInspection.get().getParameters().size();
                if (1 == n) {
                    if (d.variable() instanceof ParameterInfo pi && "separator".equals(pi.name)) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if (d.variable() instanceof ReturnVariable) {
                        String expected = d.iteration() < 4 ? "<m:joining>"
                                : "new Collector<>(){final AtomicInteger countMid=new AtomicInteger();public Supplier<OutputBuilder> supplier(){return OutputBuilder::new;}public BiConsumer<OutputBuilder,OutputBuilder> accumulator(){return (a,b)->{... debugging ...};}public BinaryOperator<OutputBuilder> combiner(){return (a,b)->{... debugging ...};}public Function<OutputBuilder,OutputBuilder> finisher(){return t->{... debugging ...};}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT);}}";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("2.0.0".equals(d.statementId())) { // a.add(separator); add is fluent; the identity is there because "a" is the first parameter of apply
                    String expected = d.iteration() < 2 ? "<m:add>"
                            : "nullable instance type OutputBuilder/*@Identity*//*{L a:0}*//*@NotNull*/";
                    assertEquals(expected, d.statementAnalysis().stateData().valueOfExpressionGet().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                ParameterInfo p0 = d.methodInfo().methodInspection.get().getParameters().get(0);
                assertNotNull(p0.parameterizedType.typeInfo);
                String typeOfParameter = p0.parameterizedType.typeInfo.simpleName;
                if ("OutputBuilder".equals(typeOfParameter)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                    assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                } else if ("OutputElement".equals(typeOfParameter)) {
                    assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                    assertDv(d.p(0), 2, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(0), 2, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);
                } else fail();
            }
            if ("joining".equals(d.methodInfo().name)) {
                int n = d.methodInfo().methodInspection.get().getParameters().size();
                if (n == 1) {
                    assertDv(d.p(0), 6, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                } else if (n == 2) {
                    assertDv(d.p(0), 6, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(1), 6, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                } else if (n == 4) {
                    // separator
                    assertDv(d.p(0), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(1), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(2), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    assertDv(d.p(3), 5, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                    String value = d.iteration() < 5 ? "<m:joining>"
                            : "/*inline joining*/new Collector<>(){final AtomicInteger countMid=new AtomicInteger();public Supplier<OutputBuilder> supplier(){return OutputBuilder::new;}public BiConsumer<OutputBuilder,OutputBuilder> accumulator(){return (a,b)->{... debugging ...};}public BinaryOperator<OutputBuilder> combiner(){return (a,b)->{... debugging ...};}public Function<OutputBuilder,OutputBuilder> finisher(){return t->{... debugging ...};}public Set<Characteristics> characteristics(){return Set.of(Characteristics.CONCURRENT);}}";
                    assertEquals(value, d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("OutputElement".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
            if ("OutputBuilder".equals(d.typeInfo().simpleName)) {
                assertDv(d, 6, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> {
            String s = switch (d.typeInfo().simpleName) {
                case "ElementarySpace" -> "-----";
                case "Space" -> "-----";
                case "FormattingOptions" -> "----";
                case "OutputElement" -> "--";
                case "Qualifier" -> "-";
                case "Guide" -> "------";
                case "TypeName" -> "-------";
                case "OutputBuilder" -> "--------";
                default -> fail(d.typeInfo().simpleName + ": " + d.delaySequence());
            };
            assertEquals(s, d.delaySequence(), d.typeInfo().simpleName);
        };

        testSupportAndUtilClasses(List.of(OutputBuilder.class, OutputElement.class, Qualifier.class,
                        FormattingOptions.class, Guide.class, ElementarySpace.class, Space.class, TypeName.class),
                0, 0, new DebugConfiguration.Builder()
                        //  .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build());
    }

    @Test
    public void testTypeName() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("fullyQualifiedName".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    assertFalse(d.allowBreakDelay());
                }
            }
            if ("write".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expected = d.iteration() <= 4 ? "<m:minimal>" : "this.minimal()";
                    assertEquals(expected, d.currentValue().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("minimal".equals(d.methodInfo().name) && "TypeName".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() <= 4 ? "<m:minimal>"
                        : "switch(required){Required.SIMPLE->simpleName;Required.FQN->fullyQualifiedName;Required.QUALIFIED_FROM_PRIMARY_TYPE->fromPrimaryTypeDownwards;}";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };
        testSupportAndUtilClasses(List.of(FormattingOptions.class, TypeName.class),
                0, 0, new DebugConfiguration.Builder()
                        .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build());
    }

}
