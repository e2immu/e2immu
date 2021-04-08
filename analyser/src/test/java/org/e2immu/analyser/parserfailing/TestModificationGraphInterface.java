
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

package org.e2immu.analyser.parserfailing;

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// in this test, the introduction of a functional interface has resolved the circular dependency
// however, useC2 remains modifying because of the functional interface

public class TestModificationGraphInterface extends CommonTestRunner {

    public TestModificationGraphInterface() {
        super(false);
    }

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        if ("useC2".equals(d.methodInfo().name)) {
            assertTrue(d.methodAnalysis().methodLevelData()
                    .getCallsPotentiallyCircularMethod());
        }
        if ("incrementAndGetWithI".equals(d.methodInfo().name) && d.iteration() > 0) {
            assertEquals(Level.TRUE, d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD));
        }
    };

    FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
        if ("c1".equals(d.fieldInfo().name) && d.iteration() > 1) {
            assertEquals(Level.TRUE, d.fieldAnalysis().getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
        }
    };

    @Test
    public void test() throws IOException {
        testClass(List.of("ModificationGraphInterface", "ModificationGraphInterfaceC1", "ModificationGraphInterfaceC2",
                "ModificationGraphInterfaceIncrementer"), 0, 0,
                new DebugConfiguration.Builder()
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().build(),
                new AnnotatedAPIConfiguration.Builder().build());
    }

}
