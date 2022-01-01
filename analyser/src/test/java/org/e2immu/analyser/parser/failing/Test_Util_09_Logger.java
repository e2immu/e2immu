
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Util_09_Logger extends CommonTestRunner {

    public Test_Util_09_Logger() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {

            if ("log".equals(d.methodInfo().name) && d.methodAnalysis().getParameterAnalyses().size() == 4) {
                ParameterAnalysis p3 = d.methodAnalysis().getParameterAnalyses().get(3);
                // ignore mods not explicitly set, but because it is an abstract method in java.util.function
                assertEquals(DV.TRUE_DV, p3.getProperty(Property.IGNORE_MODIFICATIONS));
                assertEquals(DV.FALSE_DV, p3.getProperty(Property.MODIFIED_VARIABLE));
            }
        };

        testSupportAndUtilClasses(List.of(Logger.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
