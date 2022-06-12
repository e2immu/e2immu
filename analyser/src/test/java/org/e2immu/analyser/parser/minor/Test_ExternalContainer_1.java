
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
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_ExternalContainer_1 extends CommonTestRunner {
    public Test_ExternalContainer_1() {
        super(true);
    }

    @Test
    public void test_1() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "iField".equals(fr.fieldInfo.name)) {
                    if ("0".equals(d.statementId())) {
                        assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                        assertDv(d, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                    if ("1".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
            if ("go".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "myNonContainer".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 3, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
                if (d.variable() instanceof FieldReference fr && "myContainerLinkedToParameter".equals(fr.fieldInfo.name)) {
                    if ("2".equals(d.statementId())) {
                        assertDv(d, 6, MultiLevel.NOT_CONTAINER_DV, Property.CONTEXT_CONTAINER);
                    }
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
                assertDv(d.p(0), MultiLevel.NOT_IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("ExternalContainer_0".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
            if ("setI".equals(d.methodInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyNonContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d.p(0), 1, DV.TRUE_DV, Property.MODIFIED_VARIABLE);
                assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "MyContainer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
            }
            if ("accept".equals(d.methodInfo().name) && "Consumer".equals(d.methodInfo().typeInfo.simpleName)) {
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(0), DV.TRUE_DV, Property.MODIFIED_VARIABLE);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("myNonContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 2, MultiLevel.NOT_CONTAINER_DV, Property.EXTERNAL_CONTAINER);
            }
            if ("myContainer".equals(d.fieldInfo().name)) {
                assertDv(d, 3, MultiLevel.CONTAINER_DV, Property.EXTERNAL_CONTAINER);
            }
            if ("myContainerLinkedToParameter".equals(d.fieldInfo().name)) {
                assertDv(d, 6, MultiLevel.NOT_CONTAINER_DV, Property.EXTERNAL_CONTAINER);
            }
            if ("iField".equals(d.fieldInfo().name)) {
                assertDv(d, 1, MultiLevel.CONTAINER_DV, Property.EXTERNAL_CONTAINER);
                assertDv(d, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("I".equals(d.typeInfo().simpleName)) {
                assertDv(d, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.CONTAINER_DV, Property.CONTAINER);
            }
            if ("MyNonContainer".equals(d.typeInfo().simpleName)) {
                assertDv(d, 1, MultiLevel.NOT_CONTAINER_DV, Property.CONTAINER);
            }
        };
        testClass("ExternalContainer_1", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
