
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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestImmutabilityAnnotations extends CommonTestRunner {
    public TestImmutabilityAnnotations() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo set = typeMap.get(Set.class);
        MethodInfo setOf2 = set.findUniqueMethod("of", 2);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                setOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));

        TypeInfo list = typeMap.get(List.class);
        MethodInfo listOf2 = list.findUniqueMethod("of", 2);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL,
                listOf2.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    };

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("generateBefore".equals(d.methodInfo().name) && "0".equals(d.statementId()) && "list".equals(d.variableName())) {
            assertEquals("java.util.List.of(a, b)", d.currentValue().toString());
            int notNull = d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION);
            assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
        }
        if ("setFirst".equals(d.methodInfo().name) && "ManyTs.this.ts2".equals(d.variableName())) {
            assertEquals(Level.TRUE, d.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("setFirst".equals(d.methodInfo().name)) {
            FieldInfo ts2 = d.methodInfo().typeInfo.getFieldByName("ts2", true);
            VariableInfo tv = d.getFieldAsVariable(ts2);
            if (d.iteration() > 0) {
                assert tv != null;
                assertEquals(Level.TRUE, tv.getProperty(VariableProperty.MODIFIED_VARIABLE));
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("ts2".equals(d.fieldInfo().name) && d.iteration() > 1) {
            assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
        }
    };

    @Test
    public void test() throws IOException {
        testClass("ImmutabilityAnnotations", 0, 1, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());

    }

}
