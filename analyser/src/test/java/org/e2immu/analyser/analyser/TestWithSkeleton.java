
/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.EqualsValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.testexample.withannotatedapi.TestSkeleton;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.e2immu.analyser.model.Level.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;

public class TestWithSkeleton {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestWithSkeleton.class);

    @BeforeClass
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.configure(ch.qos.logback.classic.Level.INFO);
        org.e2immu.analyser.util.Logger.activate(ANALYSER, INSPECT, RESOLVE, VARIABLE_PROPERTIES);
    }


    private TypeContext typeContext;
    private TypeInfo testSkeleton;

    @Before
    public void before() throws IOException {
        // parsing the annotatedAPI files needs them being backed up by .class files, so we'll add the Java
        // test runner's classpath to ours
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(new InputConfiguration.Builder()
                        .addSources("src/test/java")
                        .addRestrictSourceToPackages("org.e2immu.analyser.testexample.withannotatedapi.TestSkeleton")
                        .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                        .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                        .build())
                .build();

        Parser parser = new Parser(configuration);
        parser.run();
        typeContext = parser.getTypeContext();
        testSkeleton = typeContext.typeStore.get(TestSkeleton.class.getCanonicalName());
        Assert.assertNotNull(testSkeleton);
    }

    @Test
    public void testProperties() {
        TypeInfo set = typeContext.typeStore.get("java.util.Set");
        Assert.assertNotNull(set);
        Assert.assertFalse(set.typeInspection.get().hasBeenDefined);
        Assert.assertTrue(set.annotatedWith(typeContext.container.get()));
        Assert.assertEquals(TRUE, set.typeAnalysis.getProperty(VariableProperty.CONTAINER));
        Assert.assertEquals(TRUE, set.typeAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETERS));
        Assert.assertEquals(DELAY, set.typeAnalysis.getProperty(VariableProperty.NOT_NULL));

        set.typeInspection.get().methodsAndConstructors().forEach(mi -> LOGGER.info("Have {}", mi.distinguishingName()));

        MethodInfo setOf = set.getMethodOrConstructorByDistinguishingName("java.util.Set.of()");
        Assert.assertEquals(TRUE, setOf.methodAnalysis.getProperty(VariableProperty.CONTAINER));
        Assert.assertEquals(TRUE, setOf.methodAnalysis.getProperty(VariableProperty.NOT_MODIFIED));
        Assert.assertEquals(TRUE, setOf.methodAnalysis.getProperty(VariableProperty.NOT_NULL));
        Assert.assertEquals(TRUE, value(setOf.methodAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE));

        TypeInfo system = typeContext.typeStore.get("java.lang.System");
        FieldInfo out = system.typeInspection.get().fields.stream().filter(fi -> fi.name.equals("out")).findAny().orElseThrow();
        Assert.assertEquals(TRUE, out.fieldAnalysis.getProperty(VariableProperty.IGNORE_MODIFICATIONS));
        Assert.assertEquals(TRUE, out.fieldAnalysis.getProperty(VariableProperty.NOT_NULL));
    }

    @Test
    public void testAddLocalVariable() {
        MethodInfo method = testSkeleton.typeInspection.get().methods.stream().filter(mi -> mi.name.equals("method")).findAny().orElseThrow();
        VariableProperties variableProperties = new VariableProperties(typeContext, method);
        LocalVariable variableS = new LocalVariable(List.of(), "s", Primitives.PRIMITIVES.stringParameterizedType, List.of());
        LocalVariableReference localS = new LocalVariableReference(variableS, List.of());
        variableProperties.createLocalVariableOrParameter(localS);

        Assert.assertTrue(variableProperties.getNullConditionals(true).isEmpty());
        Assert.assertTrue(variableProperties.getNullConditionals(false).isEmpty());
        Assert.assertEquals(DELAY, variableProperties.getProperty(localS, VariableProperty.NOT_NULL));

        // add s != null
        Value sIsNotNull = NegatedValue.negate(new EqualsValue(new VariableValue(variableProperties, localS, localS.name()), NullValue.NULL_VALUE));
        variableProperties.addToConditional(sIsNotNull);

        Set<Variable> nullConditionals2 = variableProperties.getNullConditionals(false);
        Assert.assertTrue(nullConditionals2.contains(localS));
        Assert.assertEquals(1, nullConditionals2.size());

        // we now should have a not null local variable
        Assert.assertEquals(TRUE, variableProperties.getProperty(localS, VariableProperty.NOT_NULL));

        // now explicitly set s to null
        variableProperties.assignmentBasics(localS, NullValue.NULL_VALUE);
        Assert.assertEquals(TRUE, variableProperties.getProperty(localS, VariableProperty.NOT_YET_READ_AFTER_ASSIGNMENT));
        Assert.assertEquals(TRUE, variableProperties.getProperty(localS, VariableProperty.ASSIGNED));

        // remember that we do not copy the properties of the assigned value into the variable
        Assert.assertEquals(DELAY, variableProperties.getProperty(localS, VariableProperty.NOT_NULL));
        Assert.assertEquals(FALSE, variableProperties.currentValue(localS).getProperty(variableProperties, VariableProperty.NOT_NULL));
    }

    @Test
    public void testFinalField() {
        FieldInfo finalString = testSkeleton.typeInspection.get().fields.stream().filter(fi -> fi.name.equals("finalString")).findAny().orElseThrow();
        finalString.fieldAnalysis.setProperty(VariableProperty.FINAL, TRUE);
        finalString.fieldAnalysis.effectivelyFinalValue.set(new StringValue("this is the final value"));

        FieldReference finalStringRef = new FieldReference(finalString, new This(testSkeleton));

        // we're outside the method; during field initialisation
        VariableProperties variableProperties = new VariableProperties(typeContext, testSkeleton);
        Assert.assertFalse(variableProperties.isKnown(finalStringRef));

        Assert.assertEquals(TRUE, variableProperties.getProperty(finalStringRef, VariableProperty.FINAL));
        Assert.assertTrue(variableProperties.isKnown(finalStringRef));

        VariableValue nvv = variableProperties.newVariableValue(finalStringRef);
        Assert.assertEquals("TestSkeleton.this.finalString", nvv.name);

        Value currentValue = variableProperties.currentValue(finalStringRef);
        Assert.assertTrue("Have "+currentValue.getClass(), currentValue instanceof StringValue);
        Assert.assertEquals("this is the final value", ((StringValue) currentValue).value);
    }
}
