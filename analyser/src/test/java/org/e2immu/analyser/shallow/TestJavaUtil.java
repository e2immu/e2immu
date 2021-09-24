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

import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.TypeInfo;
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
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
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
                .addReadAnnotatedAPIPackages("org.e2immu.analyser.testannotatedapi." + className);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration.build())
                .addDebugLogTargets(Stream.of(DELAYED, ANALYSER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.io"); // to compute properties on System.out; java.io.PrintStream
        parser.preload("java.util");
        parser.preload("java.util.stream");
        parser.preload("java.util.concurrent");
        parser.preload("java.lang.reflect");
        parser.run();
        typeContext = parser.getTypeContext();
        parser.getMessages().forEach(message -> LOGGER.info(message.toString()));
        return parser.getMessages().collect(Collectors.toUnmodifiableSet());
    }

    // test that there is an error on the type
    @Test
    public void test_0() throws IOException {
        Set<Message> messages = test("JavaUtil_0");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Location location = new Location(collection);
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        ms.forEach(m -> LOGGER.info("Error: {}", m));
        assertEquals(1, ms.size());
        assertSame(Message.Label.TYPE_HAS_HIGHER_VALUE_FOR_INDEPENDENT, ms.get(0).message());
    }

    // error on the type has gone
    @Test
    public void test_1() throws IOException {
        Set<Message> messages = test("JavaUtil_1");
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        Location location = new Location(collection);
        List<Message> ms = messages.stream().filter(m -> m.location().equals(location)).toList();
        assertTrue(ms.isEmpty());
    }
}
