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

package org.e2immu.analyser.resolver;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.config.InspectorConfiguration;
import org.e2immu.analyser.log.LogTarget;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;

import java.io.IOException;

public abstract class CommonTest {
    protected static TypeMap inspectAndResolve(Class<?> clazz, String... extraSources) throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.CURRENT_JDK)
                .addSources("src/test/java")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath("jmods/java.compiler.jmod")
                .addClassPath("jmods/java.net.http.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api") // in Constructor_2
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j") // in Import_6
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/classic") // in Import_6
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core")
                .addRestrictSourceToPackages(clazz.getCanonicalName());
        for (String source : extraSources) {
            inputConfigurationBuilder.addRestrictSourceToPackages(source);
        }
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setInspectorConfiguration(new InspectorConfiguration.Builder().setStoreComments(true).build())
                .addDebugLogTargets(LogTarget.RESOLVER,LogTarget.INSPECTOR)
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        Parser.RunResult runResult = parser.run();
        return runResult.typeMap();
    }
}
