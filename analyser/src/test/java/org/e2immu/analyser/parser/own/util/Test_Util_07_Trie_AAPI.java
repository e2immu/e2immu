
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
import org.e2immu.analyser.config.AnalyserConfiguration;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeMapVisitor;
import org.e2immu.support.Freezable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class Test_Util_07_Trie_AAPI extends CommonTestRunner {

    public Test_Util_07_Trie_AAPI() {
        super(true);
    }

    @Test
    public void test() throws IOException {

        TypeMapVisitor typeMapVisitor = typeMap -> {
            TypeInfo freezable = typeMap.get(Freezable.class);

            MethodInfo ensureNotFrozen = freezable.findUniqueMethod("ensureNotFrozen", 0);
            MethodAnalysis ensureNotFrozenAna = ensureNotFrozen.methodAnalysis.get();
            assertEquals(DV.FALSE_DV, ensureNotFrozenAna.getProperty(Property.MODIFIED_METHOD));
            MethodInfo companion = ensureNotFrozen.methodInspection.get().getCompanionMethods()
                    .get(CompanionMethodName.extract("ensureNotFrozen$Precondition"));
            assertNotNull(companion);
            assertEquals("Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]",
                    ensureNotFrozenAna.getPrecondition().toString());
            assertEquals("Precondition[expression=!frozen, causes=[companionMethod:ensureNotFrozen$Precondition]]",
                    ensureNotFrozenAna.getPreconditionForEventual().toString());
            assertEquals("@Only before: [frozen]", ensureNotFrozenAna.getEventual().toString());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 2, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(1), 3, MultiLevel.INDEPENDENT_1_DV, Property.INDEPENDENT);

                String pc = "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]";
                assertEquals(pc, d.methodAnalysis().getPrecondition().toString());
                String pce = d.iteration() <= 2 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=true, causes=[]]";
                assertEquals(pce, d.methodAnalysis().getPreconditionForEventual().toString());

                String eventual = switch (d.iteration()) {
                    case 0 -> "[DelayedEventual:initial@Class_Trie]";
                    case 1 -> "[DelayedEventual:final@Field_root]";
                    case 2 -> "[DelayedEventual:immutable@Class_TrieNode]";
                    case 3 -> "[DelayedEventual:initial@Field_data;initial@Field_map]";
                    default -> "@Only before: [frozen]";
                };
                assertEquals(eventual, d.methodAnalysis().getEventual().toString());
            }
        };

        testSupportAndUtilClasses(List.of(Trie.class), 0, 0,
                new DebugConfiguration.Builder()
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
