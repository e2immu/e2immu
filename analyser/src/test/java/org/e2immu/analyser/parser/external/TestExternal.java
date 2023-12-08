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

package org.e2immu.analyser.parser.external;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class TestExternal extends CommonTestRunner {

    public TestExternal() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                // 'start' stays out of the merge in 2.0.0
                if (d.variable() instanceof ParameterInfo pi && "start".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0".equals(d.statementId())) {
                        VariableInfo initial = d.variableInfoContainer().getPreviousOrInitial();
                        assertLinked(d, initial.getLinkedVariables(), it(0, "j:0"));
                        assertTrue(d.variableInfoContainer().hasMerge());
                        assertLinked(d, it(0, "j:0"));
                    }
                    if ("2.0.0.0.2".equals(d.statementId())) {
                        assertEquals("ExpressionAsStatement{class org.e2immu.analyser.model.expression.Assignment: j=i+1}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                    if ("2.0.0.0.3".equals(d.statementId()) || "2.0.0.0.4".equals(d.statementId())) {
                        assertEquals("BreakStatement{goTo=null}",
                                d.context().evaluationContext().getCurrentStatement().statement().toString());
                        assertLinked(d, it0("buff:-1,buff[i]:-1,endPos:-1,i:-1,j:-1"), it(1, ""));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("External_0", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    // starting error: Property context-not-null, current nullable:1, new not_null:5 overwriting property value CLV
    @Test
    public void test_1() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0"));
                    }
                    if ("3".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it(0, "f:0,h:2"));
                    }
                }
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);
                    }
                }
            }
            if ("report".equals(d.methodInfo().name)) {
                if ("f".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 2, DV.TRUE_DV, Property.CNN_TRAVELS_TO_PRECONDITION);

                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "filter:0,h:2"));
                    }
                }
                if ("p".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, MultiLevel.NULLABLE_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "process:0"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "filter".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "f:0,h:2"));
                    }
                }
                if (d.variable() instanceof ParameterInfo pi && "process".equals(pi.name)) {
                    if ("1".equals(d.statementId())) {
                        assertLinked(d, it0("NOT_YET_SET"), it(1, "p:0"));
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        testClass("External_1", 1, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }


    // starting error: java.lang.IllegalStateException: Cannot change statically assigned for variable org.e2immu.analyser.parser.external.testexample.External_3.postAccumulate(org.xml.sax.XMLFilter,org.e2immu.analyser.parser.external.testexample.External_3.ProcessElement):1:process
    //old: p:-1
    //new: p:0
    @Test
    public void test_2() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("External_2", 1, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Caught exception analysing statement 0.0.0.0.0 of org.e2immu.analyser.parser.external.testexample.External_3.startDocument()
    // at org.e2immu.analyser.analysis.impl.StateDataImpl.internalAllDoneCheck(StateDataImpl.java:78)
    @Test
    public void test_3() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("------", d.delaySequence());

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("startDocument".equals(d.methodInfo().name)) {
                if ("0.0.0.0.0".equals(d.statementId())) {
                    String expected = d.iteration() < 4 ? "!instance 0.0.0 type boolean&&<null-check>"
                            : "!instance 0.0.0 type boolean&&null==document$0";
                    assertEquals(expected, d.statementAnalysis().stateData().getAbsoluteState().toString());
                }
            }
        };

        testClass("External_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: java.lang.UnsupportedOperationException: ? have index 11, looking for 9
    // in SASubBlocks:291, Caught exception in method analyser: org.e2immu.analyser.parser.external.testexample.External_4.encode(byte[])
    // then: parameter should not be assigned to (b in checkLine)
    @Test
    public void test_4() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("encode".equals(d.methodInfo().name)) {
                int dot = d.statementId().indexOf('.');
                String firstPart = dot < 0 ? d.statementId() : d.statementId().substring(0, dot);
                assertEquals(2, firstPart.length());
            }
        };
        testClass("External_4", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: java.lang.UnsupportedOperationException: ? no delays, and initial return expression even
    // though return statements are reachable
    @Test
    public void test_5() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-", d.delaySequence());

        testClass("External_5", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Changing value of @Independent from not_involved:0 to dependent:1
    @Test
    public void test_6() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-------", d.delaySequence());

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("startDocument".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "currentNode".equals(fr.fieldInfo().name)) {
                    if ("1".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0 -> "<f:document>";
                            case 1, 2, 3 -> "<wrapped:document>";
                            default ->
                                    "null==document$0?External_6.documentBuilder.newDocument():null==(nullable instance type Document).getDocumentElement()?nullable instance type Document:instance 0.1.0.0.0 type Document";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertDv(d, 4, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                    }
                }
            }
        };
        testClass("External_6", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: overwriting value: Property context-not-null, current nullable:1, new not_null:5
    // problem in explicit constructor invocation this(...)
    @Test
    public void test_7() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (d.methodInfo().isConstructor() && n == 1) {
                if (d.variable() instanceof FieldReference fr && "K".equals(fr.fieldInfo().name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if (d.methodInfo().isConstructor() && n == 2) {
                if (d.variable() instanceof ParameterInfo pi && "key".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    }
                }
            }
        };

        testClass("External_7", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting error: Changing value of @Independent from dependent:1 to independent:21
    // runs OK when 'else' keyword is removed on line 15
    @Test
    public void test_8A() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("convertToLongArray".equals(d.methodInfo().name)) {
                if ("0.1.0.0.1.0.1".equals(d.statementId())) {
                    ChangeData cd = d.findValueChange("av-18:32");
                    String expected = d.iteration() == 0 ? "<v:av-18:32[i]>" : "source/*(Object[])*/";
                    assertEquals(expected, cd.value().toString());
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("convertToLongArray".equals(d.methodInfo().name)) {
                if ("value".equals(d.variableName())) {
                    if ("0.1.0.0.1.0.0".equals(d.statementId())) {
                        assertEquals("Type Object", d.variable().parameterizedType().toString());
                        String expected = d.iteration() == 0 ? "<v:av-18:32[i]>"
                                : "source/*(Object[])*/[i$0.1.0.0.1]$0.1.0.0.1.0.0";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("av-18:32".equals(d.variableName())) {
                    if ("0.1.0.0.1.0.1".equals(d.statementId())) {
                        assertEquals("Type Object[]", d.variable().parameterizedType().toString());
                        assertDv(d, MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, Property.IMMUTABLE);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("convertToLong".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<m:convertToLong>"
                        : "/*inline convertToLong*/source instanceof Number?source/*(Number)*/.longValue():source instanceof String?Long.parseLong(source/*(String)*/):<return value>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
                assertEquals("Type long", d.methodAnalysis().getSingleReturnValue().returnType().toString());

                assertEquals("source instanceof Number||source instanceof String",
                        d.methodAnalysis().getPrecondition().expression().toString());
            }
        };
        testClass("External_8A", 0, 1, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // variant: even with removed keyword, it fails; here, we have introduced a Pattern variable list
    @Test
    public void test_8B() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S-", d.delaySequence());

        testClass("External_8B", 0, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // variant: when using 2 pattern variables, it does not fail
    @Test
    public void test_8C() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S-", d.delaySequence());

        testClass("External_8C", 0, 1, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // starting problem:  Overwriting final value: old: false, new obj instanceof External_9&&Arrays.asList(excludeElements$0).equals(Arrays.asList(obj/*(Externa
    @Test
    public void test_9() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("equals".equals(d.methodInfo().name) && "External_9".equals(d.methodInfo().typeInfo.simpleName)) {
                if ("e1".equals(d.variableName())) {
                    if ("1".equals(d.statementId())) {
                        String expected = d.iteration() < 3 ? "<m:equals>"
                                : "Arrays.asList(s1$0).equals(Arrays.asList(obj/*(External_9)*/.s1$0))";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if ("e2".equals(d.variableName())) {
                    if ("2".equals(d.statementId())) {
                        String expected = d.iteration() < 3 ? "<m:equals>"
                                : "Arrays.asList(s2$0).equals(Arrays.asList(obj/*(External_9)*/.s2$0))";
                        assertEquals(expected, d.currentValue().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable) {
                    if ("3".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 ->
                                    "obj instanceof External_9&&(<m:equals>||<return value>)&&(<m:equals>||<return value>)";
                            case 2 -> "<wrapped:return equals>";
                            default ->
                                    "obj instanceof External_9&&(Arrays.asList(s1$0).equals(Arrays.asList(obj/*(External_9)*/.s1$0))||<return value>)&&(Arrays.asList(s2$0).equals(Arrays.asList(obj/*(External_9)*/.s2$0))||<return value>)";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(d.iteration() >= 3, d.currentValue().isDone());
                    }
                    if ("4".equals(d.statementId())) {
                        String expected = switch (d.iteration()) {
                            case 0, 1 -> "<instanceOf:External_9>&&<m:equals>&&<m:equals>";
                            case 2 -> "<wrapped:return equals>";
                            default ->
                                    "obj instanceof External_9&&Arrays.asList(s1$0).equals(Arrays.asList(obj/*(External_9)*/.s1$0))&&Arrays.asList(s2$0).equals(Arrays.asList(obj/*(External_9)*/.s2$0))";
                        };
                        assertEquals(expected, d.currentValue().toString());
                        assertEquals(d.iteration() >= 3, d.currentValue().isDone());
                    }
                }
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("------", d.delaySequence());

        // public variable fields: 2 errors
        testClass("External_9", 2, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // stack overflow in ConditionManagerImpl.absoluteState ~ isNotNull0
    @Test
    public void test_10() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---", d.delaySequence());

        testClass("External_10", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // problem in InequalityHelper
    @Test
    public void test_11() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("---S-", d.delaySequence());

        testClass("External_11", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // unsupported operation exception in Assignment
    @Test
    public void test_12() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----", d.delaySequence());

        testClass("External_12", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // Problem 1: change of LVs ~ LV comes before value
    // Problem 2: incorrect modification ~ https://github.com/e2immu/e2immu/issues/61
    //   delay breaking of modification in loops
    @Test
    public void test_13() throws IOException {
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("m2".equals(d.methodInfo().name)) {
                if ("0.0.1.0.0".equals(d.statementId())) {
                    // nowhere do we encounter a CM
                    assertTrue(d.evaluationResult().changeData().values().stream()
                            .noneMatch(cd -> cd.properties().getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV)
                                    .valueIsTrue()));
                }
            }
        };
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("m1".equals(d.methodInfo().name)) {
                if ("e".equals(d.variableName())) {
                    if ("1.0.2".equals(d.statementId())) {
                        assertCurrentValue(d, 5, "");
                        assertLinked(d, it0("l:-1,la:-1,map:-1,result:-1,value:-1"),
                                it(1, 3, "la:-1,map:-1,value:-1"),
                                it(4, "map:4"));
                    }
                }
            }
            if ("m2".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "o".equals(pi.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // consequence of delay breaking
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // consequence of delay breaking
                    }
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                    if ("0.0.1.0.1".equals(d.statementId())) {
                        assertLinked(d, it(0, 3, "i:-1,l:-1,la:-1,la[i]:-1,list:-1"),
                                it(4, "list:1"));
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // consequence of delay breaking
                    }
                }
                if ("list".equals(d.variableName())) {
                    if ("0.0.1.0.0".equals(d.statementId())) {
                        VariableInfo prev = d.variableInfoContainer().getPreviousOrInitial();
                        assertFalse(d.variableInfoContainer().isInitial());
                        assertEquals(DV.FALSE_DV, prev.getProperty(Property.CONTEXT_MODIFIED));

                        // BREAK DELAY IN LOOP (SAApply.modifiedInLoop)
                        // assertEquals(d.iteration()>=4, d.variableInfoContainer()
                        //          .propertyOverrides().getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV).valueIsTrue());
                        assertLinked(d, it0("i:-1,l:-1,la:-1,o:-1"),
                                it(1, 3, "i:-1,l:-1,o:-1"),
                                it(4, "o:1"));
                        assertDv(d, 4, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        // IMPROVE, see https://github.com/e2immu/e2immu/issues/61
                    }
                    if ("0.0.0".equals(d.statementId())) {
                        assertCurrentValue(d, 0, "o/*(List<?>)*/");
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        VariableInfo eval = d.variableInfoContainer().best(Stage.EVALUATION);
                        if (d.iteration() >= 4) {
                            // consequence of delay breaking
                            assertEquals("instance 0.0.1 type List<?>/*@Identity*/", eval.getValue().toString());
                        }
                        assertCurrentValue(d, 4, "-1+list$0.0.1.size()>=instance 0.0.1 type int?instance 0.0.1 type List<?>/*@Identity*/:o/*(List<?>)*/");
                    }
                }

                if ("la".equals(d.variableName())) {
                    if ("0.0.1.0.1".equals(d.statementId())) {
                        assertLinked(d, it(0, 3, "i:-1,l:-1,la[i]:-1,list:-1,o:-1"),
                                it(4, "")); // independent of list, o!
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("la[i]".equals(d.variableName())) {
                    if ("0.0.1.0.1".equals(d.statementId())) {
                        assertLinked(d, it(0, 3, "i:-1,l:0,la:-1,list:-1,o:-1"),
                                it(4, "l:0,la:3")); // independent of list, o!
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
                if ("l".equals(d.variableName())) {
                    if ("0.0.1.0.1".equals(d.statementId())) {
                        assertLinked(d, it(0, 3, "i:-1,la:-1,la[i]:0,list:-1,o:-1"),
                                it(4, "la:3,la[i]:0")); // independent of list, o!
                        assertDv(d, 4, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("m2".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 5, DV.TRUE_DV, Property.MODIFIED_VARIABLE); // consequence of delay breaking
            }

            if ("m3".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
                String expected = d.iteration() == 0 ? "<m:m3>"
                        : "/*inline m3*/o instanceof Number?o/*(Number)*/.longValue():<return value>";
                assertEquals(expected, d.methodAnalysis().getSingleReturnValue().toString());
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        // ERROR: change of LV in 1.0.2 in m1
        testClass("External_13", 1, 0, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // Changing value of external-not-null from not_involved:0 to nullable:1, variable t, line 8
    @Test
    public void test_14() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo().name)) {
                    if ("0.0.0.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    // as soon as there is an assignment, not-involved is correct!
                    if ("0.0.0.0.0.0.1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    // compared to 14B, there is no earlier presence of 't' at the moment
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("0".equals(d.statementId())) {
                        VariableInfo init = d.variableInfoContainer().getPreviousOrInitial();
                        assertTrue(d.variableInfoContainer().isInitial());
                        String enn = switch (d.iteration()) {
                            case 0 -> "ext_not_null@Field_t";
                            case 1 ->
                                    "initial:a[0]@Method_method_0.0.0.0.0-C;initial:a[1]@Method_method_0.0.0.0.0-C;initial:s@Method_method_0.0.0.0.0-E;initial:s[0]@Method_method_0.0.0.0.0-C;initial:s[1]@Method_method_0.0.0.0.0-C;initial@Field_t;values:this.t@Field_t";
                            default -> "nullable:1";
                        };
                        assertEquals(enn, init.getProperty(Property.EXTERNAL_NOT_NULL).toString());
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("External_14", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    @Test
    public void test_14B() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("method".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo().name)) {
                    if ("0.0.0.0.0".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("0.0.0.0.1.0.0".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    // as soon as there is an assignment, not-involved is correct!
                    if ("0.0.0.0.1.0.1".equals(d.statementId())) {
                        assertDv(d, 1, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    // however, the assignment is conditional, so we must compromise between the two
                    if ("0.0.0.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("0.0.1".equals(d.statementId())) {
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                    if ("0".equals(d.statementId())) {
                        VariableInfo init = d.variableInfoContainer().getPreviousOrInitial();
                        assertTrue(d.variableInfoContainer().isInitial());
                        String enn = switch (d.iteration()) {
                            case 0 -> "ext_not_null@Field_t";
                            case 1 ->
                                    "initial:a@Method_method_0.0.0.0.0-E;initial:s@Method_method_0.0.0.0.0-E;initial@Field_t;values:this.t@Field_t";
                            default -> "nullable:1";
                        };
                        assertEquals(enn, init.getProperty(Property.EXTERNAL_NOT_NULL).toString());
                        assertDv(d, 2, MultiLevel.NOT_INVOLVED_DV, Property.EXTERNAL_NOT_NULL);
                    }
                }
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertEquals("<variable value>", d.fieldAnalysis().getValue().toString());
                assertDv(d, 1, MultiLevel.NULLABLE_DV, Property.EXTERNAL_NOT_NULL);
            }
        };
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        testClass("External_14B", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // No progress after 15 iterations...
    @Test
    public void test_15() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----SFMTO-SFMTO", d.delaySequence());

        testClass("External_15", 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

    // no primitives in inspection provider
    @Test
    public void test_16() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----", d.delaySequence());

        // ERROR: parameter should not be assigned to
        testClass("External_16", 1, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }
}
