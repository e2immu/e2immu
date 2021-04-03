
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

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Support_04_FirstThen extends CommonTestRunner {

    public Test_Support_04_FirstThen() {
        super(true);
    }

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if ("equals".equals(d.methodInfo().name) && "2".equals(d.statementId())) {
            assertEquals("null!=o&&o.getClass()==this.getClass()&&o!=this", d.state().toString());
        }
        if ("set".equals(d.methodInfo().name)) {
            if ("1.0.0.0.0".equals(d.statementId())) {
                String expectCondition = d.iteration() == 0 ? "null==<f:first>" : "null==org.e2immu.support.FirstThen.first$0";
                assertEquals(expectCondition, d.condition().toString());
            }
        }
        if ("get".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                String expectPre = d.iteration() == 0 ? "null!=<f:then>" : "null!=then";
                assertEquals(expectPre, d.statementAnalysis().stateData.precondition.get().expression().toString());
            }
        }
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("getFirst".equals(d.methodInfo().name) && "FirstThen.this.first".equals(d.variableName())) {
            if ("0".equals(d.statementId())) {
                assertTrue(d.variableInfo().isRead());
            }
            if ("1".equals(d.statementId())) {
                assertTrue(d.variableInfo().isRead());
            }
        }
        if ("equals".equals(d.methodInfo().name) && "o".equals(d.variableName())) {
            if ("2".equals(d.statementId())) {
                assertEquals(Level.FALSE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("set".equals(name)) {
            if (d.iteration() == 0) {
                assertNull(d.methodAnalysis().getPrecondition());
            } else {
                assertEquals("null!=first", d.methodAnalysis().getPrecondition().expression().toString());
            }
        }

        if ("getFirst".equals(name)) {
            FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
            VariableInfo vi = d.getFieldAsVariable(first);
            assert vi != null;
            assertTrue(vi.isRead());
        }

        if ("hashCode".equals(name)) {
            FieldInfo first = d.methodInfo().typeInfo.getFieldByName("first", true);
            VariableInfo vi = d.getFieldAsVariable(first);
            assert vi != null;
            assertTrue(vi.isRead());
            assertEquals(Level.DELAY, vi.getProperty(VariableProperty.METHOD_CALLED));

            int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
        }

        if ("equals".equals(name)) {
            int expectModified = d.iteration() <= 1 ? Level.DELAY : Level.FALSE;
            assertEquals(expectModified, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));

            ParameterAnalysis o = d.parameterAnalyses().get(0);
            int expectMv = d.iteration() == 0 ? Level.DELAY : Level.FALSE;
            assertEquals(expectMv, o.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        assertEquals("Type param S,Type param T", d.typeAnalysis().getImplicitlyImmutableDataTypes()
                .stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
        assertEquals(d.iteration() > 0, d.typeAnalysis().approvedPreconditionsIsFrozen(false));
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get(Objects.class);
        MethodInfo hash = objects.typeInspection.get().methods().stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
        ParameterInfo objectsParam = hash.methodInspection.get().getParameters().get(0);
        assertEquals(Level.FALSE, objectsParam.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED_VARIABLE));
    };

    @Test
    public void test() throws IOException {
        testSupportClass(List.of("FirstThen"), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
