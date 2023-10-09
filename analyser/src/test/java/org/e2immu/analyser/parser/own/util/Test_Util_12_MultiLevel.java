
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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.*;
import org.e2immu.analyser.analyser.util.WeightedGraph;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it;
import static org.e2immu.analyser.parser.VisitorTestSupport.IterationInfo.it0;
import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_12_MultiLevel extends CommonTestRunner {

    public Test_Util_12_MultiLevel() {
        super(true);
    }

    /*
    tests an unused Enum element inside a normal class
     */
    @Test
    public void test_1() throws IOException {
        List<Class<?>> classes = List.of(MultiLevel.class);

        testSupportAndUtilClasses(classes,
                0, 0, new DebugConfiguration.Builder()
                        //     .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                        //    .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        //      .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        //     .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build());
    }

    @Test
    public void test_2() throws IOException {
        List<Class<?>> classes = List.of(
                AbstractDelay.class, CausesOfDelay.class, CauseOfDelay.class, DV.class, Inconclusive.class,
                NoDelay.class, NotDelayed.class, ProgressWrapper.class, AnalysisStatus.class,

                MultiLevel.class);

        testSupportAndUtilClasses(classes, 0, 0, new DebugConfiguration.Builder().build());
    }

}
