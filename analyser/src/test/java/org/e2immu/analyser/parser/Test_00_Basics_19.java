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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_00_Basics_19 extends CommonTestRunner {

    public Test_00_Basics_19() {
        super(false);
    }

    @Test
    public void test() throws IOException {

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo map  = typeMap.get(Map.class);
            MethodInfo put = map.findUniqueMethod("put", 2);
            ParameterInfo put0Key = put.methodInspection.get().getParameters().get(0);
            ParameterAnalysis put0KeyAnalysis = put0Key.parameterAnalysis.get();

            // unbound parameter type, so @NotModified by default
            assertEquals(Level.TRUE, put0KeyAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));
            assertEquals(MultiLevel.NULLABLE, put0KeyAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            assertEquals(MultiLevel.MUTABLE, put0KeyAnalysis.getProperty(VariableProperty.IMMUTABLE));

            // no idea if DEP_1 or DEP_2, but INDEPENDENT because unbound
            assertEquals(MultiLevel.INDEPENDENT, put0KeyAnalysis.getProperty(VariableProperty.INDEPENDENT));
        };

        testClass("Basics_19", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }
}
