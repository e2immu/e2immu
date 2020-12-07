/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);
    public static final int VIC_LEVEL = VariableInfoContainer.LEVEL_4_SUMMARY;

    // part of modification status for methods dealing with SAMs
    private final SetOnce<Boolean> callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod = new SetOnce<>();

    public Boolean getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod() {
        return callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.getOrElse(null);
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final SetOnce<Expression> combinedPrecondition = new SetOnce<>();

    // no delays when frozen
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    public void copyFrom(Stream<MethodLevelData> others) {
        others.forEach(mld -> {
            // TODO
        });
    }

    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add("ensureThisProperties", sharedState -> ensureThisProperties(sharedState.evaluationContext, sharedState.statementAnalysis))
            .add("computeContentModifications", this::computeContentModifications)
            .add("combinePrecondition", this::combinePrecondition)
            .build();

    public AnalysisStatus analyse(StatementAnalyser.SharedState sharedState,
                                  StatementAnalysis statementAnalysis,
                                  MethodLevelData previous,
                                  StateData stateData) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();
        MethodInfo methodInfo = evaluationContext.getCurrentMethod().methodInfo;
        String logLocation = methodInfo.distinguishingName();
        try {
            StatementAnalyserResult.Builder builder = sharedState.builder();
            SharedState localSharedState = new SharedState(builder, evaluationContext, statementAnalysis, logLocation, previous, stateData);
            return analyserComponents.run(localSharedState);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, method {}", logLocation);
            throw rte;
        }
    }

    // preconditions come from the precondition object in stateData, and preconditions from method calls; they're accumulated
    // in the state.precondition field

    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        if (!combinedPrecondition.isSet()) {
            Expression result;

            if (sharedState.previous == null) {
                result = sharedState.stateData.precondition.isSet() ? sharedState.stateData.precondition.get() : EmptyExpression.EMPTY_EXPRESSION;
            } else {
                Expression v1 = sharedState.previous.combinedPrecondition.get();
                Expression v2 = sharedState.stateData.precondition.get();
                if (v1 == EmptyExpression.EMPTY_EXPRESSION) {
                    result = v2;
                } else if (v2 == EmptyExpression.EMPTY_EXPRESSION) {
                    result = v1;
                } else {
                    result = new And(sharedState.evaluationContext.getPrimitives())
                            .append(sharedState.evaluationContext, v1, v2);
                }
            }
            combinedPrecondition.set(result);
        }
        return DONE;
    }

    private AnalysisStatus computeContentModifications(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final AtomicBoolean progress = new AtomicBoolean();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        sharedState.statementAnalysis.variableStream().forEach(variableInfo -> {
            if (!variableInfo.linkedVariablesIsSet()) {
                if(!(variableInfo.variable() instanceof LocalVariableReference) ||
                        variableInfo.getProperty(VariableProperty.READ) >= Level.TRUE ||
                        variableInfo.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE) {
                    log(DELAYED, "Delaying content modification in MethodLevelData for {} in {}", variableInfo.variable().fullyQualifiedName(),
                            sharedState.evaluationContext.getCurrentStatement());
                    analysisStatus.set(DELAYS);
                } else {
                    log(LINKED_VARIABLES, "Local variable {} not read, not assigned, so cannot yet be linked");
                }
            } else {
                Set<Variable> linkedVariables = SetUtil.immutableUnion(Set.of(variableInfo.variable()), variableInfo.getLinkedVariables());
                int summary = sharedState.evaluationContext.summarizeModification(linkedVariables);
                String logLocation = sharedState.logLocation;
                for (Variable linkedVariable : linkedVariables) {
                    if (linkedVariable instanceof FieldReference) {
                        FieldInfo fieldInfo = ((FieldReference) linkedVariable).fieldInfo;
                        VariableInfo vi = sharedState.statementAnalysis.getLatestVariableInfo(fieldInfo.fullyQualifiedName());
                        int modified = vi.getProperty(VariableProperty.MODIFIED);
                        if (modified == Level.DELAY) {
                            // break the delay in case the variable is not even read
                            int fieldModified;
                            if (summary == Level.DELAY && vi.getProperty(VariableProperty.READ) < Level.TRUE) {
                                fieldModified = Level.FALSE;
                            } else fieldModified = summary;
                            if (fieldModified == Level.DELAY) {
                                log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), logLocation);
                                analysisStatus.set(DELAYS);
                            } else {
                                log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
                                        linkedVariable.fullyQualifiedName(), logLocation);
                                VariableInfoContainer vic = sharedState.statementAnalysis.findForWriting(vi.name());
                                vic.setProperty(VIC_LEVEL, VariableProperty.MODIFIED, fieldModified);
                                progress.set(true);
                            }
                        }
                    } else if (linkedVariable instanceof ParameterInfo) {
                        ParameterAnalysis parameterAnalysis = sharedState.evaluationContext.getParameterAnalysis((ParameterInfo) linkedVariable);
                        FieldInfo assigned = parameterAnalysis.getAssignedToField();
                        if (assigned != null) {
                            log(NOT_MODIFIED, "Parameter {} is assigned to field {}, not setting @NotModified {} directly",
                                    linkedVariable.fullyQualifiedName(), assigned.fullyQualifiedName(), summary);
                        } else {
                            if (summary == Level.DELAY) {
                                log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), logLocation);
                                analysisStatus.set(DELAYS);
                            } else {
                                log(NOT_MODIFIED, "Mark {} as {} in {}", linkedVariable.fullyQualifiedName(),
                                        summary == Level.TRUE ? "@Modified" : "@NotModified", logLocation);
                                int currentModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                                if (currentModified == Level.DELAY) {
                                    // we can safely cast here to the builder
                                    ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                                    sharedState.builder.add(builder.new SetProperty(VariableProperty.MODIFIED, summary));
                                    progress.set(true);
                                }
                            }
                        }
                    }
                }
            }
        });
        if(analysisStatus.get() == DONE) {
            linksHaveBeenEstablished.set();
        }
        return analysisStatus.get() == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }


    /**
     * Finish odds and ends
     *
     * @param evaluationContext context
     * @return if any change happened to methodAnalysis
     */
    private AnalysisStatus ensureThisProperties(EvaluationContext evaluationContext, StatementAnalysis statementAnalysis) {
        if (evaluationContext.getIteration() > 0) return DONE;

        VariableInfoContainer thisVi = statementAnalysis.findForWriting(evaluationContext.getAnalyserContext(),
                new This(evaluationContext.getAnalyserContext(), evaluationContext.getCurrentType()));
        thisVi.setProperty(VIC_LEVEL, VariableProperty.ASSIGNED, Level.FALSE);
        thisVi.ensureProperty(VIC_LEVEL, VariableProperty.READ, Level.FALSE);
        thisVi.ensureProperty(VIC_LEVEL, VariableProperty.METHOD_CALLED, Level.FALSE);

        if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(false);
        }

        return DONE;
    }

    public class SetCircularCallOrUndeclaredFunctionalInterface implements StatementAnalyser.StatementAnalysisModification {
        @Override
        public void accept(StatementAnalyser.ModificationData modificationData) {
            if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
                callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(true);
            }
        }
    }
}
