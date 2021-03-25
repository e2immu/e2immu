
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
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_Support_06_AddOnceSet extends CommonTestRunner {

    public Test_Support_06_AddOnceSet() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo keySet = map.findUniqueMethod("keySet", 0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                    keySet.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("AddOnceSet".equals(d.typeInfo().simpleName)) {
                assertEquals("[Type param V]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());

                assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE1().toString());
                if (d.iteration() >= 2) {
                    assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("contains".equals(d.methodInfo().name)) {
                ParameterAnalysis v = d.parameterAnalyses().get(0);
                int expectNnp = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                assertEquals(expectNnp, v.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            }
            if ("add".equals(d.methodInfo().name) && "AddOnceSet".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.iteration() <= 1) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("!frozen", d.methodAnalysis()
                            .getPreconditionForEventual().expression().toString());
                }
            }
            if("ensureNotFrozen".equals(d.methodInfo().name)) {
                if (d.iteration() == 0) {
                    assertNull(d.methodAnalysis().getPreconditionForEventual());
                } else {
                    assertEquals("!frozen", d.methodAnalysis()
                            .getPreconditionForEventual().expression().toString());
                }
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add$Modification$Size".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo v && "v".equals(v.name)) {
                    int expectNnc = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNnc, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                }
            }
        };

        testSupportClass(List.of("AddOnceSet", "Freezable"), 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}