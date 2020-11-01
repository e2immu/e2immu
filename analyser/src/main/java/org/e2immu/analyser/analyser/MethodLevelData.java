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
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    public final SetOnce<Boolean> callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod = new SetOnce<>();
    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final SetOnce<Value> combinedPrecondition = new SetOnce<>();

    // no delays when frozen
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    // ************** SUMMARIES
    // in combination with the properties in the super class, this forms the knowledge about the method itself
    public final SetOnce<TransferValue> thisSummary = new SetOnce<>();
    public final SetOnceMap<String, TransferValue> returnStatementSummaries = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, TransferValue> fieldSummaries = new SetOnceMap<>();

    // ************** LINKING

    // this one is the marker that says that links have been established
    public final SetOnce<Map<Variable, Set<Variable>>> variablesLinkedToFieldsAndParameters = new SetOnce<>();

    public final SetOnce<Set<Variable>> variablesLinkedToMethodResult = new SetOnce<>();

    public void copyFrom(Stream<MethodLevelData> others) {
        // this is perfectly safe, as each statement has its own entry in the array
        others.forEach(mld -> returnStatementSummaries.putAll(mld.returnStatementSummaries, false));
    }


    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add("copyFieldAndThisProperties", sharedState -> copyFieldAndThisProperties(sharedState.evaluationContext, sharedState.statementAnalysis))
            .add("copyFieldAssignmentValue", sharedState -> copyFieldAssignmentValue(sharedState.statementAnalysis))
            .add("copyContextProperties", this::copyContextProperties)
            .add("establishLinks", this::establishLinks)
            .add("updateVariablesLinkedToMethodResult", this::updateVariablesLinkedToMethodResult)
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
        if (variablesLinkedToFieldsAndParameters.isSet()) return DONE;
        StatementAnalysis statementAnalysis = sharedState.statementAnalysis;

        // final fields need to have a value set; all the others act as local variables
        boolean someVariablesHaveNotBeenEvaluated = statementAnalysis.variableStream().anyMatch(vi -> vi.valueForNextStatement() == UnknownValue.NO_VALUE);
        if (someVariablesHaveNotBeenEvaluated) {
            log(DELAYED, "Some variables have not yet been evaluated -- delaying establishing links in {}", sharedState.logLocation);
            return DELAYS;
        }
        if (statementAnalysis.isDelaysInDependencyGraph()) {
            log(DELAYED, "Dependency graph suffers delays -- delaying establishing links in {}", sharedState.logLocation);
            return DELAYS;
        }
        EvaluationContext evaluationContext = sharedState.evaluationContext;
        boolean allFieldsFinalDetermined = evaluationContext.getCurrentMethod().methodInfo.typeInfo.typeInspection.getPotentiallyRun()
                .fields.stream().allMatch(fieldInfo -> evaluationContext.getFieldAnalysis(fieldInfo).getProperty(VariableProperty.FINAL) != Level.DELAY);
        if (!allFieldsFinalDetermined) {
            log(DELAYED, "Delay, we don't know about final values for some fields in {}", sharedState.logLocation);
            return DELAYS;
        }

        String logLocation = sharedState.logLocation;
        Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();
        statementAnalysis.dependencyGraph.visit((variable, dependencies) -> {
            Set<Variable> fieldAndParameterDependencies = new HashSet<>(statementAnalysis.dependencyGraph.dependencies(variable));
            fieldAndParameterDependencies.removeIf(v -> !(v instanceof FieldReference) && !(v instanceof ParameterInfo));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(fieldAndParameterDependencies::add);
            }
            fieldAndParameterDependencies.remove(variable); // removing myself
            variablesLinkedToFieldsAndParameters.put(variable, fieldAndParameterDependencies);
            log(DEBUG_LINKED_VARIABLES, "Set terminals of {} in {} to [{}]", variable.fullyQualifiedName(),
                    logLocation, Variable.fullyQualifiedName(fieldAndParameterDependencies));

            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!fieldSummaries.isSet(fieldInfo)) {
                    fieldSummaries.put(fieldInfo, new TransferValue());
                }
                fieldSummaries.get(fieldInfo).linkedVariables.set(fieldAndParameterDependencies);
                log(LINKED_VARIABLES, "Decided on links of {} in {} to [{}]", variable.fullyQualifiedName(),
                        logLocation, Variable.fullyQualifiedName(fieldAndParameterDependencies));
            }
        });
        // set all the linkedVariables for fields not in the dependency graph
        fieldSummaries.stream().filter(e -> !e.getValue().linkedVariables.isSet())
                .forEach(e -> {
                    e.getValue().linkedVariables.set(Set.of());
                    log(LINKED_VARIABLES, "Clear linked variables of {} in {}", e.getKey().name, logLocation);
                });
        log(LINKED_VARIABLES, "Set variablesLinkedToFieldsAndParameters to true for {}", logLocation);
        this.variablesLinkedToFieldsAndParameters.set(variablesLinkedToFieldsAndParameters);
        return DONE;
    }

    // only needs to be run on the last statement; not that relevant on others
    private AnalysisStatus updateVariablesLinkedToMethodResult(SharedState sharedState) {
        if (sharedState.evaluationContext.getCurrentMethod().methodInfo.isConstructor) return DONE;
        if (variablesLinkedToMethodResult.isSet()) return DONE;

        Set<Variable> variables = new HashSet<>();
        boolean waitForLinkedVariables = returnStatementSummaries.stream().anyMatch(e -> !e.getValue().linkedVariables.isSet());
        if (waitForLinkedVariables) {
            log(DELAYED, "Not yet ready to compute linked variables of result of method {}", sharedState.logLocation);
            return DELAYS;
        }
        Set<Variable> variablesInvolved = returnStatementSummaries.stream()
                .flatMap(e -> e.getValue().linkedVariables.get().stream()).collect(Collectors.toSet());

        for (Variable variable : variablesInvolved) {
            Set<Variable> dependencies;
            if (variable instanceof FieldReference) {
                FieldAnalysis fieldAnalysis = sharedState.evaluationContext.getFieldAnalysis(((FieldReference) variable).fieldInfo);
                if (fieldAnalysis.getVariablesLinkedToMe() == null) {
                    log(DELAYED, "Dependencies of {} have not yet been established", variable.fullyQualifiedName());
                    return DELAYS;
                }
                dependencies = SetUtil.immutableUnion(fieldAnalysis.getVariablesLinkedToMe(), Set.of(variable));
            } else if (variable instanceof ParameterInfo) {
                dependencies = Set.of(variable);
            } else if (variable.isLocal()) {
                if (!variablesLinkedToFieldsAndParameters.isSet()) {
                    log(DELAYED, "Delaying variables linked to method result, local variable's linkage not yet known");
                    return DELAYS;
                }
                dependencies = variablesLinkedToFieldsAndParameters.get().getOrDefault(variable, Set.of());
            } else {
                dependencies = Set.of();
            }
            log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.fullyQualifiedName(), Variable.fullyQualifiedName(dependencies));
            variables.addAll(dependencies);
        }

        variablesLinkedToMethodResult.set(variables);
        // we can perfectly cast here
        MethodAnalysisImpl.Builder methodAnalysisBuilder = (MethodAnalysisImpl.Builder)
                sharedState.evaluationContext.getCurrentMethodAnalysis();
        sharedState.builder.add(methodAnalysisBuilder.new SetProperty(VariableProperty.LINKED, variables.isEmpty() ? Level.FALSE : Level.TRUE));
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", sharedState.logLocation, Variable.fullyQualifiedName(variables));
        return DONE;
    }

    private static Set<Variable> allVariablesLinkedToIncludingMyself(Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters,
                                                                     Variable variable) {
        Set<Variable> result = new HashSet<>();
        recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, variable, result);
        return result;
    }

    private static void recursivelyAddLinkedVariables(Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters,
                                                      Variable variable,
                                                      Set<Variable> result) {
        if (result.contains(variable)) return;
        result.add(variable);
        Set<Variable> linked = variablesLinkedToFieldsAndParameters.get(variable);
        if (linked != null) {
            for (Variable v : linked) recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, v, result);
        }
        // reverse linking
        List<Variable> reverse = variablesLinkedToFieldsAndParameters.entrySet()
                .stream().filter(e -> e.getValue().contains(variable)).map(Map.Entry::getKey).collect(Collectors.toList());
        reverse.forEach(v -> recursivelyAddLinkedVariables(variablesLinkedToFieldsAndParameters, v, result));
    }

    private AnalysisStatus computeContentModifications(SharedState sharedState) {
        if (!variablesLinkedToFieldsAndParameters.isSet()) return DELAYS;

        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final AtomicBoolean changes = new AtomicBoolean();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        sharedState.statementAnalysis.variableStream().forEach(variableInfo -> {
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(variablesLinkedToFieldsAndParameters.get(),
                    variableInfo.variable);
            int summary = sharedState.evaluationContext.summarizeModification(linkedVariables);
            String logLocation = sharedState.logLocation;
            for (Variable linkedVariable : linkedVariables) {
                if (linkedVariable instanceof FieldReference) {
                    FieldInfo fieldInfo = ((FieldReference) linkedVariable).fieldInfo;
                    TransferValue tv;
                    if (fieldSummaries.isSet(fieldInfo)) {
                        tv = fieldSummaries.get(fieldInfo);
                    } else {
                        tv = new TransferValue();
                        fieldSummaries.put(fieldInfo, tv);
                    }
                    int modified = tv.getProperty(VariableProperty.MODIFIED);
                    if (modified == Level.DELAY) {
                        // break the delay in case the variable is not even read
                        int fieldModified;
                        if (summary == Level.DELAY && tv.getProperty(VariableProperty.READ) < Level.TRUE) {
                            fieldModified = Level.FALSE;
                        } else fieldModified = summary;
                        if (fieldModified == Level.DELAY) {
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), logLocation);
                            analysisStatus.set(DELAYS);
                        } else {
                            log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
                                    linkedVariable.fullyQualifiedName(), logLocation);
                            tv.properties.put(VariableProperty.MODIFIED, fieldModified);
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
        });
        return analysisStatus.get() == DELAYS ? (changes.get() ? PROGRESS : DELAYS) : DONE;
    }


    /**
     * Goal is to copy properties from the evaluation context into fieldSummarized, both for fields AND for `this`.
     * There cannot be a delay here.
     * Fields that are not mentioned in the evaluation context should not be present in the fieldSummaries.
     *
     * @param evaluationContext context
     * @return if any change happened to methodAnalysis
     */
    private AnalysisStatus copyFieldAndThisProperties(EvaluationContext evaluationContext, StatementAnalysis statementAnalysis) {
        if (evaluationContext.getIteration() > 0) return DONE;

        statementAnalysis.variableStream().forEach(variableInfo -> {
            if (variableInfo.variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variableInfo.variable).fieldInfo;
                if (!fieldSummaries.isSet(fieldInfo)) {
                    TransferValue tv = new TransferValue();
                    fieldSummaries.put(fieldInfo, tv);
                    copy(variableInfo, tv);
                }
            } else if (variableInfo.variable instanceof This) {
                if (!thisSummary.isSet()) {
                    TransferValue tv = new TransferValue();
                    thisSummary.set(tv);
                    copy(variableInfo, tv);
                }
                int methodDelay = variableInfo.getProperty(VariableProperty.METHOD_DELAY);
                int methodCalled = variableInfo.getProperty(VariableProperty.METHOD_CALLED);

                if (methodDelay != Level.TRUE && methodCalled == Level.TRUE) {
                    int modified = variableInfo.getProperty(VariableProperty.MODIFIED);
                    TransferValue tv = thisSummary.get();
                    tv.properties.put(VariableProperty.MODIFIED, modified);
                }
            }
        });
        // fields that are not present, do not get a mention. But thisSummary needs to be present.
        if (!thisSummary.isSet()) {
            TransferValue tv = new TransferValue();
            thisSummary.set(tv);
            tv.properties.put(VariableProperty.ASSIGNED, Level.FALSE);
            tv.properties.put(VariableProperty.READ, Level.FALSE);
            tv.properties.put(VariableProperty.METHOD_CALLED, Level.FALSE);
        }
        return DONE;
    }

    private static void copy(VariableInfo variableInfo, TransferValue transferValue) {
        for (VariableProperty variableProperty : VariableProperty.NO_DELAY_FROM_STMT_TO_METHOD) {
            int value = variableInfo.getProperty(variableProperty);
            transferValue.properties.put(variableProperty, value);
        }
    }

    private AnalysisStatus copyFieldAssignmentValue(StatementAnalysis statementAnalysis) {
        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final AtomicBoolean changes = new AtomicBoolean();

        statementAnalysis.variableStream().forEach(variableInfo -> {
            if (variableInfo.variable instanceof FieldReference && variableInfo.getProperty(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE) {
                FieldInfo fieldInfo = ((FieldReference) variableInfo.variable).fieldInfo;
                TransferValue tv = fieldSummaries.get(fieldInfo);
                Value value = variableInfo.valueForNextStatement();
                if (value == UnknownValue.NO_VALUE) {
                    analysisStatus.set(DELAYS);
                } else if (!tv.value.isSet()) {
                    changes.set(true);
                    tv.value.set(value);
                }
                // the values of IMMUTABLE, CONTAINER, NOT_NULL, SIZE will be obtained from the value, they need not copying.
                Value stateOnAssignment = variableInfo.getStateOnAssignment();
                if (stateOnAssignment == UnknownValue.NO_VALUE) {
                    analysisStatus.set(DELAYS);
                } else if (stateOnAssignment != UnknownValue.EMPTY && !tv.stateOnAssignment.isSet()) {
                    tv.stateOnAssignment.set(stateOnAssignment);
                    changes.set(true);
                }
            }
        });
        return analysisStatus.get() == DELAYS ? (changes.get() ? PROGRESS : DELAYS) : DONE;
    }

    // a DELAY should only be possible for good reasons
    // context can generally only be delayed when there is a method delay

    private AnalysisStatus copyContextProperties(SharedState sharedState) {
        final AtomicBoolean changes = new AtomicBoolean();
        final AtomicBoolean anyDelay = new AtomicBoolean();

        sharedState.statementAnalysis.variableStream().forEach(variableInfo -> {
            int methodDelay = variableInfo.getProperty(VariableProperty.METHOD_DELAY);
            boolean haveDelay = methodDelay == Level.TRUE || variableInfo.hasNoValue();
            if (haveDelay) anyDelay.set(true);
            if (variableInfo.variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variableInfo.variable).fieldInfo;
                TransferValue tv = fieldSummaries.get(fieldInfo);

                // SIZE
                int size = variableInfo.getProperty(VariableProperty.SIZE);
                int currentSize = tv.properties.getOrDefault(VariableProperty.SIZE, haveDelay ? Level.DELAY : Level.NOT_A_SIZE);
                if (size > currentSize) {
                    tv.properties.put(VariableProperty.SIZE, size);
                    changes.set(true);
                }

                // NOT_NULL (slightly different from SIZE, different type of level)
                int notNull = variableInfo.getProperty(VariableProperty.NOT_NULL);
                int currentNotNull = tv.properties.getOrDefault(VariableProperty.NOT_NULL, haveDelay ? Level.DELAY : MultiLevel.MUTABLE);
                if (notNull > currentNotNull) {
                    tv.properties.put(VariableProperty.NOT_NULL, notNull);
                    changes.set(true);
                }

                int currentDelayResolved = tv.getProperty(VariableProperty.METHOD_DELAY_RESOLVED);
                if (currentDelayResolved == Level.FALSE && !haveDelay) {
                    log(DELAYED, "Delays on {} have now been resolved", variableInfo.variable.fullyQualifiedName());
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.TRUE);
                }
                if (currentDelayResolved == Level.DELAY && haveDelay) {
                    log(DELAYED, "Marking that delays need resolving on {}", variableInfo.variable.fullyQualifiedName());
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.FALSE);
                }
            } else if (variableInfo.variable instanceof ParameterInfo parameterInfo) {

                int notNull = variableInfo.getProperty(VariableProperty.NOT_NULL);
                if (notNull != Level.DELAY) {
                    ParameterAnalysis parameterAnalysis = sharedState.evaluationContext.getParameterAnalysis(parameterInfo);
                    int notNullInParam = parameterAnalysis.getProperty(VariableProperty.NOT_NULL);
                    if (notNullInParam == Level.DELAY) {
                        // we can safely cast here to the builder
                        ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                        sharedState.builder.add(builder.new SetProperty(VariableProperty.NOT_NULL, notNull));
                        changes.set(true);
                    }
                }

                if (parameterInfo.parameterizedType.hasSize(sharedState.evaluationContext.getPrimitives(),
                        sharedState.evaluationContext.getAnalyserContext())) {
                    int size = variableInfo.getProperty(VariableProperty.SIZE);
                    if (size == Level.DELAY && !haveDelay) {
                        // we could not find anything related to size, let's advertise that
                        ParameterAnalysis parameterAnalysis = sharedState.evaluationContext.getParameterAnalysis(parameterInfo);
                        int sizeInParam = parameterAnalysis.getProperty(VariableProperty.SIZE);
                        if (sizeInParam == Level.DELAY) {
                            // we can safely cast here to the builder
                            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                            sharedState.builder.add(builder.new SetProperty(VariableProperty.SIZE, Level.IS_A_SIZE));
                            changes.set(true);
                        }
                    }
                }
            }
        });
        if (!anyDelay.get() && !callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(false);
        }
        return anyDelay.get() ? DELAYS : changes.get() ? PROGRESS : DONE;
    }

    public class SetCircularCallOrUndeclaredFunctionalInterface implements StatementAnalysis.StatementAnalysisModification {
        @Override
        public void run() {
            if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
                callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(true);
            }
        }
    }
}
