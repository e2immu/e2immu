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

package org.e2immu.analyser.program;

import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.config.DebugConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TestBasics_0 extends CommonTestRunner {

    @Test
    public void testALL() throws IOException {
        testClass("Basics_0", 0, 0, AnalyserProgram.from(AnalyserProgram.Step.ALL),
                new DebugConfiguration.Builder()
                .build());
    }

    @Test
    public void testFieldFinal() throws IOException {
        testClass("Basics_0", 0, 0,
                AnalyserProgram.from(AnalyserProgram.Step.FIELD_FINAL),
                new DebugConfiguration.Builder()
                        .build());
    }
}
