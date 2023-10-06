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

import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_56_Fluent_AAPI extends CommonTestRunner {
    public Test_56_Fluent_AAPI() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("-----S---S--S---SFM-SFM--SFMT--", d.delaySequence());
        testClass(List.of("a.IFluent_0", "Fluent_0"), 0, 1, new DebugConfiguration.Builder()
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(), new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder()
                        .addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS)
                        .build());
    }

}
