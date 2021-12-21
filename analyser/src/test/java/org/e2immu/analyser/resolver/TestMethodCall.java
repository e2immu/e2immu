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
import org.e2immu.analyser.resolver.testexample.MethodCall_0;
import org.e2immu.analyser.resolver.testexample.MethodCall_1;
import org.e2immu.analyser.resolver.testexample.MethodCall_2;
import org.e2immu.analyser.resolver.testexample.MethodCall_3;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestMethodCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestMethodCall.class);

    private void parse(Class<?> clazz, String formalParameterType) throws IOException {
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
        TypeMap typeMap = runResult.typeMap();

        TypeInfo typeInfo = typeMap.get(MethodCall_0.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("test", 0);
        Block block = methodInfo.methodInspection.get().getMethodBody();
        if (block.structure.statements().get(0) instanceof ExpressionAsStatement eas) {
            if (eas.expression instanceof MethodCall methodCall) {
                LOGGER.info("Accept: {}", methodCall);
                ParameterInfo p0 = methodCall.methodInfo.methodInspection.get().getParameters().get(0);
                assertEquals(formalParameterType, p0.parameterizedType.toString());
            } else fail();
        } else fail();
    }

    @Test
    public void test_0() throws IOException {
        parse(MethodCall_0.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_0.Get>");
    }

    @Test
    public void test_1() throws IOException {
        parse(MethodCall_1.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_1.Get>");
    }

    @Test
    public void test_2() throws IOException {
        parse(MethodCall_2.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_2.Get>");
    }

    @Test
    public void test_3() throws IOException {
        parse(MethodCall_3.class,
                "Type java.util.List<org.e2immu.analyser.resolver.testexample.MethodCall_3.Get>");
    }
}
