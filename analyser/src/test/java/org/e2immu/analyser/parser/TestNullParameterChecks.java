package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TestNullParameterChecks extends CommonTestRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestNullParameterChecks.class);

    public TestNullParameterChecks() {
        super(true);
    }
    //     public void method2(@NotNull String s) {
    //        this.s = Objects.requireNonNull(s);
    //    }

    //  public void method8Implicit(@NotNull(type = AnnotationType.VERIFY_ABSENT) String s) {
    //    if (s != null) { // 0
    //        this.s = s.strip(); // 0.0.0
    //   } else {
    //        this.s = "abc";
    //    }
    //}

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if (d.methodInfo.name.equals("method2")) {

            if (!d.statementId.equals("0")) Assert.fail();

            if ("s".equals(d.variableName)) {
                LOGGER.info("Properties of s it iteration {} are {}, value {}", d.iteration, d.properties, d.currentValue);
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.READ));
                Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED)); //FALSE at level 1
                //Assert.assertEquals(Level.compose(Level.TRUE, 1), (int) d.properties.get(VariableProperty.NOT_NULL));
                return;
            }
            if ("NullParameterChecks.this.s".equals(d.variableName)) {
                Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED));
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.LAST_ASSIGNMENT_GUARANTEED_TO_BE_REACHED));
                Assert.assertEquals(Level.TRUE, (int) d.properties.get(VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT)); // even field will have this
                return;
            }
            //Assert.fail();
        }
        if ("method8Implicit".equals(d.methodInfo.name)) {
            if ("0.0.0".equals(d.statementId) && "NullParameterChecks.this.s".equals(d.variableName)) {
                // TODO
            }
            // the parameter "s" is not present in the variable d.properties at the 0.0.0 level; it is one higher
            if ("0".equals(d.statementId)) {
                if ("s".equals(d.variableName)) {
                    // we should know straight away (without delay) that the strip method on String is "safe"
                    Assert.assertEquals(Level.FALSE, (int) d.properties.get(VariableProperty.MODIFIED));
                    Assert.assertEquals(Level.READ_ASSIGN_MULTIPLE_TIMES, (int) d.properties.get(VariableProperty.READ));
                } else if ("NullParameterChecks.this.s".equals(d.variableName)) {
                    // we do NOT have assigned 2x here, because the if-statement blocks are not guaranteed to be executed
                    Assert.assertEquals(Level.READ_ASSIGN_ONCE, (int) d.properties.get(VariableProperty.ASSIGNED));
                }
            }
        }
        if ("method11Lambda".equals(d.methodInfo.name) && "supplier".equals(d.variableName) && "0".equals(d.statementId)) {
            // value was an Instance, which gets translated to a VariableValue that is not null
            Assert.assertTrue("Have " + d.currentValue.getClass(), d.currentValue instanceof VariableValue);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, (int) d.properties.get(VariableProperty.NOT_NULL));
        }
        if ("method12LambdaBlock".equals(d.methodInfo.name) && "supplier".equals(d.variableName) && "0".equals(d.statementId)) {
            // value was an Instance, which gets translated to a VariableValue that is not null
            Assert.assertTrue("Have " + d.currentValue.getClass(), d.currentValue instanceof VariableValue);
            Assert.assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, (int) d.properties.get(VariableProperty.NOT_NULL));
        }
    };


    StatementAnalyserVisitor statementAnalyserVisitor = new StatementAnalyserVisitor() {
        @Override
        public void visit(int iteration, MethodInfo methodInfo, NumberedStatement numberedStatement, Value conditional) {
            if ("method9".equals(methodInfo.name) && iteration > 0) {
                Assert.assertTrue(numberedStatement.errorValue.isSet());
            }
        }
    };

    TypeContextVisitor typeContextVisitor = typeContext -> {
        TypeInfo objects = typeContext.typeStore.get("java.util.Objects");
        MethodInfo requireNonNull = objects.typeInspection.get()
                .methods.stream().filter(mi -> "requireNonNull".equals(mi.name) &&
                        1 == mi.methodInspection.get().parameters.size()).findFirst().orElseThrow();
        Assert.assertEquals(Level.TRUE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.IDENTITY));
        Assert.assertEquals(Level.FALSE, requireNonNull.methodAnalysis.get().getProperty(VariableProperty.MODIFIED));

    };

    @Test
    public void test() throws IOException {
        testClass("NullParameterChecks", 0, 1, new DebugConfiguration.Builder()
                .addTypeContextVisitor(typeContextVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
