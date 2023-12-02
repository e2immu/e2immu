
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

package org.e2immu.analyser.parser.own.util;

import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.BreakDelayVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Util_13_BreakDelayLevel extends CommonTestRunner {

    public Test_Util_13_BreakDelayLevel() {
        super(true);
    }

    @Test
    public void test_1() throws IOException {
        List<Class<?>> classes = List.of(BreakDelayLevel.class);

        // FIXME does this really have to take so long??
        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("----S--SF--SF--SF--SF--SF--SF--SFM-SFMT--", d.delaySequence());

        testSupportAndUtilClasses(classes, 0, 0, new DebugConfiguration.Builder()
                .addBreakDelayVisitor(breakDelayVisitor)
                .build());
    }

}