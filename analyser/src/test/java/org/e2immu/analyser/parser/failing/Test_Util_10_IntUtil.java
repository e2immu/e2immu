
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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.IntUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class Test_Util_10_IntUtil extends CommonTestRunner {

    public Test_Util_10_IntUtil() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        testSupportAndUtilClasses(List.of(IntUtil.class), 0, 0, new DebugConfiguration.Builder()
                .build());
    }

}
