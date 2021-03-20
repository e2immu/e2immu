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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.analyser.model.MethodInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_15_InlineMethods extends CommonTestRunner {
    public Test_15_InlineMethods() {
        super(false);
    }

    TypeMapVisitor typeMapVisitor = typeMap -> {
        MethodInfo unaryMinusInt = typeMap.getPrimitives().unaryMinusOperatorInt;
        assertEquals("int.-(int)", unaryMinusInt.fullyQualifiedName());
    };

    @Test
    public void test() throws IOException {
        testClass("InlineMethods_0", 0, 0, new DebugConfiguration.Builder()
                .addTypeMapVisitor(typeMapVisitor)
                .build());
    }

}
