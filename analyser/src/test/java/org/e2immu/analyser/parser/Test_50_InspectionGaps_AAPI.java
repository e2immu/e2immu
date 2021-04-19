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
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.testexample.InspectionGaps_1;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
tries to catch the remaining problems with the inspection system
 */
public class Test_50_InspectionGaps_AAPI extends CommonTestRunner {

    public Test_50_InspectionGaps_AAPI() {
        super(true);
    }

    @Test
    public void test_0() throws IOException {
        testClass("InspectionGaps_0", 0, 0, new DebugConfiguration.Builder()
                .build());

    }

    @Test
    public void test_1() throws IOException {
        TypeContext typeContext = testClass("InspectionGaps_1", 0, 0, new DebugConfiguration.Builder()
                .build());

        TypeInfo typeInfo = typeContext.getFullyQualified(InspectionGaps_1.class);

        assertEquals("org.e2immu.analyser.testexample.InspectionGaps_1.method1(M0 extends java.util.Set<M0>)," +
                        "org.e2immu.analyser.testexample.InspectionGaps_1.method2(M0 extends java.util.List<M0>)," +
                        "org.e2immu.analyser.testexample.InspectionGaps_1.method3(java.lang.String)",
                typeInfo.typeInspection.get().methods()
                        .stream().map(m -> m.distinguishingName).sorted().collect(Collectors.joining(",")));
    }
}
