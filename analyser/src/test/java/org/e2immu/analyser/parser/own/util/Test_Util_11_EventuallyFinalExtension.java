
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

import org.e2immu.analyser.config.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.EventuallyFinalExtension;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class Test_Util_11_EventuallyFinalExtension extends CommonTestRunner {

    public Test_Util_11_EventuallyFinalExtension() {
        super(true);
    }

    // FIXME this is a bit of a silly test now, because Configuration is not included
    @Test
    public void test() throws IOException {
        testSupportAndUtilClasses(List.of(EventuallyFinalExtension.class),
                1, 1, new DebugConfiguration.Builder()
                .build());
    }

}
