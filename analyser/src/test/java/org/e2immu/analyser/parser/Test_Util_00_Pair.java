
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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterAnalysis;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_00_Pair extends CommonTestRunner {

    @Test
    public void test() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("k".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().isTransparentType());
                } else {
                    assertTrue(d.fieldAnalysis().isTransparentType());
                    assertEquals("k", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.fieldAnalysis().getEffectivelyFinalValue() instanceof VariableExpression ve) {
                        assertTrue(ve.variable() instanceof ParameterInfo pi && "k".equals(pi.name));
                    } else fail();
                }
            }
            if ("v".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().isTransparentType());
                } else {
                    assertTrue(d.fieldAnalysis().isTransparentType());
                    assertEquals("v", d.fieldAnalysis().getEffectivelyFinalValue().toString());
                    if (d.fieldAnalysis().getEffectivelyFinalValue() instanceof VariableExpression ve) {
                        assertTrue(ve.variable() instanceof ParameterInfo pi && "v".equals(pi.name));
                    } else fail();
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getV".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("Pair".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
                assertEquals(expectIndependent, p0.getProperty(VariableProperty.INDEPENDENT));
            }
        };

        // fields k and v do not link to the constructor's parameters because they are transparent
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            assertEquals("Type param K, Type param V", d.typeAnalysis().getTransparentTypes().toString());
            int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));

            int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT_1;
            assertEquals(expectIndependent, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
        };

        testSupportAndUtilClasses(List.of(Pair.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
