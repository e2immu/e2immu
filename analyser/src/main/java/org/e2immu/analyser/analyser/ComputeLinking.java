package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.VariableProperties;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

public class ComputeLinking {
    public final ParameterAnalyser parameterAnalyser;
    public final TypeContext typeContext;

    public ComputeLinking(TypeContext typeContext, ParameterAnalyser parameterAnalyser) {
        this.parameterAnalyser = parameterAnalyser;
        this.typeContext = typeContext;
    }

    // we need a recursive structure because local variables can be defined in blocks, a little later,
    // they disappear again. But, we should also be able to add properties simply for a block, so that those
    // properties disappear when that level disappears

    public boolean computeVariablePropertiesOfMethod(List<NumberedStatement> statements, MethodInfo methodInfo,
                                                      VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
        StatementAnalyser statementAnalyser = new StatementAnalyser(typeContext, methodInfo);
        if (statementAnalyser.computeVariablePropertiesOfBlock(statements.get(0), methodProperties)) changes = true;

        if (establishLinks(methodInfo, methodProperties)) changes = true;

        if (!methodInfo.isConstructor && updateVariablesLinkedToMethodResult(statements, methodInfo, methodProperties))
            changes = true;

        if (updateAnnotationsFromMethodProperties(methodAnalysis, methodProperties)) changes = true;
        if (updateParameterAnnotationsFromMethodProperties(methodInfo, methodProperties)) changes = true;
        if (updateFieldAnnotationsFromMethodProperties(methodInfo, methodProperties)) changes = true;

        return changes;
    }

