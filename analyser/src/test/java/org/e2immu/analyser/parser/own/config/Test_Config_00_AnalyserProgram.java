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

package org.e2immu.analyser.parser.own.config;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

public class Test_Config_00_AnalyserProgram extends CommonTestRunner {

    @Test
    public void test() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("from".equals(d.methodInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FLUENT);
            }
        };
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("AnalyserProgram".equals(d.typeInfo().simpleName)) {
                assertDv(d, 2, MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV, Property.IMMUTABLE);
            }
        };
        testSupportAndUtilClasses(List.of(AnalyserProgram.class), 0, 1,
                new DebugConfiguration.Builder()
                    //    .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                    //    .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder()
                        .setComputeContextPropertiesOverAllMethods(true)
                        .setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
