
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.*;
import org.e2immu.support.FirstThen;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                String expectCondition = switch (d.iteration()) {
                    case 0 -> "null==<f:first>";
                    case 1 -> "null==<f*:first>";
                    default -> "null==first";
                };
                assertEquals(expectCondition, d.condition().toString());
            }
        }
        if ("get".equals(d.methodInfo().name)) {
            if ("1".equals(d.statementId())) {
                String expectPre = switch (d.iteration()) {
                    case 0 -> "null!=<f:then>";
                    case 1 -> "null!=<vp:then:initial:this.first@Method_set_1.0.0-C;no precondition info@Method_set_1.0.0-E;values:this.then@Field_then>";
                    case 2 -> "null!=<vp:then:break_init_delay:this.first@Method_set_1.0.0-C;no precondition info@Method_set_1.0.0-E;values:this.then@Field_then>";
                    default -> "null!=then";
                };
                assertEquals(expectPre, d.statementAnalysis().stateData().getPrecondition().expression().toString());
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
                assertEquals(DV.FALSE_DV, d.getProperty(Property.MODIFIED_VARIABLE));
            }
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        String name = d.methodInfo().name;

        if ("set".equals(name)) {
            String expect = switch (d.iteration()) {
                case 0 -> "Precondition[expression=<precondition>&&null!=<f:first>, causes=[escape]]";
                case 1 -> "Precondition[expression=<precondition>&&null!=<f*:first>, causes=[escape]]";
                default -> "Precondition[expression=null!=first, causes=[escape]]";
            };
            assertEquals(expect, d.methodAnalysis().getPrecondition().toString());
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
            assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);

        }

        if ("equals".equals(name)) {
            assertDv(d, DV.FALSE_DV, Property.MODIFIED_METHOD);
            assertDv(d.p(0), 1, DV.FALSE_DV, Property.MODIFIED_VARIABLE);
        }
    };

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        assertEquals("Type param S,Type param T", d.typeAnalysis().getTransparentTypes().types()
                .stream().map(Object::toString).sorted().collect(Collectors.joining(",")));
        assertEquals(d.iteration() > 1, d.typeAnalysis().approvedPreconditionsStatus(false).isDone());
    };

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo objects = typeMap.get(Objects.class);
        MethodInfo hash = objects.typeInspection.get().methods().stream().filter(m -> m.name.equals("hash")).findFirst().orElseThrow();
        ParameterInfo objectsParam = hash.methodInspection.get().getParameters().get(0);
        assertEquals(DV.FALSE_DV, objectsParam.parameterAnalysis.get().getProperty(Property.MODIFIED_VARIABLE));
    };

    @Test
    public void test() throws IOException {
        testSupportAndUtilClasses(List.of(FirstThen.class), 0, 0, new DebugConfiguration.Builder()
            //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
            //    .addTypeMapVisitor(typeMapVisitor)
             //   .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
            //    .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
             //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .build());
    }

}
