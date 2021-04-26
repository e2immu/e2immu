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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class Test_51_TypeIndependence extends CommonTestRunner {

    public Test_51_TypeIndependence() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("next".equals(d.methodInfo().name) && "IteratorImpl".equals(d.methodInfo().typeInfo.simpleName)) {
                if (d.variable() instanceof ReturnVariable) {
                    String expectValue = d.iteration() == 0 ?
                            "<v:org.e2immu.analyser.testexample.TypeIndependence_0.elements[<f:i>]>" : "instance type T";
                    assertEquals(expectValue, d.currentValue().toString());
                    int expectNne = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_NOT_NULL;
                    assertEquals(expectNne, d.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("elements".equals(d.fieldInfo().name)) {
                assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE_NOT_E2IMMUTABLE, d.fieldAnalysis()
                        .getProperty(VariableProperty.EXTERNAL_IMMUTABLE));
                if (d.iteration() == 0) {
                    assertNull(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                } else {
                    assertFalse(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                }
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            assertEquals("[Type param T]", d.typeAnalysis().getImplicitlyImmutableDataTypes().toString());
            if ("IteratorImpl".equals(d.typeInfo().simpleName)) {
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.INDEPENDENT;
                assertEquals(expectIndependent, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
            if ("TypeIndependence_0".equals(d.typeInfo().simpleName)) {
                int expectImmutable = d.iteration() == 0 ? Level.DELAY : MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                assertEquals(expectImmutable, d.typeAnalysis().getProperty(VariableProperty.IMMUTABLE));
            }
        };

        testClass("TypeIndependence_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("TypeIndependence_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
