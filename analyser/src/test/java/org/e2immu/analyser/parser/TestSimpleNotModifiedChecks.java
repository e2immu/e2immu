package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.FinalFieldValue;
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.annotation.AnnotationMode;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class TestSimpleNotModifiedChecks extends CommonTestRunner {
    public TestSimpleNotModifiedChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {

        if ("size".equals(d.methodInfo.name) && "Example2".equals(d.methodInfo.typeInfo.simpleName)) {
            Assert.assertEquals(0, d.properties.get(VariableProperty.MODIFIED));
        }

        if ("add3".equals(d.methodInfo.name) && "local3".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                if (d.iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue);
                } else {
                    Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
                    FinalFieldValue variableValue = (FinalFieldValue) d.currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("this.set3", d.currentValue.toString());
                }
            }
        }
        if ("add4".equals(d.methodInfo.name) && "local4".equals(d.variableName)) {
            if ("0".equals(d.statementId)) {
                if (d.iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue);
                } else {
                    Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
                    FinalFieldValue variableValue = (FinalFieldValue) d.currentValue;
                    Assert.assertTrue(variableValue.variable instanceof FieldReference);
                    Assert.assertEquals("this.set4", d.currentValue.toString());
                }
            }
            if ("1".equals(d.statementId) && d.iteration > 1) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
        }
        if ("add4".equals(d.methodInfo.name) && "Example4.this.set4".equals(d.variableName)) {
            if ("1".equals(d.statementId)) {
                if (d.iteration == 0) {
                    Assert.assertSame(UnknownValue.NO_VALUE, d.currentValue);
                } else {
                    Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
                    if (d.iteration > 1) {
                        Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
                    }
                }
            }
        }
        if ("Example4".equals(d.methodInfo.name) && "set4".equals(d.variableName) && "0".equals(d.statementId)) {
            if (d.iteration == 3) {
                Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.properties.get(VariableProperty.NOT_NULL));
            }
        }

        if ("Example5".equals(d.methodInfo.name) && "in5".equals(d.variableName) && "0".equals(d.statementId)) {
            Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.MODIFIED));
        }

        if ("Example5".equals(d.methodInfo.name) && "Example5.this.set5".equals(d.variableName) && "0".equals(d.statementId)) {
            if (d.iteration == 0) {
                Assert.assertTrue(d.currentValue instanceof Instance);
            } else {
                Assert.assertTrue(d.currentValue instanceof FinalFieldValue);
            }
        }

        if ("add6".equals(d.methodInfo.name) && "values6".equals(d.variableName)) {
            if (d.iteration > 1) {
                Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
        }
        if ("add6".equals(d.methodInfo.name) && "example6".equals(d.variableName)) {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
        if ("add6".equals(d.methodInfo.name) && "example6.set6".equals(d.variableName)) {
            Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.MODIFIED));
            if (d.iteration > 1)
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
        }
        if ("Example6".equals(d.methodInfo.name) && "set6".equals(d.variableName) && "0".equals(d.statementId)) {
            if (d.iteration == 3) {
                Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
        }
        if ("Example6".equals(d.methodInfo.name) && "in6".equals(d.variableName) && "0".equals(d.statementId)) {
            if (d.iteration == 0) {
                Assert.assertFalse(d.properties.isSet(VariableProperty.MODIFIED));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("add4".equals(d.methodInfo.name) && "1".equals(d.statementId)) {
            Assert.assertNull(d.haveError(Message.NULL_POINTER_EXCEPTION));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int modified = d.fieldAnalysis().getProperty(VariableProperty.MODIFIED);
        int iteration = d.iteration();
        String name = d.fieldInfo().name;
        if (name.equals("set2ter")) {
            if (iteration == 0) {
                Assert.assertEquals(Level.DELAY, modified);
            } else {
                Assert.assertEquals(Level.TRUE, modified);
            }
        }
        if (name.equals("set2bis")) {
            if (iteration <= 1) {
                Assert.assertEquals(Level.DELAY, modified);
            } else {
                Assert.assertEquals(Level.TRUE, modified);
            }
        }
        if (name.equals("set2")) {
            if (iteration == 0) {
                Assert.assertEquals(Level.DELAY, modified);
            } else {
                Assert.assertEquals(Level.FALSE, modified);
            }
        }
        if (name.equals("set3")) {
            if (iteration == 0) {
                Assert.assertEquals(Level.DELAY, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
            if (iteration == 1) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());
            }
        }
        if (name.equals("set4")) {
            if (iteration == 0) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
            if (iteration == 1) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
                Assert.assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());
                Assert.assertNull(d.fieldAnalysis().getVariablesLinkedToMe());
            }
            if (iteration >= 2) {
                Assert.assertEquals(1, d.fieldAnalysis().getVariablesLinkedToMe().size());
                Assert.assertEquals("in4", d.fieldAnalysis().getVariablesLinkedToMe().stream().findFirst().orElseThrow().simpleName());
                Assert.assertEquals(Level.TRUE, modified);
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        }
        if (name.equals("set6")) {
            if (iteration == 0) {
                Assert.assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
            if (iteration == 1) {
                Assert.assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());
                Assert.assertNull(d.fieldAnalysis().getVariablesLinkedToMe());
            }
            if (iteration >= 2) {
                Assert.assertEquals("in6", d.fieldAnalysis().getVariablesLinkedToMe().stream().findFirst().orElseThrow().simpleName());
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, modified);
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        int iteration = d.iteration();
        String name = d.methodInfo().name;
        MethodInfo methodInfo = d.methodInfo();

        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if ("size".equals(name) && "Example2".equals(methodInfo.typeInfo.simpleName)) {
            if (iteration > 0) {
                FieldInfo set2 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.get(0);
                Assert.assertEquals("set2", set2.name);
                TransferValue tv = methodLevelData.fieldSummaries.get(set2);
                Assert.assertEquals(0, tv.properties.get(VariableProperty.MODIFIED));
            }
            if (iteration > 1) {
                Assert.assertEquals(0, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("Example4".equals(name)) {
            ParameterInfo in4 = methodInfo.methodInspection.get().parameters.get(0);
            if (iteration >= 2) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, in4.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, in4.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("add4".equals(name)) {
            if (iteration >= 1) {
                FieldInfo set4 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(f -> f.name.equals("set4")).findAny().orElseThrow();
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodLevelData.fieldSummaries.get(set4)
                        .properties.get(VariableProperty.NOT_NULL));
            }
            if (iteration >= 2) {
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
        if ("Example6".equals(name)) {
            ParameterInfo in6 = methodInfo.methodInspection.get().parameters.get(0);
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
            FieldInfo set6 = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(f -> f.name.equals("set6")).findAny().orElseThrow();

            if (iteration >= 2) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodLevelData.fieldSummaries.get(set6)
                        .properties.get(VariableProperty.NOT_NULL));
                Assert.assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo set = typeContext.getFullyQualified(Set.class);
        Assert.assertEquals(AnnotationMode.DEFENSIVE, set.typeInspection.getPotentiallyRun().annotationMode);
        MethodInfo add = set.typeInspection.getPotentiallyRun().methods.stream().filter(mi -> mi.name.equals("add")).findFirst().orElseThrow();
        Assert.assertFalse(add.methodAnalysis.get().isHasBeenDefined());
        Assert.assertEquals(Level.TRUE, add.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

        MethodInfo addAll = set.typeInspection.getPotentiallyRun().methods.stream().filter(mi -> mi.name.equals("addAll")).findFirst().orElseThrow();
        Assert.assertEquals(Level.TRUE, addAll.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));
        ParameterInfo first = addAll.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.FALSE, first.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));

        MethodInfo size = set.typeInspection.getPotentiallyRun().methods.stream().filter(mi -> mi.name.equals("size")).findFirst().orElseThrow();
        Assert.assertEquals(Level.FALSE, size.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

        TypeInfo hashSet = typeContext.getFullyQualified(Set.class);
        Assert.assertEquals(Level.TRUE, hashSet.typeAnalysis.get().getProperty(VariableProperty.CONTAINER));
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if (d.iteration() == 1 && "Example4".equals(d.typeInfo().simpleName)) {
            int immutable = d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE);
            Assert.assertEquals(MultiLevel.EFFECTIVE, MultiLevel.value(immutable, MultiLevel.E1IMMUTABLE));
            Assert.assertEquals(MultiLevel.DELAY, MultiLevel.value(immutable, MultiLevel.E2IMMUTABLE));
        }
    };

    @Test
    public void test() throws IOException {
        // Both ERROR and WARN in Example2bis
        testClass("SimpleNotModifiedChecks", 1, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
