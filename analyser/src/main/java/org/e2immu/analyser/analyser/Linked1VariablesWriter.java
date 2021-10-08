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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

/*
All come together: Linked1 computed in StatementAnalyser.apply,
joined in the dependency graph with statically assigned and linked variables.

T t = list.get(index); --> content links t to list

T t = list.get(index);
T s = t;  --> we must follow statically assigned variables, so that s is content linked to list

List<T> list1 = list.subList(0, 2); // list and list1 are linked
T t = list1.get(index);  --> we must follow linked variables, so that t is content linked to list
T s = t;  --> s is content linked to list

See Dependent1_6
 */
public record Linked1VariablesWriter(StatementAnalysis statementAnalysis, EvaluationContext evaluationContext) {
    private static final String LINKED1_WRITER = "Linked1Writer";
    private static final Logger LOGGER = LoggerFactory.getLogger(Linked1VariablesWriter.class);

    public AnalysisStatus write(Map<Variable, LinkedVariables> linked1,
                                ContextPropertyWriter.LocalCopyData localCopyData) {
        DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();

        Set<Variable> involvedInLinked1 = new HashSet<>();
        AnalysisStatus analysisStatus = fillDependencyGraph(dependencyGraph, involvedInLinked1, linked1, localCopyData);
        if (analysisStatus == DELAYS) return DELAYS;

        for (Variable variable : linked1.keySet()) {
            VariableInfoContainer vic = statementAnalysis.findForWriting(variable);

            Set<Variable> linked1FromDependencyGraph = dependencyGraph.dependencies(variable);
            if(!Collections.disjoint(linked1FromDependencyGraph, involvedInLinked1)) {
                linked1FromDependencyGraph.remove(variable);
                LinkedVariables linked1Variables = new LinkedVariables(linked1FromDependencyGraph, false);
                try {
                    vic.setLinked1Variables(linked1Variables, false);
                } catch (IllegalStateException ise) {
                    LOGGER.error("Caught IllegalStateException in statement {}, variable {}",
                            statementAnalysis.index, variable.fullyQualifiedName());
                    throw ise;
                }
            } else {
                vic.setLinked1Variables(LinkedVariables.EMPTY, false);
            }
        }

        return DONE;
    }

    private AnalysisStatus fillDependencyGraph(DependencyGraph<Variable> dependencyGraph,
                                               Set<Variable> involvedInLinked1,
                                               Map<Variable, LinkedVariables> linked1,
                                               ContextPropertyWriter.LocalCopyData localCopyData) {
        // delays in dependency graph
        AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        statementAnalysis.variables.stream()
                .map(Map.Entry::getValue)
                .filter(vic -> vic.best(EVALUATION).isNotConditionalInitialization())
                .forEach(vic -> {
                    VariableInfo variableInfo = vic.best(EVALUATION);
                    if (variableInfo.statementTimeDelayed()) {
                        /*
                         why? because a new variable (var$index) will be created in the next iteration, it will be statically assigned
                         so will be part of a cluster in the dependency graph.
                         */
                        log(DELAYED, "Delaying dependency graph for {} in {}: statement time delayed",
                                variableInfo.variable().fullyQualifiedName(), evaluationContext.getLocation());
                        analysisStatus.set(DELAYS);
                        assert evaluationContext.getIteration() == 0;
                        return;
                    }
                    VariableInfo vi1 = vic.getPreviousOrInitial();
                    LinkedVariables linkedVariables = variableInfo.getLinkedVariables();
                    LinkedVariables staticallyAssigned = variableInfo.getStaticallyAssignedVariables();

                    LinkedVariables earlierLinked1Variables = vi1.getLinkedVariables();
                    involvedInLinked1.addAll(earlierLinked1Variables.variables());
                    LinkedVariables linked1InMap = linked1.getOrDefault(variableInfo.variable(), LinkedVariables.EMPTY);
                    involvedInLinked1.addAll(linked1InMap.variables());

                    LinkedVariables merged = earlierLinked1Variables.merge
                            (linked1InMap.merge(linkedVariables.merge(staticallyAssigned)));

                    boolean ignoreDelay = variableInfo.getProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY) == Level.TRUE;
                    if (merged.isDelayed() && !ignoreDelay) {
                        if (!(variableInfo.variable() instanceof LocalVariableReference) || variableInfo.isAssigned()) {
                            log(DELAYED, "Delaying dependency graph for {} in {}: linked1 variables not set",
                                    variableInfo.variable().fullyQualifiedName(), evaluationContext.getLocation());

                            assert statementAnalysis.translatedDelay(LINKED1_WRITER,
                                    variableInfo.variable().fullyQualifiedName() + "@" + statementAnalysis.index + DelayDebugger.D_LINKED_VARIABLES_SET,
                                    statementAnalysis.fullyQualifiedName() + ".LINKED1");
                            analysisStatus.set(DELAYS);
                        }
                    } else {
                        Variable from = variableInfo.variable();
                        List<Variable> to = merged.variables().stream().filter(t -> localCopyData.accept(from, t)).toList();
                        dependencyGraph.addNode(from, to, true);
                    }
                });
        return analysisStatus.get();
    }

}
