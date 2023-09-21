
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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.util.WeightedGraph;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Test_Util_12_WeightedGraph extends CommonTestRunner {

    public Test_Util_12_WeightedGraph() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                if ("3.0.0".equals(d.statementId())) {
                    DV guaranteedToBeReachedInMethod = d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod();
                    if (d.iteration() < 40) {
                        assertTrue(guaranteedToBeReachedInMethod.isDelayed());
                    } else {
                        assertEquals("", guaranteedToBeReachedInMethod.toString());
                    }
                }
                if ("3".equals(d.statementId())) {
                    DV guaranteedToBeReachedInMethod = d.statementAnalysis().flowData().getGuaranteedToBeReachedInMethod();
                    assertEquals(FlowData.ALWAYS, guaranteedToBeReachedInMethod);

                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("accept".equals(d.methodInfo().name) && "recursivelyComputeLinks".equals(d.enclosingMethod().name)) {
                assertEquals("$1", d.methodInfo().typeInfo.simpleName);

            }
            if ("recursivelyComputeLinks".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.FALSE_DV, Property.TEMP_MODIFIED_METHOD);
                assertDv(d, 2, DV.FALSE_DV, Property.MODIFIED_METHOD);
            }
        };
        testSupportAndUtilClasses(List.of(WeightedGraph.class), 0, 0, new DebugConfiguration.Builder()
                .addStatementAnalyserVisitor(statementAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .build());
    }

}
