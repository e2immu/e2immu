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

import org.e2immu.analyser.analyser.AnnotatedAPIAnalyser;
import org.e2immu.analyser.analyser.PropertyException;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.*;

public class TestTypeSorting {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestTypeSorting.class);

    protected static TypeContext typeContext;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("jmods/java.base.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .addDebugLogTargets(Stream.of(DELAYED, ANALYSER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.util");
        parser.run();
        typeContext = parser.getTypeContext();
        parser.getMessages().forEach(message -> LOGGER.info(message.toString()));
    }

    @Test
    public void test() {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        TypeInfo abstractCollection = typeContext.getFullyQualified(AbstractCollection.class);
        TypeInfo list = typeContext.getFullyQualified(List.class);
        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);
        assertEquals(-1, AnnotatedAPIAnalyser.typeComparator(collection, abstractCollection));
        assertEquals(-1, AnnotatedAPIAnalyser.typeComparator(collection, list));
        assertEquals(-1, AnnotatedAPIAnalyser.typeComparator(collection, arrayList));
        assertEquals(-1, AnnotatedAPIAnalyser.typeComparator(list, arrayList));

        assertEquals(1, AnnotatedAPIAnalyser.typeComparator(abstractCollection, collection));
        assertEquals(1, AnnotatedAPIAnalyser.typeComparator(list, collection));
        assertEquals(1, AnnotatedAPIAnalyser.typeComparator(arrayList, collection));
        assertEquals(1, AnnotatedAPIAnalyser.typeComparator(arrayList, list));
    }
}
