
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
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodAnalysis;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestFreezableSet1 extends CommonTestRunner {

    public TestFreezableSet1() {
        super(true);
    }

    TypeAnalyserVisitor typeAnalyserVisitor = d -> {
        if (d.iteration() > 1) {
            assertEquals(1L, d.typeAnalysis().getApprovedPreconditionsE2().size());
            assertEquals("frozen", d.typeAnalysis().markLabel());
        }
    };

    MethodAnalyserVisitor methodAnalyserVisitor = d -> {
        int modified = d.methodAnalysis().getProperty(VariableProperty.MODIFIED_METHOD);
        MethodAnalysis methodAnalysis = d.methodAnalysis();
        String name = d.methodInfo().name;
        if (d.iteration() > 0) {
            if ("stream".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertEquals("[this.frozen]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("streamEarly".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("add".equals(name)) {
                assertEquals(Level.TRUE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("freeze".equals(name)) {
                assertEquals(Level.TRUE, modified);
                assertEquals("[not (this.frozen)]", methodAnalysis.getPreconditionForEventual().toString());
            }
            if ("isFrozen".equals(name)) {
                assertEquals(Level.FALSE, modified);
                assertTrue(methodAnalysis.getPreconditionForEventual().isEmpty());
            }
        }
    };

    @Test
    public void test() throws IOException {
        testClass("FreezableSet1", 0, 0, new DebugConfiguration.Builder()
                .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                .addAfterTypePropertyComputationsVisitor(typeAnalyserVisitor)
                .build());

    }

}
