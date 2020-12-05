/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.annotationxml;

import org.e2immu.analyser.config.AnnotationXmlConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
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
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/google/common/collect")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "io/vertx/core")
                .addAnnotatedAPISources("../annotatedAPIs/src/main/java");
        AnnotationXmlConfiguration.Builder annotationXml = new AnnotationXmlConfiguration.Builder()
                .addAnnotationXmlPackages("java.lang", "java.util")
                .setWriteAnnotationXmlDir("build/annotations")
                .setAnnotationXml(true);
        Configuration configuration = new Configuration.Builder()
                .addDebugLogTargets(List.of(ANNOTATION_XML_READER, ANNOTATION_XML_WRITER)
                        .stream().map(Enum::toString).collect(Collectors.joining(",")))
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setWriteAnnotationXmConfiguration(annotationXml.build())
                .build();
        configuration.initializeLoggers();
        new Parser(configuration).run();
    }
}
