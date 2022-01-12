
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

package org.e2immu.analyser.parser.basics;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.junit.jupiter.api.Assertions.*;

public class Test_00_Basics_5 extends CommonTestRunner {
    public Test_00_Basics_5() {
        super(true);
    }

    // test here mainly to generate debug information for the output system
    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo i && "input".equals(i.name)) {
                assertDv(d, 0, MultiLevel.NOT_INVOLVED_DV, EXTERNAL_NOT_NULL);
            }
        };

        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name) && "0.0.0".equals(d.statementId())) {
                assertFalse(d.localConditionManager().isDelayed());
                assertEquals("null==s", d.condition().toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo stream = typeMap.get(Stream.class);
            MethodInfo of = stream.findUniqueMethod("of", 1);
            assertFalse(of.methodInspection.get().isDefault());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("apply".equals(d.methodInfo().name)) {
                assertFalse(d.methodInfo().isNotOverridingAnyOtherMethod());
            }
        };

        testClass("Basics_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
