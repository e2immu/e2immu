package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class ComputeLinking {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeLinking.class);

    public final TypeContext typeContext;

    public ComputeLinking(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    // we need a recursive structure because local variables can be defined in blocks, a little later,
    // they disappear again. But, we should also be able to add properties simply for a block, so that those
    // properties disappear when that level disappears

    public boolean computeVariablePropertiesOfMethod(List<NumberedStatement> statements, MethodInfo methodInfo,
                                                     VariableProperties methodProperties) {
        boolean changes = false;
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            StatementAnalyser statementAnalyser = new StatementAnalyser(typeContext, methodInfo);
            NumberedStatement startStatement = statements.get(0);
            if (statementAnalyser.computeVariablePropertiesOfBlock(startStatement, methodProperties)) changes = true;

            // this method computes, ONLY THE FIRST TIME, the values for READ, ASSIGNED, METHOD_CALLED on fields and this
            if (copyFieldAndThisProperties(methodInfo, methodProperties)) changes = true;

            // this one can be delayed, it copies the field assignment values
            if (copyFieldAssignmentValue(methodInfo, methodProperties)) changes = true;

            // SIZE, NOT_NULL into fieldSummaries
            if (copyContextProperties(methodInfo, methodProperties)) changes = true;

            // this method computes, unless delayed, the values for
            // - linksComputed
            // - variablesLinkedToFieldsAndParameters
            // - fieldsLinkedToFieldsAndVariables
            if (establishLinks(methodInfo, methodAnalysis, methodProperties)) changes = true;
            if (!methodInfo.isConstructor && updateVariablesLinkedToMethodResult(methodInfo, methodAnalysis))
                changes = true;

            if (computeContentModifications(methodInfo, methodAnalysis, methodProperties)) changes = true;

            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, method {}", methodInfo.fullyQualifiedName());
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

    private boolean updateVariablesLinkedToMethodResult(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {

        if (methodAnalysis.variablesLinkedToMethodResult.isSet()) return false;

        Set<Variable> variables = new HashSet<>();
        boolean waitForLinkedVariables = methodAnalysis.returnStatementSummaries.stream().anyMatch(e -> !e.getValue().linkedVariables.isSet());
        if (waitForLinkedVariables) {
            log(DELAYED, "Not yet ready to compute linked variables of result of method {}", methodInfo.fullyQualifiedName());
            return false;
        }
        Set<Variable> variablesInvolved = methodAnalysis.returnStatementSummaries.stream()
                .flatMap(e -> e.getValue().linkedVariables.get().stream()).collect(Collectors.toSet());
        for (Variable variable : variablesInvolved) {
            Set<Variable> dependencies;
            if (variable instanceof FieldReference) {
                if (!((FieldReference) variable).fieldInfo.fieldAnalysis.get().variablesLinkedToMe.isSet()) {
                    log(DELAYED, "Dependencies of {} have not yet been established", variable.detailedString());
                    return false;
                }
                dependencies = SetUtil.immutableUnion(((FieldReference) variable).fieldInfo.fieldAnalysis.get().variablesLinkedToMe.get(),
                        Set.of(variable));
            } else if (variable instanceof ParameterInfo) {
                dependencies = Set.of(variable);
            } else if (variable instanceof LocalVariableReference) {
                if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
                    log(DELAYED, "Delaying variables linked to method result, local variable's linkage not yet known");
                    return false;
                }
                dependencies = methodAnalysis.variablesLinkedToFieldsAndParameters.get().getOrDefault(variable, Set.of());
            } else {
                dependencies = Set.of();
            }
            log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.detailedString(), Variable.detailedString(dependencies));
            variables.addAll(dependencies);
        }

        methodAnalysis.variablesLinkedToMethodResult.set(variables);
        methodAnalysis.setProperty(VariableProperty.LINKED, !variables.isEmpty());
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", methodInfo.fullyQualifiedName(), Variable.detailedString(variables));
        return true;
    }

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

    private static boolean establishLinks(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) return false;

        // final fields need to have a value set; all the others act as local variables
        boolean someVariablesHaveNotBeenEvaluated = methodProperties.variableProperties().stream()
                .anyMatch(av -> av.getCurrentValue() == UnknownValue.NO_VALUE);
        if (someVariablesHaveNotBeenEvaluated) {
            log(DELAYED, "Some variables have not yet been evaluated -- delaying establishing links");
            return false;
        }
        if (!methodProperties.dependencyGraphBestCase.equalTransitiveTerminals(methodProperties.dependencyGraphWorstCase)) {
            log(DELAYED, "Best and worst case dependency graph transitive terminal sets differ -- delaying establishing links");
            return false;
        }
        boolean allFieldsFinalDetermined = methodInfo.typeInfo.typeInspection.get().fields.stream().allMatch(fieldInfo ->
                fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL) != Level.DELAY);
        if (!allFieldsFinalDetermined) {
            log(DELAYED, "Delay, we don't know about final values for some fields");
            return false;
        }
        AtomicBoolean changes = new AtomicBoolean();
        Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();

        methodProperties.dependencyGraphBestCase.visit((variable, dependencies) -> {
            Set<Variable> fieldAndParameterDependencies = new HashSet<>(methodProperties.dependencyGraphBestCase.dependencies(variable));
            fieldAndParameterDependencies.removeIf(v -> !(v instanceof FieldReference) && !(v instanceof ParameterInfo));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(fieldAndParameterDependencies::add);
            }
            fieldAndParameterDependencies.remove(variable); // removing myself
            variablesLinkedToFieldsAndParameters.put(variable, fieldAndParameterDependencies);
            log(DEBUG_LINKED_VARIABLES, "Set terminals of {} in {} to [{}]", variable.detailedString(),
                    methodInfo.fullyQualifiedName(), Variable.detailedString(fieldAndParameterDependencies));

            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!methodAnalysis.fieldSummaries.isSet(fieldInfo)) {
                    methodAnalysis.fieldSummaries.put(fieldInfo, new TransferValue());
                }
                methodAnalysis.fieldSummaries.get(fieldInfo).linkedVariables.set(fieldAndParameterDependencies);
                changes.set(true);
                log(LINKED_VARIABLES, "Decided on links of {} in {} to [{}]", variable.detailedString(),
                        methodInfo.fullyQualifiedName(), Variable.detailedString(fieldAndParameterDependencies));
            }
        });
        // set all the linkedVariables for fields not in the dependency graph
        methodAnalysis.fieldSummaries.stream().filter(e -> !e.getValue().linkedVariables.isSet())
                .forEach(e -> {
                    e.getValue().linkedVariables.set(Set.of());
                    log(LINKED_VARIABLES, "Clear linked variables of {} in {}", e.getKey().name, methodInfo.distinguishingName());
                });
        log(LINKED_VARIABLES, "Set variablesLinkedToFieldsAndParameters to true for {}", methodInfo.fullyQualifiedName());
        methodAnalysis.variablesLinkedToFieldsAndParameters.set(variablesLinkedToFieldsAndParameters);
        return true;
    }

    private boolean computeContentModifications(MethodInfo methodInfo,
                                                MethodAnalysis methodAnalysis,
                                                VariableProperties methodProperties) {
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) return false;

        boolean changes = false;
        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        List<AboutVariable> aboutVariables = new ArrayList<>(methodProperties.variableProperties());
        for (AboutVariable aboutVariable : aboutVariables) {
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(methodAnalysis.variablesLinkedToFieldsAndParameters.get(),
                    aboutVariable.variable);
            int summary = summarizeModification(methodProperties, linkedVariables);
            for (Variable linkedVariable : linkedVariables) {
                if (linkedVariable instanceof FieldReference) {
                    FieldInfo fieldInfo = ((FieldReference) linkedVariable).fieldInfo;
                    TransferValue tv;
                    if (methodAnalysis.fieldSummaries.isSet(fieldInfo)) {
                        tv = methodAnalysis.fieldSummaries.get(fieldInfo);
                    } else {
                        tv = new TransferValue();
                        methodAnalysis.fieldSummaries.put(fieldInfo, tv);
                    }
                    int modified = tv.getProperty(VariableProperty.MODIFIED);
                    if (modified == Level.DELAY) {
                        // break the delay in case the variable is not even read
                        int fieldModified;
                        if (summary == Level.DELAY && tv.getProperty(VariableProperty.READ) < Level.TRUE) {
                            fieldModified = Level.FALSE; // TODO should this be TRUE? absence of information
                        } else fieldModified = summary;
                        if (fieldModified == Level.DELAY) {
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.detailedString(), methodInfo.distinguishingName());
                        } else {
                            log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @NotModified in {}",
                                    linkedVariable.detailedString(), methodInfo.distinguishingName());
                            tv.properties.put(VariableProperty.MODIFIED, fieldModified);
                            changes = true;
                        }
                    }
                } else if (linkedVariable instanceof ParameterInfo) {
                    ParameterAnalysis parameterAnalysis = ((ParameterInfo) linkedVariable).parameterAnalysis.get();
                    if (parameterAnalysis.assignedToField.isSet()) {
                        log(NOT_MODIFIED, "Parameter {} is assigned to field {}, not setting @NotModified {} directly",
                                linkedVariable.name(), parameterAnalysis.assignedToField.get().fullyQualifiedName(), summary);
                    } else {
                        if (summary == Level.DELAY) {
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.detailedString(), methodInfo.distinguishingName());
                        } else {
                            log(NOT_MODIFIED, "Mark {} as {} in {}", linkedVariable.detailedString(),
                                    summary == Level.TRUE ? "@Modified" : "@NotModified",
                                    methodInfo.distinguishingName());
                            int currentModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                            if (currentModified == Level.DELAY) {
                                parameterAnalysis.setProperty(VariableProperty.MODIFIED, summary);
                                changes = true;
                            }
                        }
                    }
                }
            }
        }
        return changes;
    }

    private int summarizeModification(VariableProperties methodProperties, Set<Variable> linkedVariables) {
        boolean hasDelays = false;
        for (Variable variable : linkedVariables) {
            int modified = methodProperties.getProperty(variable, VariableProperty.MODIFIED);
            int methodDelay = methodProperties.getProperty(variable, VariableProperty.METHOD_DELAY);
            if (modified == Level.TRUE) return Level.TRUE;
            if (methodDelay == Level.TRUE) hasDelays = true;
        }
        return hasDelays ? Level.DELAY : Level.FALSE;
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

    /**
     * Goal is to copy properties from the evaluation context into fieldSummarized, both for fields AND for `this`.
     * There cannot be a delay here.
     * Fields that are not mentioned in the evaluation context should not be present in the fieldSummaries.
     *
     * @param methodInfo       current method
     * @param methodProperties context
     * @return if any change happened to methodAnalysis
     */
    private static boolean copyFieldAndThisProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!methodAnalysis.fieldSummaries.isSet(fieldInfo)) {
                    TransferValue tv = new TransferValue();
                    methodAnalysis.fieldSummaries.put(fieldInfo, tv);
                    changes = true;
                    copy(aboutVariable, tv);
                }
            } else if (variable instanceof This) {
                if (!methodAnalysis.thisSummary.isSet()) {
                    TransferValue tv = new TransferValue();
                    methodAnalysis.thisSummary.set(tv);
                    changes = true;
                    copy(aboutVariable, tv);
                }
            }
        }
        // fields that are not present, do not get a mention. But thisSummary needs to be present.
        if (!methodAnalysis.thisSummary.isSet()) {
            TransferValue tv = new TransferValue();
            methodAnalysis.thisSummary.set(tv);
            tv.properties.put(VariableProperty.ASSIGNED, Level.FALSE);
            tv.properties.put(VariableProperty.READ, Level.FALSE);
            tv.properties.put(VariableProperty.METHOD_CALLED, Level.FALSE);
            changes = true;
        }
        return changes;
    }

    private static void copy(AboutVariable aboutVariable, TransferValue transferValue) {
        for (VariableProperty variableProperty : VariableProperty.NO_DELAY_FROM_STMT_TO_METHOD) {
            int value = aboutVariable.getProperty(variableProperty);
            transferValue.properties.put(variableProperty, value);
        }
    }

    private static boolean copyFieldAssignmentValue(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            if (variable instanceof FieldReference && aboutVariable.getProperty(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);
                Value value = aboutVariable.getCurrentValue();
                if (value != UnknownValue.NO_VALUE && !tv.value.isSet()) {
                    changes = true;
                    tv.value.set(value);
                    // the values of IMMUTABLE, CONTAINER, NOT_NULL, SIZE will be obtained from the value, they need not copying.
                }
            }
        }
        return changes;
    }

    // a DELAY should only be possible for good reasons
    // context can generally only be delayed when there is a method delay

    private static boolean copyContextProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            int methodDelay = aboutVariable.getProperty(VariableProperty.METHOD_DELAY);
            boolean haveDelay = methodDelay == Level.TRUE || aboutVariable.getCurrentValue() == UnknownValue.NO_VALUE;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);

                // SIZE
                int size = aboutVariable.getProperty(VariableProperty.SIZE);
                int currentSize = tv.properties.getOtherwise(VariableProperty.SIZE, haveDelay ? Level.DELAY : Level.NOT_A_SIZE);
                if (size > currentSize) {
                    tv.properties.put(VariableProperty.SIZE, size);
                    changes = true;
                }

                // NOT_NULL (slightly different from SIZE, different type of level)
                int notNull = aboutVariable.getProperty(VariableProperty.NOT_NULL);
                int currentNotNull = tv.properties.getOtherwise(VariableProperty.NOT_NULL, haveDelay ? Level.DELAY : MultiLevel.MUTABLE);
                if (notNull > currentNotNull) {
                    tv.properties.put(VariableProperty.NOT_NULL, notNull);
                    changes = true;
                }

                int currentDelayResolved = tv.getProperty(VariableProperty.METHOD_DELAY_RESOLVED);
                if (currentDelayResolved == Level.TRUE && !haveDelay) {
                    log(DELAYED, "Delays on {} have been resolved", aboutVariable.name);
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, 3);
                }
                if (currentDelayResolved == Level.DELAY && haveDelay) {
                    log(DELAYED, "Marking that delays need resolving on {}", aboutVariable.name);
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.TRUE);
                }
            }
        }

        return changes;
    }
}
