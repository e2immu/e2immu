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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Test_GetSet extends CommonTestRunner {

    public Test_GetSet() {
        super(true);
    }


    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("k".equals(d.methodInfo().name)) {
                assertEquals("k", d.methodAnalysis().getSetField().toString());
            }
            if ("l".equals(d.methodInfo().name)) {
                assertEquals("k", d.methodAnalysis().getSetField().toString());
            }
            if ("m".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getSetField());
            }
            if ("getI".equals(d.methodInfo().name)) {
                assertEquals("i", d.methodAnalysis().getSetField().toString());
            }
            if ("setI".equals(d.methodInfo().name)) {
                assertEquals("i", d.methodAnalysis().getSetField().toString());
            }
            if ("setIReturn".equals(d.methodInfo().name)) {
                assertEquals("i", d.methodAnalysis().getSetField().toString());
            }
            if ("setIAlmost".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getSetField());
            }
            if (Set.of("isAbc", "hasAbc", "getAbc").contains(d.methodInfo().name)) {
                assertEquals("abc", d.methodAnalysis().getSetField().toString());
            }
        };

        // 2 errors: wrong annotation values
        testClass("GetSet_0", 2, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
