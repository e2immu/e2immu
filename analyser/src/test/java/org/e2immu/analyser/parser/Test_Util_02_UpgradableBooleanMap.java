
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/*
first issue: import of Collector.Characteristics should be automatic
second issue: UnevaluatedLambdaExpression exception
 */
public class Test_Util_02_UpgradableBooleanMap extends CommonTestRunner {

    public Test_Util_02_UpgradableBooleanMap() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        int TOO_LATE = 8;

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("UpgradableBooleanMap".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("[Type param T, Type param T, Type param T, Type param T, Type param T, Type param T]",
                        d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());

                int expectImmutable = d.iteration() <= 1 ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }

            if ("$1".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1", d.typeInfo().fullyQualifiedName);

                int expectImmutable = d.iteration() <= TOO_LATE ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }

            if ("$2".equals(d.typeInfo().simpleName)) {
                Assert.assertEquals("org.e2immu.analyser.util.UpgradableBooleanMap.$1.$2", d.typeInfo().fullyQualifiedName);

                int expectImmutable = d.iteration() <= TOO_LATE ? Level.DELAY : MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE;
                Assert.assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("combiner".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ReturnVariable) {
                    Assert.assertEquals("UpgradableBooleanMap::putAll", d.currentValue().toString());
                    Assert.assertEquals("", d.variableInfo().getLinkedVariables().toString());
                }
            }
        };


        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("put".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("stream".equals(d.methodInfo().name)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
            if ("putAll".equals(d.methodInfo().name)) {
                Assert.assertEquals("put,stream", d.methodInfo().methodResolution.get().methodsOfOwnClassReached()
                        .stream().map(m -> m.name).sorted().collect(Collectors.joining(",")));
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            // uses putAll as a method reference
            if ("combiner".equals(d.methodInfo().name)) {
                Assert.assertEquals(Level.FALSE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            // accumulator
            if ("accept".equals(d.methodInfo().name) && "$2".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectMm = d.iteration() <= 2 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            // finisher
            if ("apply".equals(d.methodInfo().name) && "$3".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }

            // putAll
            if ("accept".equals(d.methodInfo().name) && "$4".equals(d.methodInfo().typeInfo.simpleName)) {
                int expectMm = d.iteration() == 0 ? Level.DELAY : Level.TRUE;
                Assert.assertEquals(expectMm, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
            }
        };

        testUtilClass(List.of("UpgradableBooleanMap"), 0, 0, new DebugConfiguration.Builder()
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
