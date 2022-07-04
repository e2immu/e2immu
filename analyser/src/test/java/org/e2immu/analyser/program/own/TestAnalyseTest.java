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

package org.e2immu.analyser.program.own;

import org.e2immu.analyser.config.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestAnalyseTest {

    @Test
    public void test() throws IOException {

        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.JDK_16)
                .addSources("src/test/java")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath("jmods/java.compiler.jmod")
                .addClassPath("jmods/java.xml.jmod") // org.w3c.dom.Document
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/objectweb/asm")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath("build/libs/analyser-0.5.0.jar")
                // we have to avoid doing normal parsing of annotated-api files such as the files in
                // org.e2immu.analyser.shallow.testexample, e.g. JavaUtil_0
                .addRestrictSourceToPackages("org.e2immu.analyser.parser")
                .build();

        AnalyserConfiguration analyserConfiguration = new AnalyserConfiguration.Builder()
                .setComputeFieldAnalyserAcrossAllMethods(true)
                .setAnalyserProgram(AnalyserProgram.from(AnalyserProgram.Step.ALL))
                .build();

        DebugConfiguration debugConfiguration = new DebugConfiguration.Builder().build();

        // we'll encounter some tests with dollar types. For our current purpose, they're simply Java POJOs, we don't
        // want to see them as AnnotatedAPI
        AnnotatedAPIConfiguration annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS)
                .build();
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(false)
                .setInputConfiguration(inputConfiguration)
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration)
                .setAnalyserConfiguration(analyserConfiguration)
                .setDebugConfiguration(debugConfiguration)
                .addDebugLogTargets("analyser")//.addDebugLogTargets("resolver").addDebugLogTargets("bytecode")
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.run();
    }
}
