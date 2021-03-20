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
