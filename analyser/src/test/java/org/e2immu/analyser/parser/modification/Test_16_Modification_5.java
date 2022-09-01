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

package org.e2immu.analyser.parser.modification;

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
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_16_Modification_5 extends CommonTestRunner {

    public Test_16_Modification_5() {
        super(true);
    }

    @Test
    public void test5() throws IOException {
        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name) && d.variable() instanceof ParameterInfo p &&
                    "in5".equals(p.name) && "0".equals(d.statementId())) {
                assertEquals(DV.FALSE_DV, d.getProperty(Property.CONTEXT_MODIFIED));
            }
            if ("Modification_5".equals(d.methodInfo().name)
                    && d.variable() instanceof FieldReference fr && "set5".equals(fr.fieldInfo.name)
                    && "0".equals(d.statementId())) {
                String expectValue = "new HashSet<>(in5)/*this.size()==in5.size()*/";
                assertEquals(expectValue, d.currentValue().toString());
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("Modification_5".equals(d.methodInfo().name)) {
                assertTrue(d.methodInfo().isConstructor);
                assertEquals(DV.TRUE_DV, d.methodAnalysis().getProperty(Property.MODIFIED_METHOD));
                assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_OUTSIDE_METHOD);
                assertDv(d.p(0), 1, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set5".equals(d.fieldInfo().name)) {
                assertDv(d, DV.TRUE_DV, Property.FINAL);
                /*
                linked hidden content-wise to in5, but because the intersection is immutable, the field is
                independent, and the parameter as well, and therefore, in5 is @NotModified!
                 */
                assertEquals("in5:4", d.fieldAnalysis().getLinkedVariables().toString());
                assertDv(d, MultiLevel.INDEPENDENT_DV, Property.INDEPENDENT);
            }
        };

        testClass("Modification_5", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }
}
