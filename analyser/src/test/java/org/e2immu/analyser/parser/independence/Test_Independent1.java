
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

package org.e2immu.analyser.parser.independence;


import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Independent1 extends CommonTestRunner {

    public Test_Independent1() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                assertEquals("0", d.statementId());
                if (d.variable() instanceof ParameterInfo pi && "consumer".equals(pi.name)) {
                    assertDv(d, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                    assertEquals("nullable instance type Consumer<T>/*@Identity*//*@IgnoreMods*/",
                            d.currentValue().toString());
                    assertDv(d, DV.TRUE_DV, Property.CONTEXT_MODIFIED); // not transferred to parameter
                }
                if (d.variable() instanceof FieldReference fr && "t".equals(fr.fieldInfo.name)) {
                    assertTrue(fr.scopeIsThis());
                    assertDv(d, 1, DV.FALSE_DV, Property.CONTEXT_MODIFIED); // because context is @Independent1 FIXME implement!
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("visit".equals(d.methodInfo().name)) {
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);
                // implicitly present:
                assertDv(d.p(0), MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
                assertDv(d.p(0), DV.FALSE_DV, Property.MODIFIED_VARIABLE); // because of @IgnoreModifications
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("t".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
            }
        };

        testClass("Independent1_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Independent1_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2".equals(d.typeInfo().simpleName)) {
                assertEquals("Type param T", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Independent1_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_2_1() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2_1".equals(d.typeInfo().simpleName)) {
                // String is not transparent (new String[])
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Independent1_2_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_2_2() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("Independent1_2_2".equals(d.typeInfo().simpleName)) {
                // String is not transparent (new String[])
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Independent1_2_2", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("Independent1_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3_1() throws IOException {
        testClass("Independent1_3_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4() throws IOException {
        testClass("Independent1_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_4_1() throws IOException {
        testClass("Independent1_4_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_5() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ImmutableArrayOfTransparentOnes".equals(d.typeInfo().simpleName)) {
                // we're using One[].clone() to avoid making One explicit
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Independent1_5", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }


    @Test
    public void test_6() throws IOException {
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("ImmutableArrayOfOnes".equals(d.typeInfo().simpleName)) {
                // new One[size] makes One explicit
                assertEquals("", d.typeAnalysis().getTransparentTypes().toString());
            }
        };
        testClass("Independent1_6", 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .build());
    }
}
