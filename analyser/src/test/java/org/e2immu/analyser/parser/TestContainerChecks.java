package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.TransferValue;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.PropertyWrapper;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

public class TestContainerChecks extends CommonTestRunner {
    public TestContainerChecks() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("setStrings1".equals(d.methodInfo.name)) {
            if ("strings1param".equals(d.variableName) && "0".equals(d.statementId)) {
                Assert.assertFalse(d.properties.isSet(VariableProperty.NOT_NULL));
            }
            if ("strings1param".equals(d.variableName) && "1".equals(d.statementId)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
            if ("Container1.this.strings1".equals(d.variableName) && "1".equals(d.statementId)) {
                Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, d.currentValue.getPropertyOutsideContext(VariableProperty.NOT_NULL));
            }
        }
        if ("setStrings3".equals(d.methodInfo.name)) {
            if ("strings3param".equals(d.variableName) && "0".equals(d.statementId)) {
                Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.MODIFIED));
            }
        }
        if ("Container4".equals(d.methodInfo.name)) {
            if ("strings4Param".equals(d.variableName)) {
                Assert.assertEquals(Level.IS_A_SIZE, d.properties.get(VariableProperty.SIZE));
            }
            if ("Container4.this.strings4".equals(d.variableName)) {
                Assert.assertTrue(d.currentValue instanceof PropertyWrapper);
                Assert.assertEquals("strings4Param,@NotNull", d.currentValue.toString());
                //  Assert.assertEquals(Level.IS_A_SIZE, (int) d.currentValue.getPropertyOutsideContext(VariableProperty.SIZE));
                //   Assert.assertEquals(Level.IS_A_SIZE, (int) d.properties.get(VariableProperty.SIZE));
            }
        }
        if ("addAll5".equals(d.methodInfo.name)) {
            if ("Container5.this.list".equals(d.variableName)) {
                Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.TRUE, d.properties.get(VariableProperty.MODIFIED));
                Assert.assertEquals(Level.FALSE, d.properties.get(VariableProperty.METHOD_DELAY));
            }
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("add2b".equals(d.methodInfo.name) && "0.0.0".equals(d.statementId)) {
            if (d.iteration == 0) {
                Assert.assertSame(UnknownValue.NO_VALUE, d.condition);
            } else {
                Assert.assertEquals("not (null == this.strings2b)", d.condition.toString());
            }
        }
        // POTENTIAL NULL POINTER EXCEPTION
        if ("add2".equals(d.methodInfo.name) && "0".equals(d.statementId)) {
            if (d.iteration > 0) Assert.assertTrue(d.statementAnalysis.errorFlags.errorValue.isSet());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;
        TypeInfo typeInfo = d.methodInfo().typeInfo;
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();
        if ("setStrings1".equals(name)) {
            FieldInfo strings = typeInfo.getFieldByName("strings1", true);
            TransferValue transferValue = methodLevelData.fieldSummaries.get(strings);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, transferValue.properties.get(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, transferValue.getProperty(VariableProperty.ASSIGNED));
        }
        if ("getStrings1".equals(name)) {
            FieldInfo strings = typeInfo.getFieldByName("strings1", true);
            TransferValue transferValue = methodLevelData.fieldSummaries.get(strings);
            Assert.assertFalse(transferValue.properties.isSet(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.TRUE, transferValue.getProperty(VariableProperty.READ));
            Assert.assertEquals(Level.DELAY, transferValue.getProperty(VariableProperty.ASSIGNED));
        }

        if ("setStrings2".equals(name)) {
            ParameterInfo strings2 = d.methodInfo().methodInspection.get().parameters.get(0);
            Assert.assertEquals("strings2param", strings2.name);
        }
        if ("add2".equals(name) && d.iteration() >= 1) {
            FieldInfo strings = typeInfo.typeInspection.getPotentiallyRun().fields.get(0);
            Assert.assertEquals("strings2", strings.name);
            TransferValue transferValue = methodLevelData.fieldSummaries.get(strings);
            Assert.assertFalse(transferValue.properties.isSet(VariableProperty.NOT_NULL));
            Assert.assertEquals(Level.SIZE_NOT_EMPTY, transferValue.getProperty(VariableProperty.SIZE));
        }
        if ("add2b".equals(name)) {
            FieldInfo strings = typeInfo.typeInspection.getPotentiallyRun().fields.get(0);
            Assert.assertEquals("strings2b", strings.name);
            TransferValue transferValue = methodLevelData.fieldSummaries.get(strings);
            Assert.assertEquals(Level.DELAY, transferValue.properties.get(VariableProperty.ASSIGNED));
            Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, transferValue.properties.get(VariableProperty.READ));
            Assert.assertFalse(transferValue.properties.isSet(VariableProperty.NOT_NULL));
        }
        if ("addAll5".equals(name)) {
            FieldInfo list = typeInfo.getFieldByName("list", true);
            TransferValue transferValue = methodLevelData.fieldSummaries.get(list);
            Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.READ));
            if (d.iteration() > 0) {
                Assert.assertEquals(Level.TRUE, transferValue.properties.get(VariableProperty.MODIFIED));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("strings1".equals(d.fieldInfo().name)) {
            if (d.iteration() == 0) {
                Assert.assertEquals(Level.DELAY, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
            } else {
                // setter may not have been called yet; there is no initialiser
                Assert.assertEquals(MultiLevel.NULLABLE, d.fieldAnalysis().getProperty(VariableProperty.NOT_NULL));
            }
        }
        if ("strings2".equals(d.fieldInfo().name)) {
            if (d.iteration() >= 1) {
                Assert.assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo forEach = collection.typeInspection.getPotentiallyRun().methods.stream().filter(m -> "forEach".equals(m.name)).findAny().orElseThrow();
        Assert.assertSame(Primitives.PRIMITIVES.voidTypeInfo, forEach.returnType().typeInfo);

        TypeInfo hashSet = typeContext.getFullyQualified(HashSet.class);
        MethodInfo constructor1 = hashSet.typeInspection.getPotentiallyRun().constructors.stream()
                .filter(m -> m.methodInspection.get().parameters.size() == 1)
                .filter(m -> m.methodInspection.get().parameters.get(0).parameterizedType.typeInfo == collection)
                .findAny().orElseThrow();
        ParameterInfo param1Constructor1 = constructor1.methodInspection.get().parameters.get(0);
        Assert.assertEquals(Level.FALSE, param1Constructor1.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED));
    };


    @Test
    public void test() throws IOException {
        // warning to expect: the potential null pointer exception of strings2
        testClass("ContainerChecks", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeContextVisitor(typeContextVisitor)
                .build());
    }

}
