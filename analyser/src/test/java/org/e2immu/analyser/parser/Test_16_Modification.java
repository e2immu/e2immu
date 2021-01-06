package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("org.e2immu.analyser.testexample.Modification_0.set1".equals(d.variableName())) {
                Assert.assertTrue(d.variableInfoContainer().hasEvaluation() && !d.variableInfoContainer().hasMerge());
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertTrue(d.variableInfo().isRead());
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set1".equals(d.fieldInfo().name)) {
                int expect = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expect, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            Assert.assertEquals(AnnotationMode.DEFENSIVE, set.typeInspection.get().annotationMode());
            MethodInfo add = set.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

            MethodInfo size = set.findUniqueMethod("size", 0);
            Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

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
                Assert.assertEquals(expect, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            }
            if ("getFirst".equals(d.methodInfo().name)) {
                Assert.assertNotNull(d.haveError(Message.UNUSED_PARAMETER));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set2")) {
                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
                if (d.iteration() <= 1) {
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

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("getFirst".equals(d.methodInfo().name) && "0".equals(d.statementId())) {
                Assert.assertEquals(GET_FIRST_VALUE, d.evaluationResult().value().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("org.e2immu.analyser.testexample.Modification_2.Example2ter.getFirst(String)".equals(d.variableName())) {
                Assert.assertEquals(GET_FIRST_VALUE, d.currentValue().toString());
                Set<Variable> lv = d.currentValue().linkedVariables(d.evaluationContext());
                Assert.assertNotSame(EvaluationResult.LINKED_VARIABLE_DELAY, lv);
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.fieldInfo().name;
            if (name.equals("set2ter")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                Assert.assertEquals(Level.TRUE, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
                int expectModified = iteration <= 1 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectModified, modified);
            }
            if (name.equals("set2bis")) {
                int effFinal = d.fieldAnalysis().getProperty(VariableProperty.FINAL);
                int expectFinal = iteration == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectFinal, effFinal);

                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
                int expectModified = iteration == 0 ? Level.DELAY : Level.TRUE;
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
        final String SET3_EFV = "new HashSet()";

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertSame(EmptyExpression.NO_VALUE, d.evaluationResult().value());
                } else {
                    Assert.assertEquals("set3.add(v)", d.evaluationResult().value().toString());
                    StatementAnalyser.SetProperty setProperty = d.evaluationResult().getModificationStream()
                            .filter(sam -> sam instanceof StatementAnalyser.SetProperty)
                            .map(sam -> (StatementAnalyser.SetProperty) sam)
                            .filter(sp -> sp.property == VariableProperty.MODIFIED && sp.variable.fullyQualifiedName().equals("local3"))
                            .findFirst().orElseThrow();
                    Assert.assertEquals(Level.TRUE, setProperty.value);
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "local3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertFalse(d.variableInfo().isRead());

                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                    } else {
                        Assert.assertTrue(d.variableInfo().getValue() instanceof VariableExpression);
                        VariableExpression variableValue = (VariableExpression) d.currentValue();
                        Assert.assertTrue(variableValue.variable() instanceof FieldReference);
                        Assert.assertEquals("set3", d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    //  the READ is written at level 1
                    Assert.assertTrue(d.variableInfo().isAssigned());
                    Assert.assertTrue(d.variableInfo().isRead());
                    Assert.assertTrue(d.variableInfo().getReadId().compareTo(d.variableInfo().getAssignmentId()) > 0);
                    if (d.iteration() == 0) {
                        // there is a variable info at levels 0 and 3
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                        Assert.assertFalse(d.variableInfoContainer().isInitial());
                    } else {
                        // there is a variable info in level 1, copied from level 1 in statement 0
                        // problem is that there is one in level 3 already, with a NO_VALUE
                        VariableInfo vi1 = d.variableInfoContainer().current();
                        Assert.assertEquals("set3", vi1.getValue().toString());
                        Assert.assertEquals("this.set3", debug(vi1.getLinkedVariables()));
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    }
                }
            }
            if ("add3".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_3.set3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    if (d.iteration() == 0) Assert.assertNull(d.variableInfo().getLinkedVariables());
                    else {
                        Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                        Assert.assertEquals(SET3_EFV, d.variableInfo().getValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertTrue(d.variableInfo().isRead());

                    if (d.iteration() == 0) {
                        Assert.assertNull(d.variableInfo().getLinkedVariables());
                        // start off with FALSE
                        Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    } else {
                        Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                        Assert.assertEquals("instance type HashSet", d.variableInfo().getValue().toString());
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    }
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() > 0) {
                    Assert.assertTrue(d.statementAnalysis().methodLevelData.linksHaveBeenEstablished.isSet());
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if (d.fieldInfo().name.equals("set3")) {
                if (d.iteration() == 0) {
                    Assert.assertEquals(Level.DELAY, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                } else {
                    Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                    Assert.assertEquals(SET3_EFV, d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.iteration() > 1) {
                        Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                    }
                }
            }
        };


        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addInSet = set.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, addInSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            TypeInfo hashSet = typeMap.get(HashSet.class);
            MethodInfo addInHashSet = hashSet.findUniqueMethod("add", 1);
            Assert.assertEquals(Level.TRUE, addInHashSet.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
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
    IT 0: READ, ASSIGNED; set4 FINAL
    IT 1: set4 gets a value in add4; set4 linked to in4
    IT 2: set4 MODIFIED, NOT_NULL;
     */

    @Test
    public void test4() throws IOException {
        final String SET4 = "org.e2immu.analyser.testexample.Modification_4.set4";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && SET4.equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                    } else {
                        Assert.assertEquals("instance type Set<String>", d.currentValue().toString());
                    }
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && "local4".equals(d.variableName()) && "0".equals(d.statementId())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
            }
            if ("Modification_4".equals(d.methodInfo().name) && SET4.equals(d.variableName()) && "0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.NOT_NULL));
                } else {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }
                Assert.assertEquals("in4", d.currentValue().toString());
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                Assert.assertNull(d.haveError(Message.NULL_POINTER_EXCEPTION));
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            int iteration = d.iteration();
            if (d.fieldInfo().name.equals("set4")) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
                int notNull = d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL);
                if (iteration == 1) {
                    Assert.assertEquals("in4", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    Assert.assertEquals("in4", debug(d.fieldAnalysis().getLinkedVariables()));
                    Assert.assertEquals(Level.DELAY, modified);
                    Assert.assertEquals(Level.DELAY, notNull);
                }
                if (iteration >= 2) {
                    Assert.assertEquals(Level.TRUE, modified);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, notNull);
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                ParameterAnalysis in4 = d.parameterAnalyses().get(0);
                if (iteration >= 2) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in4.getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, in4.getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("add4".equals(name)) {
                if (iteration >= 1) {
                    FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                    VariableInfo vi = d.getFieldAsVariable(set4);
                    assert vi != null;
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL));
                }
                if (iteration >= 2) {
                    Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
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

            if ("Modification_5".equals(d.methodInfo().name) && "in5".equals(d.variableName()) && "0".equals(d.statementId())) {
                Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
            }

            if ("Modification_5".equals(d.methodInfo().name) &&
                    "org.e2immu.analyser.testexample.Modification_5.set5".equals(d.variableName()) && "0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertTrue(d.currentValue() instanceof NewObject);
                } else {
                    Assert.assertTrue(d.currentValue() instanceof VariableExpression);
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.FINAL));
                }
            }

        };
        testClass("Modification_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }

                if (EXAMPLE6.equals(d.variableName())) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                }
                if (EXAMPLE6_SET6.equals(d.variableName())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    if (d.iteration() > 1)
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name)) {
                if (SET6.equals(d.variableName()) && "0".equals(d.statementId()) && d.iteration() == 3) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                }
                if (IN6.equals(d.variableName()) && "0".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED));
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
                    Assert.assertEquals("in6", debug(d.fieldAnalysis().getLinkedVariables()));
                }
                if (iteration >= 2) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
                    int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
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
                Assert.assertEquals(expectIn6NotNull, in6.getProperty(VariableProperty.NOT_NULL));

                int expectIn6Modified = iteration < 2 ? Level.FALSE : Level.TRUE;
                Assert.assertEquals(expectIn6Modified, in6.getProperty(VariableProperty.MODIFIED));
            }
            if ("add6".equals(name)) {
                ParameterAnalysis values6 = d.parameterAnalyses().get(1);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, values6.getProperty(VariableProperty.NOT_NULL));

                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);
                VariableInfo set6VariableInfo = d.getFieldAsVariable(set6);
                Assert.assertNull(set6VariableInfo); // this variable does not occur!

                List<VariableInfo> vis = d.methodAnalysis().getLastStatement().latestInfoOfVariablesReferringTo(set6, false);
                Assert.assertEquals(1, vis.size());
                VariableInfo vi = vis.get(0);
                if (d.iteration() > 0)
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, vi.getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, vi.getProperty(VariableProperty.MODIFIED));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            ParameterInfo p0 = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.parameterAnalysis.get()
                    .getProperty(VariableProperty.NOT_NULL));
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

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "theSet".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                }
                if (d.iteration() > 0) {
                    Assert.assertNull(d.variableInfo().getLinkedVariables());
                    if (d.currentValue() instanceof VariableExpression ve) {
                        // we read the linkedVariables from s2
                        Assert.assertEquals("s2", ve.variable().simpleName());
                    } else {
                        Assert.fail();
                    }
                }
            }
            if ("add".equals(d.methodInfo().name) && S2.equals(d.variableName())) {
                if (d.iteration() > 0) {
                    Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if (ADD.equals(d.methodInfo().fullyQualifiedName) && d.iteration() > 0) {
                Assert.assertTrue(d.methodAnalysis().methodLevelData().linksHaveBeenEstablished.isSet());
            }
        };
        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test10() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

            if ("addAll".equals(d.methodInfo().name) && "d".equals(d.variableName())) {
                Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
            }
            if ("addAll".equals(d.methodInfo().name) && "c".equals(d.variableName())) {
                Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
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
                    Assert.assertTrue(list.getAssignedToField().isEmpty());
                }
                if (iteration >= 2) {
                    Assert.assertEquals(0, list.getProperty(VariableProperty.MODIFIED));
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
                    Assert.assertEquals(0, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("s0".equals(fieldInfo.name)) {
                if (iteration >= 2) {
                    Assert.assertEquals(1, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

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
        testClass("Modification_11", 0, 0, new DebugConfiguration.Builder()

                .build());
    }
}
