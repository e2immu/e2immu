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

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_64_StaticBlock extends CommonTestRunner {

    public Test_64_StaticBlock() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("StaticBlock_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // basics
    @Test
    public void test_1() throws IOException {
        testClass("StaticBlock_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // @Variable, assignment in constructor
    @Test
    public void test_2() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("map".equals(d.fieldInfo().name)) {
                assertEquals(2, ((FieldAnalysisImpl.Builder) d.fieldAnalysis()).getValues().size());
            }
        };

        testClass("StaticBlock_2", 0, 1, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

    // @Variable, assignment in sub
    // potential null pointer
    @Test
    public void test_3() throws IOException {
        testClass("StaticBlock_3", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    // @Modified, modification in sub
    @Test
    public void test_4() throws IOException {
        testClass("StaticBlock_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
