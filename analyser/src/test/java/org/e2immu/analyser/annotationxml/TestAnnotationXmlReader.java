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

package org.e2immu.analyser.annotationxml;

import org.e2immu.analyser.annotationxml.model.TypeItem;
import org.e2immu.analyser.util.Resources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class TestAnnotationXmlReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnnotationXmlReader.class);

    @BeforeAll
    public static void beforeClass() {
        org.e2immu.analyser.util.Logger.activate(org.e2immu.analyser.util.Logger.LogTarget.ANNOTATION_XML_READER);
    }

    @Test
    public void test() throws ParserConfigurationException, SAXException, IOException {
        final URL resourceUrl = getClass().getClassLoader().getResource("annotations/jdkAnnotations/java/lang/annotations.xml");
        Objects.requireNonNull(resourceUrl);
        AnnotationXmlReader reader = new AnnotationXmlReader(resourceUrl);
        TypeItem booleanType = reader.typeItemMap.get("java.lang.Boolean");
        assertNotNull(booleanType);
        System.out.println(reader
                .summary()
                .entrySet()
                .stream().map(e -> e.getKey() + ": " + e.getValue())
                .sorted()
                .collect(Collectors.joining("\n")));
        File outputFile = File.createTempFile("e2immu", "TestAnnotationReader.xml");
        outputFile.deleteOnExit();
        AnnotationXmlWriter.writeSinglePackage(outputFile, reader.typeItemMap.values());
        LOGGER.info("Wrote to {}", outputFile);

        assertTrue(reader.typeItemMap.size() > 20);
        AnnotationXmlReader reader2 = new AnnotationXmlReader(outputFile.toURI().toURL());

        assertEquals(reader.typeItemMap.keySet(), reader2.typeItemMap.keySet());
        // TODO more tests would be good
    }

    @Test
    public void testWholeFolder() {
        Resources resources = new Resources();
        resources.addDirectoryFromFileSystem(new File("src/main/resources/annotations/jdkAnnotations"));
        AnnotationXmlReader annotationParser = new AnnotationXmlReader(resources);
        TypeItem booleanType = annotationParser.typeItemMap.get("java.lang.Boolean");
        assertNotNull(booleanType);
        System.out.println(annotationParser
                .summary()
                .entrySet()
                .stream().map(e -> e.getKey() + ": " + e.getValue())
                .sorted()
                .collect(Collectors.joining("\n")));
    }
}
