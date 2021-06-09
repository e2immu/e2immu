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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_45_Project_AAPI extends CommonTestRunner {

    public Test_45_Project_AAPI() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map = typeMap.get(Map.class);
            MethodInfo get = map.findUniqueMethod("get", 1);
            assertEquals(MultiLevel.NULLABLE, get.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        };

        testClass("Project_0", 1, 4, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Project_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    /*

     */
    @Test
    public void test_2() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.EXTERNAL_PROPAGATE_MOD));
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeAnalysis stringAnalysis = typeMap.getPrimitives().stringTypeInfo.typeAnalysis.get();
            assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, stringAnalysis.getProperty(VariableProperty.IMMUTABLE));
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Container".equals(d.methodInfo().name) && d.methodInfo().isConstructor) {
                ParameterAnalysis p0 = d.parameterAnalyses().get(0);
                int expectMom = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMom, p0.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
                int expectCm = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectCm, p0.getProperty(VariableProperty.CONTEXT_MODIFIED));
                int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
                assertEquals(expectMv, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        };

        testClass("Project_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                if ("entry".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        // for(Map.Entry entry: ... .entrySet())
                        int expectCnn = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                        assertEquals(expectCnn, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
                if ("entry$0".equals(d.variableName())) {
                    if ("0".equals(d.statementId())) {
                        assertTrue(d.iteration() > 0);
                        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, d.getProperty(VariableProperty.CONTEXT_NOT_NULL));
                    }
                }
            }
        };
        testClass("Project_3", 1, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
