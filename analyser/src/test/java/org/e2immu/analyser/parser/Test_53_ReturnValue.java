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
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_53_ReturnValue extends CommonTestRunner {

    public Test_53_ReturnValue() {
        super(true);
    }

    // Non-modifying formula; trivial test
    @Test
    public void test_0() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("cube".equals(d.methodInfo().name)) {
                if (d.iteration() > 0) {
                    assertEquals("i*i*i", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };
        testClass("ReturnValue_0", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

    // Modifying method
    @Test
    public void test_1() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("nextInt".equals(d.methodInfo().name)) {
                if (d.iteration() > 2) {
                    // FIXME do we want this?? I'd say NO
                    assertEquals("random.next()%max", d.methodAnalysis().getSingleReturnValue().toString());
                }
            }
        };

        testClass("ReturnValue_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
