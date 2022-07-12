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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_16_Modification_0 extends CommonTestRunner {

    public Test_16_Modification_0() {
        super(true);
    }

    @Test
    public void test0() throws IOException {

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                if (d.variable() instanceof FieldReference fr && "set1".equals(fr.fieldInfo.name)) {
                    assertTrue(d.variableInfoContainer().hasEvaluation() && !d.variableInfoContainer().hasMerge());
                    assertTrue(d.variableInfo().isRead());
                    String expectValue = d.iteration() == 0 ? "<f:set1>" : "instance type HashSet<String>/*this.contains(v)&&this.size()>=1*/";
                    assertEquals(expectValue, d.currentValue().toString());
                    assertTrue(d.variableInfo().getLinkedVariables().isEmpty());
                    assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                }
            }
        };

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("set1".equals(d.fieldInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_OUTSIDE_METHOD);

                Expression e = d.fieldAnalysis().getValue();
                assertEquals("instance type HashSet<String>", e.toString());
            }
        };

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo set = typeMap.get(Set.class);
            MethodInfo add = set.findUniqueMethod("add", 1);
            assertEquals(DV.TRUE_DV, add.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            MethodInfo addAll = set.findUniqueMethod("addAll", 1);
            assertEquals(DV.TRUE_DV, addAll.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));
            ParameterInfo first = addAll.methodInspection.get().getParameters().get(0);
            assertEquals(DV.FALSE_DV, first.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));

            MethodInfo size = set.findUniqueMethod("size", 0);
            assertEquals(DV.FALSE_DV, size.methodAnalysis.get().getProperty(Property.MODIFIED_METHOD));

            TypeInfo hashSet = typeMap.get(Set.class);
            assertEquals(MultiLevel.CONTAINER_DV, hashSet.typeAnalysis.get().getProperty(Property.CONTAINER));
        };

        testClass("Modification_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .build());
    }

}
