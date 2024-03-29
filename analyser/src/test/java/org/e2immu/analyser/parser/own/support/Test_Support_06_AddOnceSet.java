
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.AddOnceSet;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class Test_Support_06_AddOnceSet extends CommonTestRunner {

    public Test_Support_06_AddOnceSet() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo keySet = map.findUniqueMethod("keySet", 0);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                    keySet.methodAnalysis.get().getProperty(Property.NOT_NULL_EXPRESSION));
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("AddOnceSet".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param V", d.typeAnalysis().getTransparentTypes().toString());

                assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE1().toString());
                if (d.iteration() >= 2) {
                    assertEquals("{frozen=!frozen}", d.typeAnalysis().getApprovedPreconditionsE2().toString());
                    assertEquals("[set]", d.typeAnalysis().getGuardedByEventuallyImmutableFields().toString());
                }
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("contains".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.NOT_NULL_PARAMETER);
            }
            if ("add".equals(d.methodInfo().name) && "AddOnceSet".equals(d.methodInfo().typeInfo.simpleName)) {
                String expected = d.iteration() == 0 ? "<precondition>" : "!frozen";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
                assertEquals(d.iteration() <= 1, d.methodAnalysis().eventualStatus().isDelayed());
            }
            if ("ensureNotFrozen".equals(d.methodInfo().name)) {
                String expected = d.iteration() == 0 ? "<precondition>" : "!frozen";
                assertEquals(expected, d.methodAnalysis().getPreconditionForEventual().expression().toString());
            }
        };

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add$Modification$Size".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo v && "v".equals(v.name)) {
                    assertDv(d, 1, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                }
            }
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "frozen".equals(fr.fieldInfo.name)) {
                    fail("In statement: " + d.statementId());
                }
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("get".equals(d.methodInfo().name)) {
                if ("1".equals(d.statementId())) {
                    assertEquals("true", d.state().toString());
                }
            }
        };
        // IMPROVE the warning could go if we use companions with "contains"? (instead of the "true")
        testSupportAndUtilClasses(List.of(AddOnceSet.class, Freezable.class), 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
