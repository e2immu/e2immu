
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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.util.Trie;
import org.e2immu.analyser.visitor.*;
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

        StatementAnalyserVariableVisitor statementAnalyserVariableVisitor = d -> {
            if ("recursivelyVisit".equals(d.methodInfo().name)) {
                if (d.variable() instanceof ParameterInfo pi && "node".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
            if ("accept".equals(d.methodInfo().name) && "visitLeaves".equals(d.enclosingMethod().name)) {
                if (d.variable() instanceof ParameterInfo pi && "n".equals(pi.name)) {
                    if ("0.0.0".equals(d.statementId())) {
                        assertDv(d, 1, DV.TRUE_DV, Property.CONTEXT_MODIFIED);
                    }
                }
            }
        };
        StatementAnalyserVisitor statementAnalyserVisitor = d -> {
            if ("visitLeaves".equals(d.methodInfo().name)) {
                if ("2.0.0".equals(d.statementId())) {
                    String properties = switch (d.iteration()) {
                        case 0 ->
                                "map={context-modified=link@NOT_YET_SET, context-not-null=link@NOT_YET_SET}, node={context-modified=link@NOT_YET_SET, context-not-null=link@NOT_YET_SET}, strings={context-modified=link@NOT_YET_SET, context-not-null=link@NOT_YET_SET}, this={context-modified=link@NOT_YET_SET}, visitor={context-modified=constructor-to-instance@Method_accept_0.0.0-E;initial:n.map@Method_accept_0-C;initial:n@Method_accept_0-E;initial:s@Method_accept_0.0.0-E;link@NOT_YET_SET, context-not-null=initial:n.map@Method_accept_0-C;link@NOT_YET_SET, read=true:1}";
                        case 1 ->
                                "map={context-modified=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M}, node={context-modified=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M}, strings={context-modified=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M}, this={context-modified=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M}, visitor={context-modified=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;initial@Field_root;link:strings@Method_goTo_0:M;link:this.root@Method_goTo_2:M, read=true:1}";
                        case 2 ->
                                "map={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo}, node={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo}, strings={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo}, this={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo}, visitor={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link:strings@Method_goTo_0:M;link@Field_data;link@Field_map;srv@Method_goTo, read=true:1}";
                        case 3 ->
                                "map={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo}, node={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo}, strings={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo}, this={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo}, visitor={context-modified=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=constructor-to-instance@Method_visitLeaves_0-E;link@Field_data;link@Field_map;srv@Method_goTo, read=true:1}";
                        case 4 ->
                                "map={context-modified=link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=link@Field_data;link@Field_map;srv@Method_goTo}, node={context-modified=link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=link@Field_data;link@Field_map;srv@Method_goTo}, strings={context-modified=link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=link@Field_data;link@Field_map;srv@Method_goTo}, this={context-modified=link@Field_data;link@Field_map;srv@Method_goTo}, visitor={context-modified=link@Field_data;link@Field_map;srv@Method_goTo, context-not-null=link@Field_data;link@Field_map;srv@Method_goTo, read=true:1}";
                        default ->
                                "map={context-modified=false:0, context-not-null=nullable:1}, node={context-modified=false:0, context-not-null=nullable:1}, strings={context-modified=false:0, context-not-null=nullable:1}, this={context-modified=false:0}, visitor={context-modified=true:1, context-not-null=not_null:5, read=true:1}";
                    };
                    assertEquals(properties, d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
            if ("recursivelyVisit".equals(d.methodInfo().name)) {
                if ("1.0.0".equals(d.statementId())) {
                    // IMPROVE incorrectly, strings is not modified: recursive method in lambda
                    // (see code in StatementAnalyserImpl.transferFromClosureToResult)
                    assertEquals("strings={read=true:1}, visitor={read=true:1}",
                            d.statementAnalysis().propertiesFromSubAnalysersSortedToString());
                }
            }
        };

        TypeMapVisitor typeMapVisitor = d -> {
            TypeInfo freezable = d.typeMap().get(Freezable.class);

            MethodInfo ensureNotFrozen = freezable.findUniqueMethod("ensureNotFrozen", 0);
            MethodAnalysis ensureNotFrozenAna = ensureNotFrozen.methodAnalysis.get();
            assertEquals(DV.FALSE_DV, ensureNotFrozenAna.getProperty(Property.MODIFIED_METHOD));
            MethodInfo companion = ensureNotFrozen.methodInspection.get().getCompanionMethods()
                    .get(CompanionMethodName.extract("ensureNotFrozen$Precondition"));
            assertNotNull(companion);
            assertEquals("Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]",
                    ensureNotFrozenAna.getPrecondition().toString());
            // via shallow analyser rather than computed analyser
            assertEquals("Precondition[expression=[frozen], causes=[]]",
                    ensureNotFrozenAna.getPreconditionForEventual().toString());
            assertEquals("@Only before: [frozen]", ensureNotFrozenAna.getEventual().toString());
        };

        MethodAnalyserVisitor methodAnalyserVisitor = d -> {
            if ("add".equals(d.methodInfo().name)) {
                assertDv(d, 1, DV.TRUE_DV, Property.MODIFIED_METHOD);
                assertDv(d.p(1), 5, MultiLevel.INDEPENDENT_HC_DV, Property.INDEPENDENT);

                String pc = "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]";
                assertEquals(pc, d.methodAnalysis().getPrecondition().toString());
                String pce = d.iteration() < 2 ? "Precondition[expression=<precondition>, causes=[]]"
                        : "Precondition[expression=!this.isFrozen(), causes=[companionMethod:ensureNotFrozen$Precondition]]";
                assertEquals(pce, d.methodAnalysis().getPreconditionForEventual().toString());

                String eventual = switch (d.iteration()) {
                    case 0 -> "[DelayedEventual:initial@Class_Trie]";
                    case 1 -> "[DelayedEventual:[19 delays]]";
                    case 2 -> "[DelayedEventual:[13 delays]]";
                    case 3 -> "[DelayedEventual:[15 delays]]";
                    case 4 -> "[DelayedEventual:[15 delays]]";
                    case 5 -> "[DelayedEventual:[13 delays]]";
                    default -> "@Only before: [frozen]";
                };
                assertEquals(eventual, d.methodAnalysis().getEventual().toString());
                if (d.iteration() >= 6) {
                    AnnotationExpression only = d.evaluationContext().getAnalyserContext().getE2ImmuAnnotationExpressions().only;
                    AnnotationExpression ae = d.methodAnalysis().annotationGetOrDefaultNull(only);
                    assertEquals("@Only(before=\"frozen\")", ae.toString());
                }
            }

            if ("recursivelyVisit".equals(d.methodInfo().name)) {
                assertDv(d.p(2), MultiLevel.NOT_IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
            }
            // on a non-private method, IGNORE_MODS is implicit
            if ("visit".equals(d.methodInfo().name)) {
                assertDv(d.p(1), MultiLevel.IGNORE_MODS_DV, Property.IGNORE_MODIFICATIONS);
            }
        };

        BreakDelayVisitor breakDelayVisitor = d -> assertEquals("--------", d.delaySequence());

        testSupportAndUtilClasses(List.of(Trie.class), 0, 2,
                new DebugConfiguration.Builder()
                     //   .addStatementAnalyserVariableVisitor(statementAnalyserVariableVisitor)
                     //   .addStatementAnalyserVisitor(statementAnalyserVisitor)
                        .addTypeMapVisitor(typeMapVisitor)
                        .addAfterMethodAnalyserVisitor(methodAnalyserVisitor)
                        .addBreakDelayVisitor(breakDelayVisitor)
                        .build(),
                new AnalyserConfiguration.Builder().setComputeFieldAnalyserAcrossAllMethods(true).build());
    }

}
