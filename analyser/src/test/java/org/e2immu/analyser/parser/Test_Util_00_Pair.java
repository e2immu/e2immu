
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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Test_Util_00_Pair extends CommonTestRunner {

    @Test
    public void test() throws IOException {

        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("v".equals(d.fieldInfo().name)) {
                if (d.iteration() == 0) assertNull(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
                else assertTrue(d.fieldAnalysis().isOfImplicitlyImmutableDataType());
            }
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("getV".equals(d.methodInfo().name)) {
                int expectIndependent = d.iteration() <= 1 ? Level.DELAY : MultiLevel.DEPENDENT_1;
                assertEquals(expectIndependent, d.methodAnalysis().getProperty(VariableProperty.INDEPENDENT));
            }
        };

        // fields k and v do not link to the constructor's parameters because they are implicitly immutable
        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            int expectIndependent = d.iteration() == 0 ? Level.DELAY : MultiLevel.INDEPENDENT;
            assertEquals(expectIndependent, d.typeAnalysis().getProperty(VariableProperty.INDEPENDENT));
        };

        testUtilClass(List.of("Pair"), 0, 0, new DebugConfiguration.Builder()
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());
    }

}
