
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

import static org.junit.jupiter.api.Assertions.*;

/*
With respect to commutable: the absolute minimum has been implemented in e2immu.
The real code resides in JFocus.
 */
public class Test_Commutable extends CommonTestRunner {
    public Test_Commutable() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {

        // expect warning that expressions are exactly the same
        testClass("Commutable_0", 0, 1, new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void test_1() throws IOException {

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getHeaders".equals(d.methodInfo().name)) {
                assertNull(d.methodAnalysis().getCommutableData());
            }
            if ("setTimeout".equals(d.methodInfo().name)) {
                assertTrue(d.methodAnalysis().getCommutableData().isDefault());
            }
            if ("addHeader".equals(d.methodInfo().name)) {
                assertEquals("header", d.methodAnalysis().getCommutableData().seq());
            }
        };

        testClass("Commutable_1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }
}
