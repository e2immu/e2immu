
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
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
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

    public static final String PC = "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]";
    public static final String PC2 = "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition, companionMethod:ensureNotFrozen$Precondition]]";

    @Test
    public void test() throws IOException {
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("addNode".equals(d.methodInfo().name) && 3 == n) {
                if ("0".equals(d.statementId())) {
                    assertEquals(PC, d.statementAnalysis().stateData().getPrecondition().toString());
                    assertEquals(PC, d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
                if ("1".equals(d.statementId())) {
                    // also call to getOrCreate...?
                    assertEquals("Precondition[expression=true, causes=[]]", d.statementAnalysis().stateData().getPrecondition().toString());
                    assertEquals(PC, d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
                if ("2.0.2".equals(d.statementId())) {
                    assertEquals(PC, d.statementAnalysis().stateData().getPrecondition().toString()); // call to getOrCreate
                    assertEquals(PC, d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
                if ("2".equals(d.statementId())) {
                    assertEquals(PC2,
                            d.statementAnalysis().methodLevelData().combinedPreconditionGet().toString());
                }
            }
        };
        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            int n = d.methodInfo().methodInspection.get().getParameters().size();
            if ("addNode".equals(d.methodInfo().name) && 3 == n) {
                assertEquals(PC2, d.methodAnalysis().getPrecondition().toString());
            }
            if ("getOrCreate".equals(d.methodInfo().name)) {
                assertEquals(PC, d.methodAnalysis().getPrecondition().toString());
            }
        };
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
                assertEquals(PC, ensureNotFrozenAna.getPrecondition().toString());
                assertEquals("Precondition[expression=!frozen, causes=[companionMethod:ensureNotFrozen$Precondition]]",
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
                assertEquals("Precondition[expression=this.isFrozen(), causes=[companionMethod:ensureFrozen$Precondition]]",
                        ensureFrozenAna.getPrecondition().toString());
                assertEquals("Precondition[expression=frozen, causes=[companionMethod:ensureFrozen$Precondition]]",
                        ensureFrozenAna.getPreconditionForEventual().toString());
                assertEquals("@Only after: [frozen]", ensureFrozenAna.getEventual().toString());
            }
        };

        testSupportAndUtilClasses(List.of(DependencyGraph.class), 7, 2,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }
}
