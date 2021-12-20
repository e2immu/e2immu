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

package org.e2immu.analyser.inspection;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVER;

public class TestInspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestInspect.class);


    @Test
    public void inspect() throws IOException {
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.JDK_16)
                .addSources("src/main/java")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath("jmods/java.compiler.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/objectweb/asm")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/apache/commons/io")
                .build();
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(inputConfiguration)
                .addDebugLogTargets(Stream.of(INSPECTOR, RESOLVER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        Parser.RunResult runResult = parser.run();
        parser.getMessages().forEach(m -> LOGGER.info("Message: {}", m));

        TypeMap typeMap = runResult.typeMap();

    }
}
