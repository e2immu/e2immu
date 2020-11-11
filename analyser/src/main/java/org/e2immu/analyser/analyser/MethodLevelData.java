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
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.NOT_MODIFIED;
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
    public final SetOnce<Boolean> callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod = new SetOnce<>();
    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final SetOnce<Value> combinedPrecondition = new SetOnce<>();

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
            .add("establishLinks", this::establishLinks)
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
            Value result;

            if (sharedState.previous == null) {
                result = sharedState.stateData.precondition.isSet() ? sharedState.stateData.precondition.get() : UnknownValue.EMPTY;
            } else {
                Value v1 = sharedState.previous.combinedPrecondition.get();
                Value v2 = sharedState.stateData.precondition.get();
                if (v1 == UnknownValue.EMPTY) {
                    result = v2;
                } else if (v2 == UnknownValue.EMPTY) {
                    result = v1;
                } else {
                    result = new AndValue(sharedState.evaluationContext.getPrimitives())
                            .append(sharedState.evaluationContext, v1, v2);
                }
            }
            combinedPrecondition.set(result);
        }
        return DONE;
    }

    /*
     Relies on
     - numberedStatement.linkedVariables, which should return us all the variables involved in the return statement
            it does so by computing the linkedVariables of the evaluation of the expression in the return statement
     - for fields among the linkedVariables: fieldAnalysis.variablesLinkedToMe,
       which in turn depends on fieldAssignments and fieldsLinkedToFieldsAndVariables of ALL OTHER methods
     - for local variables: variablesLinkedToFieldsAndParameters for this method

     sets variablesLinkedToMethodResult, and @Linked on or off dependent on whether the set is empty or not
    */


    /*
      goal: we need to establish that in this method, recursively, a given field is linked to one or more fields or parameters
      we need to find out if a parameter is linked, recursively, to another field or parameter
      local variables need to be taken out of the loop

      in essence: moving from the dependency graph to the MethodAnalysis.variablesLinkedToFieldsAndParameters data structure
      gets rid of local vars and follows links transitively

      To answer how this method deals with unevaluated links (links that can do better when one of their components are != NO_VALUE)
      two dependency graphs have been created: a best-case one where some annotations on the current type have been discovered
      already, and a worst-case one where we do not take them into account.

      Why? if a method is called, as part of the value, and we do not yet know anything about the independence (@Independent) of that method,
      the outcome of linkedVariables() can be seriously different. If there is a difference between the transitive
      closures of best and worst, we should delay.

      On top of this, fields whose @Final status has not been set yet, are represented (as currentValues in the evaluation context)
      by VariableValues with a special boolean flag, instead of NO_VALUES.
      This allows us to delay computations without completely losing the dependency structure as constructed up by method calls.
      It is that dependency structure that we need to be able to distinguish between best and worst case.

    */

    private AnalysisStatus establishLinks(SharedState sharedState) {
        if (linksHaveBeenEstablished.isSet()) return DONE;
        StatementAnalysis statementAnalysis = sharedState.statementAnalysis;
        boolean delays = statementAnalysis.variableStream()
                .filter(variableInfo -> variableInfo.variable() instanceof FieldReference || variableInfo.variable() instanceof ReturnVariable)
                .anyMatch(variableInfo -> !variableInfo.linkedVariablesIsSet());
        if (delays) return DELAYS;
        linksHaveBeenEstablished.set();
        return DONE;
    }

    private AnalysisStatus computeContentModifications(SharedState sharedState) {
        if (!linksHaveBeenEstablished.isSet()) return DELAYS;

        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final AtomicBoolean changes = new AtomicBoolean();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        sharedState.statementAnalysis.variableStream().forEach(variableInfo -> {
            if (!variableInfo.linkedVariablesIsSet()) {
                analysisStatus.set(DELAYS);
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
                                changes.set(true);
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
                                    changes.set(true);
                                }
                            }
                        }
                    }
                }
            }
        });
        return analysisStatus.get() == DELAYS ? (changes.get() ? PROGRESS : DELAYS) : DONE;
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
                new This(evaluationContext.getCurrentType().typeInfo));
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
