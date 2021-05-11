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
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class Test_52_Var extends CommonTestRunner {

    public Test_52_Var() {
        super(false);
    }

    // basics
    @Test
    public void test_0() throws IOException {
        testClass("Var_0", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // var set = new HashSet<>(xes);
    @Test
    public void test_1() throws IOException {
        testClass("Var_1", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // for (var x : xes) { ... } direct child
    @Test
    public void test_2() throws IOException {
        testClass("Var_2", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // for (var x : xes) { ... } indirect child
    @Test
    public void test_3() throws IOException {
        testClass("Var_3", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // lambda
    // TODO add a test for @NotNull on the function returned
    @Test
    public void test_4() throws IOException {
        testClass("Var_4", 0, 0, new DebugConfiguration.Builder()
                .build());
    }

    // try
    @Test
    public void test_5() throws IOException {
        // expect one potential null ptr (result of sw.append())
        testClass("Var_5", 0, 1, new DebugConfiguration.Builder()
                .build());
    }
}
