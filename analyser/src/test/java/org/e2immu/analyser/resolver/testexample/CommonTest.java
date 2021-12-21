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

package org.e2immu.analyser.resolver.testexample;

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVER;

public abstract class CommonTest {
    protected static TypeMap inspectAndResolve(Class<?> clazz) throws IOException {
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.JDK_16)
                .addSources("src/test/java")
                .addClassPath("jmods/java.base.jmod")
                .addRestrictSourceToPackages(clazz.getCanonicalName())
                .build();
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(inputConfiguration)
                .addDebugLogTargets(Stream.of(RESOLVER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        Parser.RunResult runResult = parser.run();
        return runResult.typeMap();
    }
}