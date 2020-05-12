/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.analyser.methodanalysercomponent.StaticModifier;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final TypeContext typeContext;
    private final ParameterAnalyser parameterAnalyser;
    private final ComputeLinking computeLinking;

    public MethodAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
        this.parameterAnalyser = new ParameterAnalyser(typeContext);
        this.computeLinking = new ComputeLinking(typeContext, parameterAnalyser);
    }

    public void check(MethodInfo methodInfo) {
        // before we check, we copy the properties into annotations
        methodInfo.methodAnalysis.transferPropertiesToAnnotations(typeContext, methodInfo::minimalValueByDefinition);

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, typeContext.independent.get());
        check(methodInfo, NotModified.class, typeContext.notModified.get());

        if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
            check(methodInfo, NotNull.class, typeContext.notNull.get());
            check(methodInfo, Fluent.class, typeContext.fluent.get());
            check(methodInfo, Identity.class, typeContext.identity.get());
            CheckConstant.checkConstantForMethods(typeContext, methodInfo);
        }

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Method " + methodInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getSimpleName()));
    }

    public boolean analyse(MethodInfo methodInfo, VariableProperties methodProperties) {
        List<Statement> statements = methodInfo.methodInspection.get().methodBody.get().statements;
        if (!statements.isEmpty()) {
            return analyseMethod(methodInfo, methodProperties, statements);
        }
        return false;
    }

    // return when there have been changes
    private boolean analyseMethod(MethodInfo methodInfo, VariableProperties methodProperties, List<Statement> statements) {
        boolean changes = false;

        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());

        if (!methodInfo.methodAnalysis.numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            CreateNumberedStatements.recursivelyCreateNumberedStatements(statements, indices, numberedStatements,
                    new SideEffectContext(methodInfo));
            methodInfo.methodAnalysis.numberedStatements.set(ImmutableList.copyOf(numberedStatements));
            changes = true;
        }
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            methodProperties.createLocalVariableOrParameter(parameterInfo);
        }
        if (analyseFlow(methodInfo, methodProperties)) changes = true;
        return changes;
    }

    private boolean analyseFlow(MethodInfo methodInfo, VariableProperties methodProperties) {
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeLinking.computeVariablePropertiesOfMethod(numberedStatements, methodInfo, methodProperties))
                changes = true;
            if (methodIsIndependent(methodInfo, methodAnalysis)) changes = true;
            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            if (!methodInfo.isConstructor) {
                List<NumberedStatement> returnStatements = methodAnalysis.returnStatements.get();
                if (!returnStatements.isEmpty()) {
                    if (propertiesOfReturnStatements(methodProperties, returnStatements, methodInfo, methodAnalysis))
                        changes = true;
                    // methodIsConstant makes use of methodIsNotNull, so order is important
                    if (methodIsConstant(returnStatements, methodInfo, methodAnalysis)) changes = true;
                }
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements, methodInfo, methodAnalysis)) changes = true;
                }
                StaticModifier.detectMissingStaticStatement(typeContext, methodInfo, methodAnalysis);
                if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;
            }
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    // part of @UtilityClass computation in the type analyser
    private boolean methodCreatesObjectOfSelf(List<NumberedStatement> numberedStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.createObjectOfSelf.isSet()) {
            boolean createSelf = numberedStatements.stream().flatMap(ns -> Statement.findExpressionRecursivelyInStatements(ns.statement, NewObject.class))
                    .anyMatch(no -> no.parameterizedType.typeInfo == methodInfo.typeInfo);
            log(UTILITY_CLASS, "Is {} a static non-constructor method that creates self? {}", methodInfo.fullyQualifiedName(), createSelf);
            methodAnalysis.createObjectOfSelf.set(createSelf);
            return true;
        }
        return false;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private boolean methodIsConstant(List<NumberedStatement> returnStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.singleReturnValue.isSet()) return false;

        boolean allReturnValuesSet = returnStatements.stream().allMatch(ns -> ns.returnValue.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return false;
        }
        Value value;
        if (returnStatements.size() == 1) {
            value = returnStatements.get(0).returnValue.get();
        } else {
            value = new MethodValue(methodInfo, UnknownValue.UNKNOWN_VALUE, List.of());
        }
        methodAnalysis.singleReturnValue.set(value);
        boolean isConstant = value instanceof Constant;
        AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
        methodAnalysis.annotations.put(constantAnnotation, isConstant);
        methodAnalysis.setProperty(VariableProperty.CONSTANT, Level.TRUE);
        log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
        return true;
    }

    private final VariableProperty[] PROPERTIES_OF_RETURN_STATEMENTS = new VariableProperty[]{
            VariableProperty.IDENTITY,
            VariableProperty.FLUENT,
            VariableProperty.NOT_NULL,
            VariableProperty.CONTAINER,
            VariableProperty.IMMUTABLE};

    private boolean propertiesOfReturnStatements(EvaluationContext evaluationContext,
                                                 List<NumberedStatement> returnStatements,
                                                 MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis) {
        boolean changes = false;
        for (VariableProperty variableProperty : PROPERTIES_OF_RETURN_STATEMENTS) {
            if (propertyOfReturnStatements(evaluationContext, variableProperty, returnStatements, methodInfo, methodAnalysis))
                changes = true;
        }
        return changes;
    }

    private boolean propertyOfReturnStatements(EvaluationContext evaluationContext,
                                               VariableProperty variableProperty,
                                               List<NumberedStatement> returnStatements,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis) {
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (!variableProperty.canImprove && currentValue != Level.DELAY) return false;

        int value = returnStatements.stream().mapToInt(ns -> ns.getProperty(evaluationContext, variableProperty)).min().orElse(Level.DELAY);
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return true;
    }

    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.getProperty(VariableProperty.NOT_MODIFIED) != Level.DELAY) return false;

        // first step, check that no fields are being assigned
        boolean fieldAssignments = methodAnalysis.fieldAssignments.stream().anyMatch(Map.Entry::getValue);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.NOT_MODIFIED, Level.FALSE);
            return true;
        }
        // second step, check that no fields are modified
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @NotModified yet, delaying because linking not computed");
            return false;
        }
        boolean isNotModified = methodAnalysis.contentModifications
                .stream()
                .filter(e -> e.getKey() instanceof FieldReference)
                .noneMatch(Map.Entry::getValue);
        if (!isNotModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.contentModifications.stream().filter(e -> e.getKey() instanceof FieldReference)
                            .filter(Map.Entry::getValue).map(e -> e.getKey().detailedString()).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (isNotModified) {
            int allMethodCallsNotModified = methodAnalysis.localMethodsCalled.get().stream()
                    .mapToInt(mi -> mi.methodAnalysis.getProperty(VariableProperty.NOT_MODIFIED))
                    .min().orElse(Level.TRUE); // true when there are none
            if (allMethodCallsNotModified == Level.DELAY) {
                log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                        methodInfo.distinguishingName());
                return false;
            }
            isNotModified = allMethodCallsNotModified == Level.TRUE;
            if (isLogEnabled(NOT_MODIFIED)) {
                log(NOT_MODIFIED, "Mark method {} as {}@NotModified", methodInfo.distinguishingName(),
                        isNotModified ? "" : "NOT ");
                if (!isNotModified) {
                    List<String> offendingCalls = methodAnalysis.localMethodsCalled.get().stream()
                            .filter(mi -> mi.methodAnalysis.getProperty(VariableProperty.NOT_MODIFIED) == Level.FALSE)
                            .map(MethodInfo::distinguishingName)
                            .collect(Collectors.toList());
                    log(NOT_MODIFIED, "Offending method calls are to: {}", offendingCalls);
                }
            }
        } // else: already false, why should we analyse this?
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(VariableProperty.NOT_MODIFIED, isNotModified);
        return true;
    }

    private boolean methodIsIndependent(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.getProperty(VariableProperty.INDEPENDENT) != Level.DELAY) return false;
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not computed", methodInfo.fullyQualifiedName());
            return false;
        }

        // PART 1: check the return object, if it is there

        boolean returnObjectIsIndependent;
        if (!methodInfo.isConstructor && !methodInfo.returnType().isVoid()) {
            if (!methodAnalysis.variablesLinkedToMethodResult.isSet()) {
                log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                        methodInfo.fullyQualifiedName());
                return false;
            }
            Set<Variable> variables = methodAnalysis.variablesLinkedToMethodResult.get();
            int e2ImmutableStatusOfFieldRefs = variables.stream()
                    .filter(v -> v instanceof FieldReference)
                    .mapToInt(v -> Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE))
                    .min().orElse(Level.TRUE);
            if (e2ImmutableStatusOfFieldRefs == Level.DELAY) {
                log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                        variables.stream()
                                .filter(v -> v instanceof FieldReference &&
                                        Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE),
                                                Level.E2IMMUTABLE) == Level.DELAY)
                                .map(Variable::detailedString)
                                .collect(Collectors.joining(", ")));
                return false;
            }
            returnObjectIsIndependent = e2ImmutableStatusOfFieldRefs == Level.TRUE;
        } else {
            returnObjectIsIndependent = true;
        }

        // PART 2: check parameters

        boolean parametersIndependentOfFields = methodAnalysis.fieldsLinkedToFieldsAndVariables.stream()
                .allMatch(e -> Collections.disjoint(e.getValue(), methodInfo.methodInspection.get().parameters));

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }
}
