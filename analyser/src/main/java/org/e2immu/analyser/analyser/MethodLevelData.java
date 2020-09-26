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
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.AnalysisResult.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.LogTarget.NOT_MODIFIED;
import static org.e2immu.analyser.util.Logger.log;

public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    public final SetOnce<Boolean> callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod = new SetOnce<>();
    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // ************** SUMMARIES
    // in combination with the properties in the super class, this forms the knowledge about the method itself
    public final SetOnce<Value> singleReturnValue = new SetOnce<>();

    public final SetOnce<TransferValue> thisSummary = new SetOnce<>();
    public final SetOnceMap<String, TransferValue> returnStatementSummaries = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, TransferValue> fieldSummaries = new SetOnceMap<>();

    // ************** LINKING

    // this one is the marker that says that links have been established
    public final SetOnce<Map<Variable, Set<Variable>>> variablesLinkedToFieldsAndParameters = new SetOnce<>();

    public final SetOnce<Set<Variable>> variablesLinkedToMethodResult = new SetOnce<>();

    public StatementAnalyserResult finalise(EvaluationContext evaluationContext, VariableData variableData) {
        MethodInfo methodInfo = evaluationContext.getCurrentMethod().methodInfo;
        String logLocation = methodInfo.distinguishingName();
        try {
            StatementAnalyserResult.Builder builder = new StatementAnalyserResult.Builder();

            // start with a one-off copying
            AnalysisResult analysisResult = copyFieldAndThisProperties(evaluationContext, variableData)

                    // this one can be delayed, it copies the field assignment values
                    .combine(copyFieldAssignmentValue(variableData))

                    // SIZE, NOT_NULL into fieldSummaries
                    .combine(copyContextProperties(evaluationContext, variableData, builder))

                    // this method computes, unless delayed, the values for
                    // - linksComputed
                    // - variablesLinkedToFieldsAndParameters
                    // - fieldsLinkedToFieldsAndVariables
                    .combine(establishLinks(variableData, evaluationContext, logLocation))
                    .combine(methodInfo.isConstructor ? DONE : updateVariablesLinkedToMethodResult(evaluationContext, builder, logLocation))
                    .combine(computeContentModifications(evaluationContext, variableData, builder, logLocation));

            return builder.build(analysisResult);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, method {}", logLocation);
            throw rte;
        }
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

    private AnalysisResult establishLinks(VariableData variableData, EvaluationContext evaluationContext, String logLocation) {
        if (variablesLinkedToFieldsAndParameters.isSet()) return DONE;

        // final fields need to have a value set; all the others act as local variables
        boolean someVariablesHaveNotBeenEvaluated = variableData.variables().stream()
                .anyMatch(av -> av.getValue().getCurrentValue() == UnknownValue.NO_VALUE);
        if (someVariablesHaveNotBeenEvaluated) {
            log(DELAYED, "Some variables have not yet been evaluated -- delaying establishing links");
            return DELAYS;
        }
        if (variableData.isDelaysInDependencyGraph()) {
            log(DELAYED, "Dependency graph suffers delays -- delaying establishing links");
            return DELAYS;
        }
        boolean allFieldsFinalDetermined = evaluationContext.getCurrentMethod().methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().allMatch(fieldInfo ->
                fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL) != Level.DELAY);
        if (!allFieldsFinalDetermined) {
            log(DELAYED, "Delay, we don't know about final values for some fields");
            return DELAYS;
        }

        Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();
        variableData.getDependencyGraph().visit((variable, dependencies) -> {
            Set<Variable> fieldAndParameterDependencies = new HashSet<>(variableData.getDependencyGraph().dependencies(variable));
            fieldAndParameterDependencies.removeIf(v -> !(v instanceof FieldReference) && !(v instanceof ParameterInfo));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(fieldAndParameterDependencies::add);
            }
            fieldAndParameterDependencies.remove(variable); // removing myself
            variablesLinkedToFieldsAndParameters.put(variable, fieldAndParameterDependencies);
            log(DEBUG_LINKED_VARIABLES, "Set terminals of {} in {} to [{}]", variable.detailedString(),
                    logLocation, Variable.detailedString(fieldAndParameterDependencies));

            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!fieldSummaries.isSet(fieldInfo)) {
                    fieldSummaries.put(fieldInfo, new TransferValue());
                }
                fieldSummaries.get(fieldInfo).linkedVariables.set(fieldAndParameterDependencies);
                log(LINKED_VARIABLES, "Decided on links of {} in {} to [{}]", variable.detailedString(),
                        logLocation, Variable.detailedString(fieldAndParameterDependencies));
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
    private AnalysisResult updateVariablesLinkedToMethodResult(EvaluationContext evaluationContext, StatementAnalyserResult.Builder builder, String logLocation) {

        if (variablesLinkedToMethodResult.isSet()) return DONE;

        Set<Variable> variables = new HashSet<>();
        boolean waitForLinkedVariables = returnStatementSummaries.stream().anyMatch(e -> !e.getValue().linkedVariables.isSet());
        if (waitForLinkedVariables) {
            log(DELAYED, "Not yet ready to compute linked variables of result of method {}", logLocation);
            return DELAYS;
        }
        Set<Variable> variablesInvolved = returnStatementSummaries.stream()
                .flatMap(e -> e.getValue().linkedVariables.get().stream()).collect(Collectors.toSet());

        for (Variable variable : variablesInvolved) {
            Set<Variable> dependencies;
            if (variable instanceof FieldReference) {
                FieldAnalysis fieldAnalysis = ((FieldReference) variable).fieldInfo.fieldAnalysis.get();
                if (!fieldAnalysis.variablesLinkedToMe.isSet()) {
                    log(DELAYED, "Dependencies of {} have not yet been established", variable.detailedString());
                    return DELAYS;
                }
                dependencies = SetUtil.immutableUnion(((FieldReference) variable).fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get(),
                        Set.of(variable));
            } else if (variable instanceof ParameterInfo) {
                dependencies = Set.of(variable);
            } else if (variable instanceof LocalVariableReference) {
                if (!variablesLinkedToFieldsAndParameters.isSet()) {
                    log(DELAYED, "Delaying variables linked to method result, local variable's linkage not yet known");
                    return DELAYS;
                }
                dependencies = variablesLinkedToFieldsAndParameters.get().getOrDefault(variable, Set.of());
            } else {
                dependencies = Set.of();
            }
            log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.detailedString(), Variable.detailedString(dependencies));
            variables.addAll(dependencies);
        }

        variablesLinkedToMethodResult.set(variables);
        MethodAnalysis methodAnalysis = evaluationContext.getCurrentMethodAnalysis();
        builder.add(methodAnalysis.new SetProperty(VariableProperty.LINKED, variables.isEmpty() ? Level.FALSE : Level.TRUE));
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", logLocation, Variable.detailedString(variables));
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

    private AnalysisResult computeContentModifications(EvaluationContext evaluationContext,
                                                       VariableData variableData,
                                                       StatementAnalyserResult.Builder builder,
                                                       String logLocation) {
        if (!variablesLinkedToFieldsAndParameters.isSet()) return DELAYS;

        AnalysisResult analysisResult = DONE;
        boolean changes = false;

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        for (Map.Entry<String, VariableInfo> entry : variableData.variables()) {
            VariableInfo variableInfo = entry.getValue();
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(variablesLinkedToFieldsAndParameters.get(),
                    variableInfo.getVariable());
            int summary = evaluationContext.summarizeModification(linkedVariables);
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
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.detailedString(), logLocation);
                            analysisResult = DELAYS;
                        } else {
                            log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
                                    linkedVariable.detailedString(), logLocation);
                            tv.properties.put(VariableProperty.MODIFIED, fieldModified);
                            changes = true;
                        }
                    }
                } else if (linkedVariable instanceof ParameterInfo) {
                    ParameterAnalysis parameterAnalysis = evaluationContext.getParameterAnalysis((ParameterInfo) linkedVariable);
                    if (parameterAnalysis.assignedToField.isSet()) {
                        log(NOT_MODIFIED, "Parameter {} is assigned to field {}, not setting @NotModified {} directly",
                                linkedVariable.name(), parameterAnalysis.assignedToField.get().fullyQualifiedName(), summary);
                    } else {
                        if (summary == Level.DELAY) {
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.detailedString(), logLocation);
                            analysisResult = DELAYS;
                        } else {
                            log(NOT_MODIFIED, "Mark {} as {} in {}", linkedVariable.detailedString(),
                                    summary == Level.TRUE ? "@Modified" : "@NotModified", logLocation);
                            int currentModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                            if (currentModified == Level.DELAY) {
                                builder.add(parameterAnalysis.new SetProperty(VariableProperty.MODIFIED, summary));
                                changes = true;
                            }
                        }
                    }
                }
            }
        }
        return analysisResult == DELAYS ? (changes ? PROGRESS : DELAYS) : DONE;
    }


    /**
     * Goal is to copy properties from the evaluation context into fieldSummarized, both for fields AND for `this`.
     * There cannot be a delay here.
     * Fields that are not mentioned in the evaluation context should not be present in the fieldSummaries.
     *
     * @param evaluationContext context
     * @return if any change happened to methodAnalysis
     */
    private AnalysisResult copyFieldAndThisProperties(EvaluationContext evaluationContext, VariableData variableData) {
        if (evaluationContext.getIteration() > 0) return DONE;

        for (Map.Entry<String, VariableInfo> entry : variableData.variables()) {
            VariableInfo variableInfo = entry.getValue();
            Variable variable = variableInfo.getVariable();
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!fieldSummaries.isSet(fieldInfo)) {
                    TransferValue tv = new TransferValue();
                    fieldSummaries.put(fieldInfo, tv);
                    copy(variableInfo, tv);
                }
            } else if (variable instanceof This) {
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
        }
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

    private AnalysisResult copyFieldAssignmentValue(VariableData variableData) {
        boolean changes = false;
        AnalysisResult analysisResult = DONE;
        for (Map.Entry<String, VariableInfo> entry : variableData.variables()) {
            VariableInfo variableInfo = entry.getValue();
            Variable variable = variableInfo.getVariable();
            if (variable instanceof FieldReference && variableInfo.getProperty(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                TransferValue tv = fieldSummaries.get(fieldInfo);
                Value value = variableInfo.getCurrentValue();
                if (value == UnknownValue.NO_VALUE) {
                    analysisResult = DELAYS;
                } else if (!tv.value.isSet()) {
                    changes = true;
                    tv.value.set(value);
                }
                // the values of IMMUTABLE, CONTAINER, NOT_NULL, SIZE will be obtained from the value, they need not copying.
                Value stateOnAssignment = variableInfo.getStateOnAssignment();
                if (stateOnAssignment == UnknownValue.NO_VALUE) {
                    analysisResult = DELAYS;
                } else if (stateOnAssignment != UnknownValue.EMPTY && !tv.stateOnAssignment.isSet()) {
                    tv.stateOnAssignment.set(stateOnAssignment);
                    changes = true;
                }
            }
        }
        return analysisResult == DELAYS ? (changes ? PROGRESS : DELAYS) : DONE;
    }

    // a DELAY should only be possible for good reasons
    // context can generally only be delayed when there is a method delay

    private AnalysisResult copyContextProperties(EvaluationContext evaluationContext,
                                                 VariableData variableData,
                                                 StatementAnalyserResult.Builder builder) {
        boolean changes = false;
        boolean anyDelay = false;
        for (Map.Entry<String, VariableInfo> entry : variableData.variables()) {
            VariableInfo variableInfo = entry.getValue();
            Variable variable = variableInfo.getVariable();
            int methodDelay = variableInfo.getProperty(VariableProperty.METHOD_DELAY);
            boolean haveDelay = methodDelay == Level.TRUE || variableInfo.getCurrentValue() == UnknownValue.NO_VALUE;
            if (haveDelay) anyDelay = true;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                TransferValue tv = fieldSummaries.get(fieldInfo);

                // SIZE
                int size = variableInfo.getProperty(VariableProperty.SIZE);
                int currentSize = tv.properties.getOtherwise(VariableProperty.SIZE, haveDelay ? Level.DELAY : Level.NOT_A_SIZE);
                if (size > currentSize) {
                    tv.properties.put(VariableProperty.SIZE, size);
                    changes = true;
                }

                // NOT_NULL (slightly different from SIZE, different type of level)
                int notNull = variableInfo.getProperty(VariableProperty.NOT_NULL);
                int currentNotNull = tv.properties.getOtherwise(VariableProperty.NOT_NULL, haveDelay ? Level.DELAY : MultiLevel.MUTABLE);
                if (notNull > currentNotNull) {
                    tv.properties.put(VariableProperty.NOT_NULL, notNull);
                    changes = true;
                }

                int currentDelayResolved = tv.getProperty(VariableProperty.METHOD_DELAY_RESOLVED);
                if (currentDelayResolved == Level.FALSE && !haveDelay) {
                    log(DELAYED, "Delays on {} have now been resolved", variable.name());
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.TRUE);
                }
                if (currentDelayResolved == Level.DELAY && haveDelay) {
                    log(DELAYED, "Marking that delays need resolving on {}", variable.name());
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.FALSE);
                }
            } else if (variable instanceof ParameterInfo) {
                ParameterInfo parameterInfo = (ParameterInfo) variable;

                if (parameterInfo.parameterizedType.hasSize()) {
                    int size = variableInfo.getProperty(VariableProperty.SIZE);
                    if (size == Level.DELAY && !haveDelay) {
                        // we could not find anything related to size, let's advertise that
                        int sizeInParam = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.SIZE);
                        if (sizeInParam == Level.DELAY) {
                            ParameterAnalysis parameterAnalysis = evaluationContext.getParameterAnalysis(parameterInfo);
                            builder.add(parameterAnalysis.new SetProperty(VariableProperty.SIZE, Level.IS_A_SIZE));
                            changes = true;
                        }
                    }
                }
            }
        }
        if (!anyDelay && !callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(false);
        }
        return anyDelay ? DELAYS : changes ? PROGRESS : DONE;
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
