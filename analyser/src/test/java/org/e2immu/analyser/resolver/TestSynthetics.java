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
import org.e2immu.analyser.config.LogTarget;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.model.IsAssignableFrom;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.TypeMap;
import org.junit.jupiter.api.Test;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.e2immu.analyser.model.IsAssignableFrom.NOT_ASSIGNABLE;
import static org.e2immu.analyser.model.IsAssignableFrom.SAME_UNDERLYING_TYPE;
import static org.junit.jupiter.api.Assertions.*;

public class TestSynthetics {

    @Test
    public void test() throws IOException {
        InputConfiguration inputConfiguration = new InputConfiguration.Builder()
                .setAlternativeJREDirectory(CommonTestRunner.CURRENT_JDK)
                .addSources("src/test/java")
                .addClassPath("jmods/java.base.jmod")
                .addRestrictSourceToPackages("some.unknown.type")
                .build();
        Configuration configuration = new Configuration.Builder()
                .setSkipAnalysis(true)
                .setInputConfiguration(inputConfiguration)
                .addDebugLogTargets(LogTarget.RESOLVER)
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        TypeMap.Builder typeMap = parser.inspectOnlyForTesting();
        parser.preload("java.io");
        TypeInfo fileNameFilter = typeMap.get(FilenameFilter.class);
        assertNotNull(fileNameFilter);
        ParameterizedType ffPt = fileNameFilter.asParameterizedType(typeMap);
        assertTrue(ffPt.isFunctionalInterface(typeMap));

        {
            TypeInfo t3 = typeMap.syntheticFunction(3, true);
            assertEquals("_internal_.SyntheticConsumer3", t3.fullyQualifiedName);
            ParameterizedType pt = t3.asParameterizedType(typeMap);
            assertEquals("Type _internal_.SyntheticConsumer3<P0,P1,P2>", pt.toString());
            assertEquals(3, t3.typeInspection.get().typeParameters().size());

            MethodInfo m3 = t3.findUniqueMethod("accept", 3);
            assertEquals("_internal_.SyntheticConsumer3.accept(P0,P1,P2)", m3.fullyQualifiedName);
            assertTrue(pt.isFunctionalInterface(typeMap));
            MethodTypeParameterMap map = new MethodTypeParameterMap(m3.methodInspection.get(),
                    Map.of(t3.typeInspection.get().typeParameters().get(0),
                            typeMap.getPrimitives().stringParameterizedType()));
            assertTrue(map.isSingleAbstractMethod());
            ParameterizedType c = map.getConcreteTypeOfParameter(typeMap.getPrimitives(), 0);
            assertEquals("Type String", c.toString());
        }
        {
            TypeInfo f2 = typeMap.syntheticFunction(2, false);
            assertEquals("_internal_.SyntheticFunction2", f2.fullyQualifiedName);
            ParameterizedType pt = f2.asParameterizedType(typeMap);
            assertEquals("Type _internal_.SyntheticFunction2<P0,P1,P2>", pt.toString());
            assertEquals(3, f2.typeInspection.get().typeParameters().size());

            MethodInfo m2 = f2.findUniqueMethod("apply", 2);
            assertEquals("_internal_.SyntheticFunction2.apply(P0,P1)", m2.fullyQualifiedName);
            assertTrue(pt.isFunctionalInterface(typeMap));
            assertEquals("Type param P2", m2.returnType().toString());

            TypeInfo biFunction = typeMap.get(BiFunction.class);
            ParameterizedType biPt = biFunction.asParameterizedType(typeMap);
            assertTrue(biPt.isFunctionalInterface(typeMap));

            // important! the synthetic FI is assignable in both directions from BiFunction
            assertEquals(SAME_UNDERLYING_TYPE, new IsAssignableFrom(typeMap, pt, biPt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));
            assertEquals(SAME_UNDERLYING_TYPE, new IsAssignableFrom(typeMap, biPt, pt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));

            // important! the synthetic FI is assignable in both directions from FilenameFilter
            assertEquals(SAME_UNDERLYING_TYPE, new IsAssignableFrom(typeMap, pt, ffPt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));
            assertEquals(SAME_UNDERLYING_TYPE, new IsAssignableFrom(typeMap, ffPt, pt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));

        }
        {
            TypeInfo f1 = typeMap.syntheticFunction(1, false);
            assertEquals("_internal_.SyntheticFunction1", f1.fullyQualifiedName);
            ParameterizedType pt = f1.asParameterizedType(typeMap);
            assertEquals("Type _internal_.SyntheticFunction1<P0,P1>", pt.toString());
            assertEquals(2, f1.typeInspection.get().typeParameters().size());

            MethodInfo m1 = f1.findUniqueMethod("apply", 1);
            assertEquals("_internal_.SyntheticFunction1.apply(P0)", m1.fullyQualifiedName);
            assertTrue(pt.isFunctionalInterface(typeMap));
            assertEquals("Type param P1", m1.returnType().toString());

            // important! the synthetic FI is not assignable in both directions from FilenameFilter, which is a BiFunction!
            assertEquals(NOT_ASSIGNABLE, new IsAssignableFrom(typeMap, pt, ffPt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));
            assertEquals(NOT_ASSIGNABLE, new IsAssignableFrom(typeMap, ffPt, pt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));

            TypeInfo consumer = typeMap.get(Consumer.class);
            ParameterizedType consumerPt = consumer.asParameterizedType(typeMap);

            // the synthetic f1 is assignable to a consumer, but not the other way around
            assertEquals(NOT_ASSIGNABLE, new IsAssignableFrom(typeMap, pt, consumerPt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));
            assertEquals(SAME_UNDERLYING_TYPE, new IsAssignableFrom(typeMap, consumerPt, pt)
                    .execute(true, IsAssignableFrom.Mode.COVARIANT_ERASURE));
        }
    }
}
