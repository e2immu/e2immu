
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.CommonTestRunner;
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
                assertTrue(d.fieldAnalysis().isTransparentType().isDone());
                assertEquals("k", d.fieldAnalysis().getValue().toString());
                if (d.fieldAnalysis().getValue() instanceof VariableExpression ve) {
                    assertTrue(ve.variable() instanceof ParameterInfo pi && "k".equals(pi.name));
                } else fail();
            }
            if ("v".equals(d.fieldInfo().name)) {
                assertTrue(d.fieldAnalysis().isTransparentType().isDone());
                assertEquals("v", d.fieldAnalysis().getValue().toString());
                if (d.fieldAnalysis().getValue() instanceof VariableExpression ve) {
                    assertTrue(ve.variable() instanceof ParameterInfo pi && "v".equals(pi.name));
                } else fail();
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getV".equals(d.methodInfo().name)) {
                assertDv(d, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
            if ("Pair".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
            }
        };

        // fields k and v do not link to the constructor's parameters because they are transparent
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            assertEquals("Type param K, Type param V", d.typeAnalysis().getTransparentTypes().toString());
            assertDv(d, 1, MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, Property.IMMUTABLE);
            assertDv(d, 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
        };

        testSupportAndUtilClasses(List.of(Pair.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

}
