
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

import org.e2immu.analyser.analyser.MethodLevelData;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.Lazy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Support_05_Lazy extends CommonTestRunner {

    public Test_Support_05_Lazy() {
        super(true);
    }

    StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
        if ("get".equals(d.methodInfo().name)) {
            if (d.variable() instanceof FieldReference s && "supplier".equals(s.fieldInfo.name)) {
                assertFalse(d.variableInfo().isAssigned());
            }
            if (d.variable() instanceof FieldReference t && "t".equals(t.fieldInfo.name)) {
                if ("1".equals(d.statementId())) {
                    if (d.iteration() > 0) {
                        assertEquals("supplier.get()/*@NotNull*/", d.currentValue().toString());
                        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL,
                                d.getPropertyOfCurrentValue(VariableProperty.NOT_NULL_EXPRESSION));
                        assertEquals(1, d.currentValue().variables().size());
                    }
                }
            }
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        int iteration = d.iteration();
        if ("t".equals(d.fieldInfo().name) && iteration > 0) {
            assertEquals(Level.FALSE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
        }
        if ("supplier".equals(d.fieldInfo().name)) {
            assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.FINAL));
            if (iteration > 0) assertNotNull(d.fieldAnalysis().getEffectivelyFinalValue());
        }
    };

    StatementAnalyserVisitor statementAnalyserVisitor = d -> {
        if (d.iteration() > 0 && "get".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                // this can be picked up as a precondition for the method
                assertEquals("null==t$0", d.state().toString());
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if (!"Lazy".equals(d.methodInfo().typeInfo.simpleName)) return;

        FieldInfo supplier = d.methodInfo().typeInfo.getFieldByName("supplier", true);
        MethodLevelData methodLevelData = d.methodAnalysis().methodLevelData();

        if ("Lazy".equals(d.methodInfo().name)) {
            VariableInfo tv = d.getFieldAsVariable(supplier);
            assert tv != null;
            assertFalse(tv.isDelayed());

            ParameterInfo supplierParam = d.methodInfo().methodInspection.get().getParameters().get(0);
            assertEquals("supplierParam", supplierParam.name);
        }
        if ("get".equals(d.methodInfo().name)) {
            VariableInfo vi = d.getFieldAsVariable(supplier);
            assert vi != null;
            assertFalse(vi.isAssigned());

            VariableInfo ret = d.getReturnAsVariable();
            if (d.iteration() >= 1) {
                assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, ret.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                assertTrue(methodLevelData.linksHaveBeenEstablished.isSet());
            }
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if ("Lazy".equals(d.typeInfo().simpleName)) {
            assertEquals("Type java.util.function.Supplier<T>,Type param T",
                    d.typeAnalysis().getTransparentTypes().toString());
        }
    };

    @Test
    public void test() throws IOException {
        testSupportAndUtilClasses(List.of(Lazy.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
