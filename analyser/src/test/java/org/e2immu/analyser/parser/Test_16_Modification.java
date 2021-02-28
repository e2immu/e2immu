package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("org.e2immu.analyser.testexample.Modification_0.set1".equals(d.variableName())) {
                    Assert.assertTrue(d.variableInfoContainer().hasEvaluation() && !d.variableInfoContainer().hasMerge());
                    Assert.assertTrue(d.variableInfo().isRead());
                    int expectContextModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectContextModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectModified, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    String expectLinked = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? "<field:org.e2immu.analyser.testexample.Modification_0.set1>" : "instance type HashSet<String>";
                    Assert.assertEquals(expectValue, d.currentValue().debugOutput());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set1".equals(d.fieldInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expect, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            Assert.assertEquals(AnnotationMode.DEFENSIVE, set.typeInspection.get().annotationMode());
            MethodInfo add = set.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

            MethodInfo size = set.findUniqueMethod("size", 0);
            Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(Set.class);
            Assert.assertEquals(Level.TRUE, hashSet.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
        };

        testClass("Modification_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("size".equals(d.methodInfo().name) && "Modification_1".equals(d.methodInfo().typeInfo.simpleName)) {
                int expect = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expect, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("getFirst".equals(d.methodInfo().name) && d.iteration() > 0) {
                Assert.assertNotNull(d.haveError(Message.UNUSED_PARAMETER));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set2")) {
                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                if (d.iteration() == 0) {
                    Assert.assertEquals(Level.DELAY, modified);
                } else {
                    Assert.assertEquals(Level.FALSE, modified);
                }
            }
        };

        testClass("Modification_1", 0, 1, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test2() throws IOException {
        final String GET_FIRST_VALUE = "set2ter.isEmpty()?\"\":(instance type Stream<E>).findAny().orElseThrow()";
        final String GET_FIRST_VALUE_DELAYED = "<m:isEmpty>?\"\":<m:orElseThrow>";
        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getFirst".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;
                Assert.assertEquals(expect, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("org.e2immu.analyser.testexample.Modification_2.Example2ter.getFirst(String)".equals(d.variableName())) {
                String expect = d.iteration() == 0 ? GET_FIRST_VALUE_DELAYED : GET_FIRST_VALUE;

                Assert.assertEquals(expect, d.currentValue().toString());
                Assert.assertNotSame(LinkedVariables.DELAY, d.currentValue().linkedVariables(d.evaluationContext()));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set2ter")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                Assert.assertEquals(Level.TRUE, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectModified = iteration == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, modified);
            }
            if (name.equals("set2bis")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                int expectFinal = Level.FALSE;
                Assert.assertEquals(expectFinal, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                int expectModified = Level.TRUE;
                Assert.assertEquals(expectModified, modified);
            }
        };

        testClass("Modification_2", 1, 2, new DebugConfiguration.Builder()
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());

    }

    @Test
    public void test3() throws IOException {

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertTrue(d.evaluationResult().someValueWasDelayed());
                } else {
                    Assert.assertEquals("set3.add(v)", d.evaluationResult().value().toString());
                    int v = d.evaluationResult().changeData().entrySet().stream()
                            .filter(e -> e.getKey().fullyQualifiedName().equals("local3"))
                            .map(Map.Entry::getValue)
                            .mapToInt(ecd -> ecd.properties().get(VariableProperty.CONTEXT_MODIFIED))
                            .findFirst().orElseThrow();
                    Assert.assertEquals(Level.TRUE, v);
                }
            }
        };
        final String INSTANCE_TYPE_HASH_SET = "instance type HashSet<String>";
        final String SET3_DELAYED = "<f:set3>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "local3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead());
                    Assert.assertEquals("this.set3", d.variableInfo().getStaticallyAssignedVariables().toString());

                    if (d.iteration() == 0) {
                        Assert.assertTrue(d.currentValueIsDelayed());
                    } else {
                        Assert.assertTrue(d.variableInfo().getValue() instanceof VariableExpression);
                        VariableExpression variableValue = (VariableExpression) d.currentValue();
                        Assert.assertTrue(variableValue.variable() instanceof FieldReference);
                        Assert.assertEquals("set3", d.currentValue().toString());
                    }
                    if (d.iteration() > 0) {
                        Assert.assertEquals("this.set3", d.variableInfo().getLinkedVariables().toString());
                    } else {
                        Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                    }
                }
                if ("1".equals(d.statementId())) {
                    //  the READ is written at level 1
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertTrue(d.variableInfo().isRead());
                    Assert.assertEquals("this.set3", d.variableInfo().getStaticallyAssignedVariables().toString());

                    Assert.assertTrue(d.variableInfo().getReadId().compareTo(d.variableInfo().getAssignmentId()) > 0);
                    if (d.iteration() == 0) {
                        // there is a variable info at levels 0 and 3
                        Assert.assertTrue(d.currentValueIsDelayed());
                        Assert.assertFalse(d.variableInfoContainer().isInitial());
                    } else {
                        // there is a variable info in level 1, copied from level 1 in statement 0
                        // problem is that there is one in level 3 already, with a NO_VALUE
                        VariableInfo vi1 = d.variableInfoContainer().current();
                        Assert.assertEquals("instance type HashSet<String>", vi1.getValue().toString());
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    }
                    if (d.iteration() > 0) {
                        Assert.assertEquals("this.set3", d.variableInfo().getLinkedVariables().toString());
                    } else {
                        Assert.assertSame("It: " + d.iteration(), LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                    }
                }
            }
            if ("add3".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_3.set3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead());
                    String expectLv = d.iteration() == 0 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLv, d.variableInfo().getLinkedVariables().toString());
                    String expectValue = d.iteration() == 0 ? SET3_DELAYED : INSTANCE_TYPE_HASH_SET;
                    Assert.assertEquals(expectValue, d.variableInfo().getValue().toString());
                    int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() > 1) {
                    Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set3")) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertEquals(1, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).values.get().expressions().length);
                if (d.iteration() > 0) {
                    Assert.assertEquals(INSTANCE_TYPE_HASH_SET, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.iteration() > 1) {
                        Assert.assertEquals(Level.TRUE,
                                d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                    }
                }
            }
        };


        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addInSet = set.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, addInSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(HashSet.class);
            MethodInfo addInHashSet = hashSet.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, addInHashSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_METHOD));
        };

        testClass("Modification_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    /*
    What happens in each iteration?
    IT 0: READ, ASSIGNED; set4 FINAL in field analyser, gets value and linked variables
    IT 1: set4 gets a value in add4; set4 linked to in4
    IT 2: set4 MODIFIED, NOT_NULL;
     */

    @Test
    public void test4() throws IOException {
        final String SET4 = "org.e2immu.analyser.testexample.Modification_4.set4";
        final String SET4_DELAYED = "<f:set4>";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && SET4.equals(d.variableName())) {
                if (d.iteration() == 0) {
                    Assert.assertTrue(d.currentValueIsDelayed());
                } else {
                    Assert.assertEquals("0-E", d.variableInfo().getReadId());
                    Assert.assertEquals("instance type Set<String>", d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    // via statical assignments
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("add4".equals(d.methodInfo().name) && "local4".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    String expect = d.iteration() == 0 ? SET4_DELAYED : "set4";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    String expect = d.iteration() == 0 ? "<f:set4>" : "instance type Set<String>";
                    Assert.assertEquals(expect, d.currentValue().toString());
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && SET4.equals(d.variableName()) && "0".equals(d.statementId())) {
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectEnn, d.getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectMv = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMv, d.getProperty(VariableProperty.MODIFIED_VARIABLE));

                Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                Assert.assertEquals("in4", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.NULL_POINTER_EXCEPTION));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set4")) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                        d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                Assert.assertEquals("in4", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                Assert.assertEquals("in4", d.fieldAnalysis().getLinkedVariables().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                ParameterAnalysis in4 = d.parameterAnalyses().get(0);
                int expectNN = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNN, in4.getProperty(VariableProperty.EXTERNAL_NOT_NULL));
                int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, in4.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("add4".equals(name)) {
                FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                if (iteration >= 1) {
                    VariableInfo vi = d.getFieldAsVariable(set4);
                    assert vi != null;
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
                }
            }
        };

        testClass("Modification_4", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p &&
                    "in5".equals(p.name) && "0".equals(d.statementId())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
            if ("Modification_5".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_5.set5".equals(d.variableName()) && "0".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() <= 1 ? Level.DELAY : Level.TRUE, d.getProperty(VariableProperty.FINAL));
                String expectValue = "new HashSet<>(in5)/*this.size()==in5.size()*/";
                Assert.assertEquals(expectValue, d.currentValue().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name)) {
                ParameterAnalysis in5 = d.parameterAnalyses().get(0);
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMom, in5.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Modification_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }


    @Test
    public void test6() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_6";
        final String SET6 = TYPE + ".set6";
        final String IN6 = TYPE + ".Modification_6(Set<String>):0:in6";
        final String EXAMPLE6_SET6 = TYPE + ".set6#" + TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String EXAMPLE6 = TYPE + ".add6(Modification_6,Set<String>):0:example6";
        final String VALUES6 = TYPE + ".add6(Modification_6,Set<String>):1:values6";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("add6".equals(d.methodInfo().name)) {
                if (VALUES6.equals(d.variableName())) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }

                if (EXAMPLE6.equals(d.variableName())) {
                    String expectValue = d.iteration() == 0 ?
                            "<parameter:org.e2immu.analyser.testexample.Modification_6.add6(Modification_6,Set<String>):0:example6>" :
                            "nullable? instance type Modification_6";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (EXAMPLE6_SET6.equals(d.variableName())) {
                    if (d.iteration() > 0)
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    if (d.iteration() > 1)
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (SET6.equals(d.variableName()) && "0".equals(d.statementId()) && d.iteration() == 3) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                }
                if (IN6.equals(d.variableName()) && "0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED_VARIABLE));
                    }
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set6")) {
                if (iteration == 0) {
                    Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                }
                if (iteration >= 1) {
                    Assert.assertEquals("in6", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    Assert.assertEquals("in6", d.fieldAnalysis().getLinkedVariables().toString());
                }
                if (iteration >= 2) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                            d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);
                    Assert.assertEquals(Level.TRUE, modified);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Example6".equals(name)) {
                ParameterAnalysis in6 = d.parameterAnalyses().get(0);

                int expectIn6NotNull = iteration < 2 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectIn6NotNull, in6.getProperty(VariableProperty.NOT_NULL_EXPRESSION));

                int expectIn6Modified = iteration < 2 ? Level.FALSE : Level.TRUE;
                Assert.assertEquals(expectIn6Modified, in6.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("add6".equals(name)) {
                ParameterAnalysis values6 = d.parameterAnalyses().get(1);
                int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                Assert.assertEquals(expectNnp, values6.getProperty(VariableProperty.NOT_NULL_PARAMETER));

                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);
                VariableInfo set6VariableInfo = d.getFieldAsVariable(set6);
                Assert.assertNull(set6VariableInfo); // this variable does not occur!

                List<VariableInfo> vis = d.methodAnalysis().getLastStatement()
                        .latestInfoOfVariablesReferringTo(set6, false);
                Assert.assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    Assert.assertEquals(Level.TRUE, vi.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo p0 = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.parameterAnalysis.get()
                    .getProperty(VariableProperty.NOT_NULL_PARAMETER));
        };

        testClass("Modification_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test7() throws IOException {
        testClass("Modification_7", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test8() throws IOException {
        testClass("Modification_8", 0, 0, new DebugConfiguration.Builder()
                .build());
    }


    @Test
    public void test9() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_9";
        final String S2 = TYPE + ".s2";
        final String ADD = TYPE + ".add(String)";

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() > 0,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals(d.iteration() >= 1,
                            d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "theSet".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("2".equals(d.statementId())) {
                    int expectCm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectCm, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if (d.iteration() == 0) {
                    Assert.assertSame(LinkedVariables.DELAY, d.variableInfo().getLinkedVariables());
                } else {
                    Assert.assertEquals("this.s2", d.variableInfo().getLinkedVariables().toString());
                }
                if (d.statementId().equals("1") && d.iteration() > 0) {
                    Assert.assertEquals("s2", d.currentValue().toString());
                }
                if (d.statementId().equals("2") && d.iteration() > 0) {
                    Assert.assertEquals("instance type HashSet<String>", d.currentValue().toString());
                }
            }
            if ("add".equals(d.methodInfo().name) && S2.equals(d.variableName())) {
                if (d.iteration() > 0) {
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
                if (("2".equals(d.statementId()) || "3".equals(d.statementId())) && d.iteration() > 1) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
                if ("3".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName) && d.iteration() > 1) {
                Assert.assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("s2".equals(d.fieldInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
                Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
                Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();

            if ("Modification_10".equals(d.methodInfo().name)) {
                ParameterAnalysis list = d.parameterAnalyses().get(0);
                ParameterAnalysis set3 = d.parameterAnalyses().get(1);

                if (iteration == 0) {
                    Assert.assertFalse(list.isAssignedToFieldDelaysResolved());
                } else {
                    Assert.assertEquals("{c1=LINKED, s0=NO, c0=ASSIGNED, s1=NO, l1=NO, l2=NO, l0=NO}", list.getAssignedToField().toString());
                }
                if (iteration >= 2) {
                    Assert.assertEquals(0, list.getProperty(VariableProperty.MODIFIED_VARIABLE));
                    Assert.assertFalse(set3.getAssignedToField().isEmpty());
                    // Assert.assertEquals(1, set3.getProperty(VariableProperty.MODIFIED)); // directly assigned to s0
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            FieldInfo fieldInfo = d.fieldInfo();
            if ("c0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    Assert.assertEquals(0, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    Assert.assertEquals(1, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));

        };

        testClass("Modification_10", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

    @Test
    public void test11() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_11";
        final String SET_IN_C1 = TYPE + ".C1.set";
        final String SET_IN_C1_DELAYED = "<f:set>";
        final String S2 = TYPE + ".s2";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("example1".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                EvaluationResult.ChangeData s2 = d.findValueChange(S2);
                int expectCnn = d.iteration() <= 1 ? Level.TRUE : Level.DELAY;
                Assert.assertEquals(expectCnn, s2.getProperty(VariableProperty.CONTEXT_NOT_NULL_DELAY));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    // not a direct assignment!
                    Assert.assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                    Assert.assertEquals("setC", d.variableInfo().getLinkedVariables().toString());
                    Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo setC && "setC".equals(setC.name)) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("getSet".equals(d.methodInfo().name) && SET_IN_C1.equals(d.variableName())) {
                int expectNne = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                Assert.assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                // not a direct assignment!
                Assert.assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                String expectLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
            }

            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                if (SET_IN_C1.equals(d.variableName())) {
                    String expectValue = d.iteration() <= 1 ? SET_IN_C1_DELAYED : "instance type Set<String>";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                    int expectNN = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                    Assert.assertEquals(expectNN, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                    int expectCNN = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    Assert.assertEquals(expectCNN, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                    String expectLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
                if (d.variable() instanceof ParameterInfo s && "string".equals(s.name)) {
                    String expectLinked = d.iteration() <= 1 ? LinkedVariables.DELAY_STRING : "";
                    Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                }
            }


            if ("example1".equals(d.methodInfo().name)) {
                if (S2.equals(d.variableName()) && "0".equals(d.statementId())) {
                    int expectCnn = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                    Assert.assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));

                    Assert.assertEquals("", d.variableInfo().getStaticallyAssignedVariables().toString());
                }
                if ("c".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.NULLABLE, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                    if ("2".equals(d.statementId())) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                        String expectLinked = d.iteration() <= 2 ? LinkedVariables.DELAY_STRING : "this.s2";
                        Assert.assertEquals(expectLinked, d.variableInfo().getLinkedVariables().toString());
                    }
                }
                if (d.variable() instanceof ReturnVariable && "2".equals(d.statementId())) {
                    String expectValue = d.iteration() <= 1 ? "<m:addAll>" : "c.set.addAll(localD.set)";
                    Assert.assertEquals(expectValue, d.currentValue().toString());
                }
            }
            if ("addAll".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi1 && "d".equals(pi1.name)) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                            d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
                if (d.variable() instanceof ParameterInfo pi0 && "c".equals(pi0.name)) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "C1".equals(d.methodInfo().typeInfo.simpleName)) {
                Assert.assertEquals(d.iteration() >= 2,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
            if ("example1".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
                Assert.assertEquals(d.iteration() >= 3,
                        d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set".equals(d.fieldInfo().name)) {
                Assert.assertEquals("setC", d.fieldAnalysis().getLinkedVariables().toString());
                Assert.assertEquals("setC/*@NotNull*/", d.fieldAnalysis().getEffectivelyFinalValue().debugOutput());
                // the field analyser sees addAll being used on set in the method addAllOnC
                int expectEnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                Assert.assertEquals(expectEnn, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_NOT_NULL));

                int expectMm = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMm, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("C1".equals(d.methodInfo().name)) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectNnp = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                Assert.assertEquals(expectNnp, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMv = d.iteration() <= 2 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
            if ("addAll".equals(d.methodInfo().name)) {
                ParameterAnalysis p1 = d.parameterAnalyses().get(1);
                int expectNnp1 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                Assert.assertEquals(expectNnp1, p1.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMp1 = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMp1, p1.getProperty(VariableProperty.MODIFIED_VARIABLE));

                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectNnp0 = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                Assert.assertEquals(expectNnp0, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
                int expectMp0 = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMp0, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .build());
    }


    @Test
    public void test12() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.Modification_12";
        final String CHILD_CLASS_SUPER = TYPE + ".ChildClass.super";
        final String PARENT_CLASS_THIS = TYPE + ".ParentClass.this";
        final String PARENT_CLASS_SET = TYPE + ".ParentClass.set";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && PARENT_CLASS_SET.equals(d.variableName())) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId()) && d.variable() instanceof This) {
                Assert.assertEquals(PARENT_CLASS_THIS, d.variableName());
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }

            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    Assert.assertEquals(CHILD_CLASS_SUPER, d.variableName());
                    int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.TRUE;
                    // we have to wait for clearAndLog in ParentClass, which is analysed AFTER this one
                    Assert.assertEquals(expectModified, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
                }
            }

            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof This thisVar && thisVar.writeSuper) {
                    Assert.assertEquals(CHILD_CLASS_SUPER, d.variableName());
                    if (d.iteration() > 1) Assert.assertEquals(Level.TRUE,
                            d.getProperty(VariableProperty.MODIFIED_VARIABLE));
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("clear".equals(d.methodInfo().name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                Expression scope = ((MethodCall) ((ExpressionAsStatement) d.statementAnalysis().statement).expression).object;
                VariableExpression variableExpression = (VariableExpression) scope;
                This t = (This) variableExpression.variable();
                Assert.assertTrue(t.explicitlyWriteType);
                Assert.assertTrue(t.writeSuper);
            }
            // we make sure that super.clearAndLog refers to the method in ParentClass
            if ("clearAndLog".equals(d.methodInfo().name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)
                    && "0".equals(d.statementId())) {
                if (d.statementAnalysis().statement instanceof ExpressionAsStatement expressionAsStatement) {
                    Expression expression = expressionAsStatement.expression;
                    if (expression instanceof MethodCall methodCall) {
                        Assert.assertEquals("org.e2immu.analyser.testexample.Modification_12.ParentClass.clearAndLog()",
                                methodCall.methodInfo.fullyQualifiedName);
                    } else Assert.fail();
                } else Assert.fail();
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            String name = d.methodInfo().name;
            if ("clear".equals(name) && "ParentClass".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("clearAndAdd".equals(name) && "ChildClass".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("clear".equals(name) && "InnerOfChild".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.getThisAsVariable().getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };


        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            TypeInfo typeInfo = d.typeInfo();
            if ("ParentClass".equals(typeInfo.simpleName)) {
                Assert.assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("ChildClass".equals(typeInfo.simpleName)) {
                Assert.assertEquals("Modification_12", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("InnerOfChild".equals(typeInfo.simpleName)) {
                Assert.assertEquals("ChildClass", typeInfo.packageNameOrEnclosingType.getRight().simpleName);
            }
            if ("ModifiedThis".equals(typeInfo.simpleName)) {
                Assert.assertEquals("org.e2immu.analyser.testexample", typeInfo.packageNameOrEnclosingType.getLeft());
            }
        };

        testClass("Modification_12", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }


    @Test
    public void test13() throws IOException {
        final String INNER_THIS = "org.e2immu.analyser.testexample.Modification_13.Inner.this";
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name) && INNER_THIS.equals(d.variableName())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.CONTEXT_MODIFIED));
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("clearIfExceeds".equals(d.methodInfo().name)) {
                int expectModified = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };
        testClass("Modification_13", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test14() throws IOException {
        testClass("Modification_14", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test15() throws IOException {
        testClass("Modification_15", 1, 0, new DebugConfiguration.Builder()
                .build());
    }
}
