
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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.DependencyGraph;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// only parse DependencyGraph, take Freezable from the Annotated APIs
public class Test_Util_06_DependencyGraph_AAPI extends CommonTestRunner {

    public Test_Util_06_DependencyGraph_AAPI() {
        super(true);
    }

    @Test
    public void test() throws IOException {
        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo freezable = typeMap.get(Freezable.class);

            TypeAnalysis freezableAna = freezable.typeAnalysis.get();
            assertEquals(MultiLevel.EVENTUALLY_RECURSIVELY_IMMUTABLE_DV, freezableAna.getProperty(Property.IMMUTABLE));
            assertEquals(MultiLevel.CONTAINER_DV, freezableAna.getProperty(Property.CONTAINER));

            MethodInfo freeze = freezable.findUniqueMethod("freeze", 0);
            MethodAnalysis freezeAna = freeze.methodAnalysis.get();
            assertEquals("@Mark: [frozen]", freezeAna.getEventual().toString());
            assertEquals(DV.TRUE_DV, freezeAna.getProperty(Property.MODIFIED_METHOD));

            MethodInfo isFrozen = freezable.findUniqueMethod("isFrozen", 0);
            MethodAnalysis isFrozenAna = isFrozen.methodAnalysis.get();
            assertEquals(DV.FALSE_DV, isFrozenAna.getProperty(Property.MODIFIED_METHOD));
            assertEquals("@TestMark: [frozen]", isFrozenAna.getEventual().toString());
            assertEquals("<return value>", isFrozenAna.getSingleReturnValue().toString());

            {
                MethodInfo ensureNotFrozen = freezable.findUniqueMethod("ensureNotFrozen", 0);
                MethodAnalysis ensureNotFrozenAna = ensureNotFrozen.methodAnalysis.get();
                assertEquals(DV.FALSE_DV, ensureNotFrozenAna.getProperty(Property.MODIFIED_METHOD));
                MethodInfo companion = ensureNotFrozen.methodInspection.get().getCompanionMethods()
                        .get(CompanionMethodName.extract("ensureNotFrozen$Precondition"));
                assertNotNull(companion);
                assertEquals("Precondition[expression=!this.isFrozen(), causes=[escape]]",
                        ensureNotFrozenAna.getPrecondition().toString());
                assertEquals("Precondition[expression=!frozen, causes=[escape]]",
                        ensureNotFrozenAna.getPreconditionForEventual().toString());
                assertEquals("@Only before: [frozen]", ensureNotFrozenAna.getEventual().toString());
            }
            {
                MethodInfo ensureFrozen = freezable.findUniqueMethod("ensureFrozen", 0);
                MethodAnalysis ensureFrozenAna = ensureFrozen.methodAnalysis.get();
                assertEquals(DV.FALSE_DV, ensureFrozenAna.getProperty(Property.MODIFIED_METHOD));
                MethodInfo companion2 = ensureFrozen.methodInspection.get().getCompanionMethods()
                        .get(CompanionMethodName.extract("ensureFrozen$Precondition"));
                assertNotNull(companion2);
                assertEquals("Precondition[expression=this.isFrozen(), causes=[escape]]",
                        ensureFrozenAna.getPrecondition().toString());
                assertEquals("Precondition[expression=frozen, causes=[escape]]",
                        ensureFrozenAna.getPreconditionForEventual().toString());
                assertEquals("@Only after: [frozen]", ensureFrozenAna.getEventual().toString());
            }
        };

        testSupportAndUtilClasses(List.of(DependencyGraph.class), 7, 2,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(false).build());
    }
}
