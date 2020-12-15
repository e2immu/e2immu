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
import java.util.Set;

public class Test_16_Modification extends CommonTestRunner {

    public Test_16_Modification() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

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
                Assert.assertEquals(StatementAnalyser.STEP_3, d.step());
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

        EvaluationResultVisitor evaluationResultVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "1".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertSame(EmptyExpression.NO_VALUE, d.evaluationResult().value());
                } else {
                    Assert.assertEquals("set3.add(v)", d.evaluationResult().value().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add3".equals(d.methodInfo().name) && "local3".equals(d.variableName())) {
                if ("0".equals(d.statementId())) {
                    Assert.assertEquals(1, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertEquals(Level.DELAY, d.getProperty(VariableProperty.READ));

                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                    } else {
                        Assert.assertEquals(1, d.variableInfoContainer().getCurrentLevel());
                        VariableInfo variableInfo = d.variableInfoContainer().get(1);
                        Assert.assertTrue(variableInfo.getValue() instanceof VariableExpression);
                        VariableExpression variableValue = (VariableExpression) d.currentValue();
                        Assert.assertTrue(variableValue.variable() instanceof FieldReference);
                        Assert.assertEquals("set3", d.currentValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    // thanks to the code in ModificationData.level, the READ is written at level 1 instead of 3
                    Assert.assertEquals(1, d.variableInfoContainer().getCurrentLevel());
                    Assert.assertEquals(1, d.getProperty(VariableProperty.ASSIGNED));
                    Assert.assertEquals(2, d.getProperty(VariableProperty.READ));
                    if (d.iteration() == 0) {
                        // there is a variable info at levels 0 and 3
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                        Assert.assertNotNull(d.variableInfoContainer().best(0));
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
                        Assert.assertEquals("instance type Set<String>", d.variableInfo().getValue().toString());
                    }
                }
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.READ));

                    if (d.iteration() == 0) {
                        Assert.assertNull(d.variableInfo().getLinkedVariables());
                        // start off with FALSE
                        Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    } else {
                        Assert.assertEquals("", debug(d.variableInfo().getLinkedVariables()));
                        // FIXME the /*empty*/ prob. comes from the add() method
                        // Assert.assertEquals("instance type Set<String>", d.variableInfo().getValue().toString());
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
                    Assert.assertEquals("set3", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.iteration() > 1) {
                        Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED));
                    }
                }
            }
        };

        testClass("Modification_3", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addEvaluationResultVisitor(evaluationResultVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }


    @Test
    public void test4() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add4".equals(d.methodInfo().name) && "org.e2immu.analyser.testexample.Modification_4.set4".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() == 0) {
                        Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                    } else {
                        Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.FINAL));
                        Assert.assertEquals("instance type Set<String>", d.currentValue().toString());
                        if (d.iteration() > 1) {
                            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                        }
                    }
                }
            }
            if ("Modification_4".equals(d.methodInfo().name) && "set4".equals(d.variableName()) && "0".equals(d.statementId())) {
                if (d.iteration() == 3) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getProperty(VariableProperty.NOT_NULL));
                }
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
                if (iteration >= 1) {
                    Assert.assertEquals("in4", debug(d.fieldAnalysis().getLinkedVariables()));
                }
                if (iteration >= 2) {
                    int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
                    Assert.assertEquals(Level.TRUE, modified);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;
            if ("Modification_4".equals(name)) {
                ParameterInfo in4 = d.methodInfo().methodInspection.get().getParameters().get(0);
                if (iteration >= 2) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in4.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, in4.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("add4".equals(name)) {
                if (iteration >= 1) {
                    FieldInfo set4 = d.methodInfo().typeInfo.getFieldByName("set4", true);
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getFieldAsVariable(set4).getProperty(VariableProperty.NOT_NULL));
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

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add6".equals(d.methodInfo().name) && "values6".equals(d.variableName())) {
                if (d.iteration() > 1) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                }
            }
            if ("add6".equals(d.methodInfo().name) && "example6".equals(d.variableName())) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            }
            if ("add6".equals(d.methodInfo().name) && "example6.set6".equals(d.variableName())) {
                Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                if (d.iteration() > 1)
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
            }
            if ("Modification_6".equals(d.methodInfo().name) && "set6".equals(d.variableName()) && "0".equals(d.statementId())) {
                if (d.iteration() == 3) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL));
                }
            }
            if ("Modification_6".equals(d.methodInfo().name) && "in6".equals(d.variableName()) && "0".equals(d.statementId())) {
                if (d.iteration() == 0) {
                    Assert.assertFalse(d.hasProperty(VariableProperty.MODIFIED));
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
                if (iteration == 1) {
                    Assert.assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());
                    Assert.assertNull(d.fieldAnalysis().getLinkedVariables());
                }
                if (iteration >= 2) {
                    Assert.assertEquals("in6", d.fieldAnalysis().getLinkedVariables().stream().findFirst().orElseThrow().simpleName());
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
                ParameterInfo in6 = d.methodInfo().methodInspection.get().getParameters().get(0);
                if (iteration == 0 || iteration == 1) {
                    Assert.assertEquals(Level.DELAY, in6.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    // NOTE: an "improvement from FALSE to TRUE" will be made from iteration 1 to iteration 2
                    Assert.assertEquals(Level.FALSE, in6.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                } else {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in6.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, in6.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("add6".equals(name)) {
                FieldInfo set6 = d.methodInfo().typeInfo.getFieldByName("set6", true);

                if (iteration >= 2) {
                    Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.getFieldAsVariable(set6).getProperty(VariableProperty.NOT_NULL));
                    Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
                }
            }
        };
        testClass("Modification_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && "theSet".equals(d.variableName())) {
                if ("1".equals(d.statementId())) {
                    Assert.assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED));
                }
                if ("2".equals(d.statementId())) {
                    Assert.assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED));
                }
            }
        };

        testClass("Modification_9", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
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
            if ("addAllOnC".equals(d.methodInfo().name)) {
                if ("d".equals(d.variableName())) {
                    Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
                }
                if ("d.set".equals(d.variableName())) {
                    Assert.assertEquals(0, d.getProperty(VariableProperty.MODIFIED));
                }
                if ("c.set".equals(d.variableName())) {
                    Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
                }
                if ("c".equals(d.variableName())) {
                    Assert.assertEquals(1, d.getProperty(VariableProperty.MODIFIED));
                }
            }
            if ("NotModifiedChecks".equals(d.methodInfo().name) && "NotModifiedChecks.this.s2".equals(d.variableName())) {
                if (d.iteration() < 2) {
                    Assert.assertSame(EmptyExpression.NO_VALUE, d.currentValue());
                } else {
                    Assert.assertEquals("set2", d.currentValue().toString());
                }
            }
            if ("C1".equals(d.methodInfo().name) && "C1.this.set".equals(d.variableName())) {
                Assert.assertEquals("set1,@NotNull", d.currentValue().toString());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int iteration = d.iteration();
            String name = d.methodInfo().name;

            if ("NotModifiedChecks".equals(d.methodInfo().name)) {
                ParameterAnalysis list = d.parameterAnalyses().get(0);
                ParameterAnalysis set2 = d.parameterAnalyses().get(1);
                ParameterAnalysis set3 = d.parameterAnalyses().get(2);
                ParameterAnalysis set4 = d.parameterAnalyses().get(3);

                if (iteration == 0) {
                    Assert.assertNull(list.getAssignedToField());
                } else {
                    Assert.assertNotNull(list.getAssignedToField());
                }
                if (iteration >= 2) {
                    Assert.assertEquals(0, list.getProperty(VariableProperty.MODIFIED));
                    Assert.assertNotNull(set3.getAssignedToField());
                    Assert.assertEquals(1, set3.getProperty(VariableProperty.MODIFIED)); // directly assigned to s0
                    Assert.assertEquals(1, set2.getProperty(VariableProperty.MODIFIED));
                    Assert.assertEquals(1, set4.getProperty(VariableProperty.MODIFIED));
                }
                FieldInfo s2 = d.methodInfo().typeInfo.getFieldByName("s2", true);
                if (iteration > 1) {
                    Set<Variable> s2links = d.getFieldAsVariable(s2).getLinkedVariables();
                    Assert.assertEquals("[1:set2]", s2links.toString());
                }
                FieldInfo set = d.methodInfo().typeInfo.typeInspection.get().subTypes().get(0).getFieldByName("set", true);
                Assert.assertFalse(d.methodAnalysis().getLastStatement().variables.isSet(set.fullyQualifiedName()));
            }
            if ("addAllOnC".equals(name)) {
                ParameterInfo c1 = d.methodInfo().methodInspection.get().getParameters().get(0);
                Assert.assertEquals("c1", c1.name);
                Assert.assertEquals(Level.TRUE, c1.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
            }
            if ("getSet".equals(name)) {
                if (iteration > 0) {
                    int identity = d.getReturnAsVariable().getProperty(VariableProperty.IDENTITY);
                    Assert.assertEquals(Level.FALSE, identity);
                    Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.IDENTITY));

                }
                if (iteration > 1) {
                    Expression value = d.methodAnalysis().getSingleReturnValue();
                    Assert.assertEquals("inline getSet on this.set", value.toString());
                }
            }
            if ("C1".equals(name)) {
                FieldInfo fieldInfo = d.methodInfo().typeInfo.getFieldByName("set", true);
                VariableInfo tv = d.getFieldAsVariable(fieldInfo);
                Assert.assertEquals("[0:set1]", tv.getLinkedVariables().toString());
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
            if ("set".equals(fieldInfo.name)) {
                if (iteration > 0) {
                    Assert.assertEquals("this.set", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                }
                if (iteration > 0) {
                    Assert.assertEquals("[0:set1]", d.fieldAnalysis().getLinkedVariables().toString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);

            MethodInfo addAll = set.typeInspection.get().methods().stream().filter(mi -> mi.name.equals("addAll")).findFirst().orElseThrow();
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
}
