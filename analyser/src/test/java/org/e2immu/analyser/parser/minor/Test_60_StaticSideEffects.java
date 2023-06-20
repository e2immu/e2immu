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

package org.e2immu.analyser.parser.minor;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_60_StaticSideEffects extends CommonTestRunner {

    // AtomicInteger, System.out, ...
    public Test_60_StaticSideEffects() {
        super(false);
    }

    @Test
    public void test_6() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if (d.methodInfo().isConstructor && n == 1) {
                if (d.variable() instanceof FieldReference fr && "generator".equals(fr.fieldInfo.name)) {
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertCurrentValue(d, 1, "instance type AtomicInteger");
                }
            }
            if (d.methodInfo().isConstructor && n == 0) {
                if (d.variable() instanceof FieldReference fr && "generator".equals(fr.fieldInfo.name)) {
                    assertDv(d, MultiLevel.EFFECTIVELY_NOT_NULL_DV, Property.CONTEXT_NOT_NULL);
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED);
                    assertCurrentValue(d, 1, "instance type AtomicInteger");
                }
            }
        };
        testClass("StaticSideEffects_6", 0, 1, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }
}
