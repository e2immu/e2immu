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

import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.resolver.SortedType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.ANNOTATION_XML_READER;
import static org.e2immu.analyser.util.Logger.LogTarget.ANNOTATION_XML_WRITER;

/*
  description = "Convert all annotations in the annotatedAPIs to annotation.xml files"
    classpath = sourceSets.main.runtimeClasspath
    main = 'org.e2immu.analyser.cli.Main'
    Set<File> reducedClassPath = sourceSets.main.runtimeClasspath.toList()
    reducedClassPath += sourceSets.test.runtimeClasspath
    reducedClassPath.removeIf({ f -> f.path.contains("build/classes") || f.path.contains("build/resources") })
    jvmArgs("--enable-preview")
    args('--classpath=' + reducedClassPath.join(":") + ":src/main/resources/annotatedAPIs:jmods/java.base.jmod",
            '--source=non_existing_dir',
            '-w',
            '--write-annotation-xml-dir=build/annotations',
            '--debug=INSPECT,ANNOTATION_XML_WRITER'
 */
public class TestAnnotationXmlWriter {

    @Test
    public void test() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addSources("some/empty/dir")
                .addClassPath(InputConfiguration.DEFAULT_CLASSPATH)
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addAnnotatedAPISources("../annotatedAPIs/src/main/java");
        AnnotationXmlConfiguration.Builder annotationXml = new AnnotationXmlConfiguration.Builder()
                .addAnnotationXmlWritePackages("java.", "org.slf4j.")
                .setWriteAnnotationXmlDir("build/annotations")
                .setAnnotationXml(true);
        Configuration configuration = new Configuration.Builder()
                .addDebugLogTargets(List.of(ANNOTATION_XML_READER, ANNOTATION_XML_WRITER)
                        .stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotationXmConfiguration(annotationXml.build())
                .build();
        configuration.initializeLoggers();
        List<SortedType> res = new Parser(configuration).run().annotatedAPISortedTypes();
        Set<TypeInfo> types = res.stream().map(SortedType::primaryType).collect(Collectors.toSet());
        AnnotationXmlWriter.write(configuration.annotationXmlConfiguration, types);
    }
}
