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

package org.e2immu.analyser.parser.start;

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class Test_25_Record extends CommonTestRunner {

    public Test_25_Record() {
        super(false);
    }

    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("string".equals(d.methodInfo().name)) {
                assertEquals(4, d.methodInfo().getComplexity());
                String sv = d.iteration() == 0 ? "<m:string>" : "/*inline string*/string";
                assertEquals(sv, d.methodAnalysis().getSingleReturnValue().toString());
                MethodInspection mi = d.methodInfo().methodInspection.get();
                Block block = mi.getMethodBody();
                assertFalse(block.isEmpty());
            }
        };
        testClass("Record_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    @Test
    public void test_1() throws IOException {
        testClass("Record_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }
}