    private boolean updateVariablesLinkedToMethodResult(List<NumberedStatement> numberedStatements,
                                                        MethodInfo methodInfo,
                                                        VariableProperties methodProperties) {
        if (methodInfo.methodAnalysis.variablesLinkedToMethodResult.isSet()) return false;

        Set<Variable> variables = new HashSet<>();
        for (NumberedStatement numberedStatement : numberedStatements) {
            if (numberedStatement.statement instanceof ReturnStatement) {
                if (numberedStatement.linkedVariables.isSet()) { // this implies the statement is a return statement
                    for (Variable variable : numberedStatement.linkedVariables.get()) {
                        Set<Variable> dependencies;
                        if (variable instanceof FieldReference) {
                            if (!((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
                                log(LINKED_VARIABLES, "Dependencies of {} have not yet been established", variable.detailedString());
                                return false;
                            }
                            dependencies = ((FieldReference) variable).fieldInfo.fieldAnalysis.variablesLinkedToMe.get();
                        } else if (variable instanceof ParameterInfo) {
                            dependencies = Set.of(variable);
                        } else if (variable instanceof LocalVariableReference) {
                            dependencies = methodProperties.variablesLinkedToFieldsAndParameters.getOrDefault(variable, Set.of());
                        } else {
                            dependencies = Set.of(); // TODO This...
                        }
                        log(LINKED_VARIABLES, "Dependencies of {} are [{}]", variable.detailedString(), Variable.detailedString(dependencies));
                        variables.addAll(dependencies);
                    }
                } else {
                    log(LINKED_VARIABLES, "Not yet ready to compute linked variables of method {}", methodInfo.fullyQualifiedName());
                    return false;
                }
            }
        }
        methodInfo.methodAnalysis.variablesLinkedToMethodResult.set(variables);
        methodInfo.methodAnalysis.annotations.put(typeContext.linked.get(), !variables.isEmpty());
        log(LINKED_VARIABLES, "Set variables linked to result of {} to [{}]", methodInfo.fullyQualifiedName(), Variable.detailedString(variables));
        return true;
    }

    // goal: we need to establish that in this method, recursively, a given field is linked to one or more fields or parameters
    // we need to find out if a parameter is linked, recursively, to another field or parameter
    // local variables need to be taken out of the loop

    // in essence: moving from the dependency graph to the MethodAnalysis.variablesLinkedToFieldsAndParameters data structure
    // gets rid of local vars and transitive links
    private static boolean establishLinks(MethodInfo methodInfo, VariableProperties methodProperties) {
        log(LINKED_VARIABLES, "Establishing links, copying from dependency graph of size {}",
                methodProperties.dependencyGraph.size());
        AtomicBoolean changes = new AtomicBoolean();
        methodProperties.dependencyGraph.visit((variable, dependencies) -> {
            Set<Variable> terminals = new HashSet<>(methodProperties.dependencyGraph.dependencies(variable));
            if (dependencies != null) {
                dependencies.stream().filter(d -> d instanceof ParameterInfo).forEach(terminals::add);
            }
            terminals.remove(variable); // removing myself
            methodProperties.variablesLinkedToFieldsAndParameters.put(variable, terminals);
            log(LINKED_VARIABLES, "MA: Set terminals of {} in {} to {}", variable.detailedString(),
                    methodInfo.fullyQualifiedName(), Variable.detailedString(terminals));

            if (variable instanceof FieldReference) {
                if (!methodInfo.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(variable)) {
                    methodInfo.methodAnalysis.fieldsLinkedToFieldsAndVariables.put(variable, terminals);
                    changes.set(true);
                    log(LINKED_VARIABLES, "MA: Decide on links of {} in {} to {}", variable.detailedString(),
                            methodInfo.fullyQualifiedName(), Variable.detailedString(terminals));
                }
            }
        });
        return changes.get();
    }

    private boolean updateAnnotationsFromMethodProperties(MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        boolean changes = false;
        for (Variable variable : methodProperties.variableProperties.keySet()) {
            Set<Variable> linkedVariables = allVariablesLinkedToIncludingMyself(methodProperties, variable);
            Boolean directContentModification = summarizeModification(methodProperties, linkedVariables);
            log(MODIFY_CONTENT, "Starting at {}, we loop over {} to set direct modification {}", variable.detailedString(),
                    Variable.detailedString(linkedVariables), directContentModification);
            for (Variable linkedVariable : linkedVariables) {
                if ((linkedVariable instanceof FieldReference)) {
                    if (!methodAnalysis.contentModifications.isSet(linkedVariable)) {
                        boolean directlyModifiedField = directContentModification == Boolean.TRUE;
                        log(MODIFY_CONTENT, "MA: Mark that the content of {} has " + (directlyModifiedField ? "" : "not ") +
                                "been modified", linkedVariable.detailedString());
                        methodAnalysis.contentModifications.put(linkedVariable, directlyModifiedField);
                        changes = true;
                    }
                } else if (linkedVariable instanceof ParameterInfo) {
                    parameterAnalyser.notModified((ParameterInfo) linkedVariable, directContentModification);
                }
            }
        }
        return changes;
    }

    private Boolean summarizeModification(VariableProperties methodProperties, Set<Variable> linkedVariables) {
        boolean hasDelays = false;
        for (Variable variable : linkedVariables) {
            if (variable instanceof FieldReference) {
                Boolean notModified = ((FieldReference) variable).fieldInfo.isNotModified(typeContext);
                if (notModified == null) hasDelays = true;
                else if (!notModified) return true;
            }
            // local, parameter, field... data from statement analyser
            VariableProperties.AboutVariable properties = methodProperties.variableProperties.get(variable);
            // properties can be null (variable out of scope)
            if (properties != null && properties.properties.contains(VariableProperty.CONTENT_MODIFIED)) return true;
        }
        return hasDelays ? null : false;
    }


    private Set<Variable> allVariablesLinkedToIncludingMyself(VariableProperties methodProperties, Variable variable) {
        Set<Variable> result = new HashSet<>();
        recursivelyAddLinkedVariables(methodProperties, variable, result);
        return result;
    }

    private void recursivelyAddLinkedVariables(VariableProperties methodProperties, Variable variable, Set<Variable> result) {
        if (result.contains(variable)) return;
        result.add(variable);
        Set<Variable> linked = methodProperties.variablesLinkedToFieldsAndParameters.get(variable);
        if (linked != null) {
            for (Variable v : linked) recursivelyAddLinkedVariables(methodProperties, v, result);
        }
        // reverse linking
        List<Variable> reverse = methodProperties.variablesLinkedToFieldsAndParameters.entrySet()
                .stream().filter(e -> e.getValue().contains(variable)).map(Map.Entry::getKey).collect(Collectors.toList());
        reverse.forEach(v -> recursivelyAddLinkedVariables(methodProperties, v, result));
    }

    private boolean updateParameterAnnotationsFromMethodProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : methodProperties.variableProperties.entrySet()) {
            Set<VariableProperty> properties = entry.getValue().properties;
            Variable variable = entry.getKey();
            if (variable instanceof ParameterInfo) {
                if (properties.contains(VariableProperty.ASSIGNED)
                        && !methodInfo.methodAnalysis.parameterAssignments.isSet((ParameterInfo) variable)) {
                    typeContext.addMessage(Message.Severity.ERROR,
                            "Parameter " + variable.detailedString() + " should not be assigned to");
                    methodInfo.methodAnalysis.parameterAssignments.put((ParameterInfo) variable, true);
                    changes = true;
                }
            }
        }
        return changes;
    }

