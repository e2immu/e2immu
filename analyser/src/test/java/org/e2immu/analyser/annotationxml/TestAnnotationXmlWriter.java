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

import org.e2immu.analyser.cli.Main;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.parser.Parser;
import org.junit.Test;

import java.io.IOException;

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
        Configuration configuration = new Configuration.Builder().build();
        configuration.initializeLoggers();
        new Parser(configuration).run();
    }
}
