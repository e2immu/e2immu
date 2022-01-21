
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
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_ExternalContainer extends CommonTestRunner {
    public Test_ExternalContainer() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("print".equals(d.methodInfo().name)) {
                assertDv(d.p(0), DV.TRUE_DV, Property.CONTAINER);
            }
        };
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if("set".equals(d.fieldInfo().name)) {
                // Context container comes from the "go" method
                // External container from the initialisation
            }
        };
        testClass("ExternalContainer_0", 1, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("ExternalContainer_1", 0, 0, new DebugConfiguration.Builder().build());
    }
}