    private static boolean updateFieldAnnotationsFromMethodProperties(MethodInfo methodInfo, VariableProperties methodProperties) {
        boolean changes = false;
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
        for (Map.Entry<Variable, VariableProperties.AboutVariable> entry : methodProperties.variableProperties.entrySet()) {
            Variable variable = entry.getKey();
            Set<VariableProperty> properties = entry.getValue().properties;
            if (variable instanceof FieldReference) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                    boolean isModified = properties.contains(VariableProperty.ASSIGNED);
                    methodAnalysis.fieldAssignments.put(fieldInfo, isModified);
                    log(ANALYSER, "Mark that {} is modified? {} in {}", fieldInfo.name, isModified, methodInfo.fullyQualifiedName());
                    changes = true;
                }
                Value currentValue = entry.getValue().getCurrentValue();
                if (currentValue != UnknownValue.NO_VALUE && properties.contains(VariableProperty.ASSIGNED) &&
                        !properties.contains(VariableProperty.ASSIGNED_MULTIPLE_TIMES) &&
                        !methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                    log(ANALYSER, "Single assignment of field {} to {}", fieldInfo.fullyQualifiedName(), currentValue);
                    methodAnalysis.fieldAssignmentValues.put(fieldInfo, currentValue);
                    changes = true;
                }
                if (properties.contains(VariableProperty.READ) && !methodAnalysis.fieldRead.isSet(fieldInfo)) {
                    log(ANALYSER, "Mark that the content of field {} has been read", variable.detailedString());
                    methodAnalysis.fieldRead.put(fieldInfo, true);
                    changes = true;
                }
            } else if (variable instanceof This && properties.contains(VariableProperty.READ)) {
                if (!methodAnalysis.thisRead.isSet()) {
                    log(ANALYSER, "Mark that 'this' has been read in {}", variable.detailedString());
                    methodAnalysis.thisRead.set(true);
                    changes = true;
                }
            }
        }

        for (FieldInfo fieldInfo : methodInfo.typeInfo.typeInspection.get().fields) {
            if (!methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                methodAnalysis.fieldAssignments.put(fieldInfo, false);
                changes = true;
                log(ANALYSER, "Mark field {} not modified in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
            }
            //FieldReference fieldReference = new FieldReference(fieldInfo, methodProperties.thisVariable);
            //if (!methodAnalysis.directContentModifications.isSet(fieldReference)) {
            //methodAnalysis.directContentModifications.put(fieldReference, false);
            //changes = true;
            //log(MODIFY_CONTENT, "Mark field {}'s content not modified in {}, not present, not delayed",
            //        fieldInfo.fullyQualifiedName(), methodInfo.name);
            //}
            if (!methodAnalysis.fieldRead.isSet(fieldInfo)) {
                methodAnalysis.fieldRead.put(fieldInfo, false);
                log(ANALYSER, "Mark field {} as ignore in {}, not present", fieldInfo.fullyQualifiedName(), methodInfo.name);
                changes = true;
            }
        }
        return changes;
    }
}
