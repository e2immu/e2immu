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

package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestJavaUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestJavaUtil.class);

    protected TypeContext typeContext;

    private Set<Message> test(String className) throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("jmods/java.base.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        AnnotatedAPIConfiguration.Builder annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs("src/test/java")
                .addReadAnnotatedAPIPackages(getClass().getPackageName() + ".testexample." + className);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration.build())
                .addDebugLogTargets(LogTarget.SHALLOW_ANALYSERS)
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.run();
        typeContext = parser.getTypeContext();
        parser.getMessages().forEach(message -> LOGGER.info(message.toString()));
        parser.getAnnotatedAPIMessages().forEach(message -> LOGGER.info(message.toString()));
        return parser.getAnnotatedAPIMessages().collect(Collectors.toUnmodifiableSet());
    }

    // test that there is an error on the type
    @Test
    public void test_0() throws IOException {
        Set<Message> messages = test("JavaUtil_0");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Location location = collection.newLocation();
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        ms.forEach(m -> LOGGER.info("Error: {}", m));
        assertEquals(1, ms.size());
        assertSame(Message.Label.TYPE_HAS_DIFFERENT_VALUE_FOR_INDEPENDENT, ms.get(0).message());
    }

    // error on the type has gone
    @Test
    public void test_1() throws IOException {
        Set<Message> messages = test("JavaUtil_1");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Location location = collection.newLocation();
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        assertTrue(ms.isEmpty());
    }


    // test that there is an error clashing with @Container
    @Test
    public void test_2() throws IOException {
        Set<Message> messages = test("JavaUtil_2");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        MethodInfo add = collection.findUniqueMethod("add", 1);
        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        Location location = p0.newLocation();
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        ms.forEach(m -> LOGGER.info("Error: {}", m));
        assertEquals(1, ms.size());
        assertSame(Message.Label.CONTRADICTING_ANNOTATIONS, ms.get(0).message());
    }

    // test that there is an error clashing with override
    @Test
    public void test_3() throws IOException {
        Set<Message> messages = test("JavaUtil_3");
        TypeInfo collection = typeContext.getFullyQualified(List.class);
        MethodInfo add = collection.findUniqueMethod("add", 1);
        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        Location location = p0.newLocation();
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        ms.forEach(m -> LOGGER.info("Error: {}", m));
        assertEquals(1, ms.size());
        assertSame(Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER, ms.get(0).message());
    }

    // test @Independent on type parameter
    @Test
    public void test_4() throws IOException {
        test("JavaUtil_4");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        TypeInspection typeInspection = collection.typeInspection.get();
        TypeParameter e = typeInspection.typeParameters().get(0);
        assertEquals("E", e.getName());
        assertTrue(e.isAnnotatedWithIndependent());
        assertEquals(DV.FALSE_DV, collection.typeAnalysis.get().immutableDeterminedByTypeParameters());
        TypeInfo list = typeContext.getFullyQualified(List.class);
        assertEquals(DV.TRUE_DV, list.typeAnalysis.get().immutableDeterminedByTypeParameters());
    }
}
