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

package org.e2immu.analyser.parser.failing;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test_24_TypeParameters extends CommonTestRunner {
    public Test_24_TypeParameters() {
        super(true);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        TypeInfo collection = typeMap.get(Collection.class);
        assertNotNull(collection);
        MethodInfo stream = collection.typeInspection.get().methods().stream().filter(m -> m.name.equals("stream")).findAny().orElseThrow();
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV,
                stream.methodAnalysis.get().getProperty(Property.NOT_NULL_EXPRESSION));
    };

    @Test
    public void test_0() throws IOException {
        final String TYPE = "org.e2immu.analyser.testexample.TypeParameters_0";
        final String STRINGS = TYPE+".strings";

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("TypeParameters_0".equals(d.methodInfo().name) && STRINGS.equals(d.variableName())) {
                assertEquals("input.stream().map(C::new).collect(Collectors.toList())", d.currentValue().toString());
                assertEquals("this.strings:0", d.variableInfo().getLinkedVariables().toString());
            }
        };

        testClass("TypeParameters_0", 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("TypeParameters_1", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

    @Test
    public void test_2() throws IOException {
        testClass("TypeParameters_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_3() throws IOException {
        testClass("TypeParameters_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
