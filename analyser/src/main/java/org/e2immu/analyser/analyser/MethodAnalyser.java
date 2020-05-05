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
import org.e2immu.analyser.model.abstractvalue.Instance;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);
    private static final Set<AnnotationExpression> INITIAL = new HashSet<>();

    private final TypeContext typeContext;
    private final ParameterAnalyser parameterAnalyser;
    private final ComputeLinking computeLinking;

    public MethodAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
        this.parameterAnalyser = new ParameterAnalyser(typeContext);
        this.computeLinking = new ComputeLinking(typeContext, parameterAnalyser);
    }

    public void check(MethodInfo methodInfo) {
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
            CreateNumberedStatements.recursivelyCreateNumberedStatements(statements, indices, numberedStatements, new SideEffectContext(typeContext, methodInfo));
            methodInfo.methodAnalysis.numberedStatements.set(ImmutableList.copyOf(numberedStatements));
            changes = true;
        }
        for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
            methodProperties.create(parameterInfo);
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

            List<NumberedStatement> returnStatements = methodAnalysis.returnStatements.get();
            if (!returnStatements.isEmpty()) {
                if (methodIsIdentity(returnStatements, methodInfo, methodAnalysis)) changes = true;
                if (methodIsFluent(returnStatements, methodInfo, methodAnalysis)) changes = true;
                if (methodIsNotNull(returnStatements, methodInfo, methodAnalysis)) changes = true;
                // methodIsConstant makes use of methodIsNotNull, so order is important
                if (methodIsConstant(returnStatements, methodInfo, methodAnalysis)) changes = true;
                if (methodHasDynamicTypeAnnotations(returnStatements, methodProperties, methodInfo, methodAnalysis))
                    changes = true;
            }

            if (!methodInfo.isConstructor) {
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
        if (!methodAnalysis.singleReturnValue.isSet()) {
            boolean allReturnValuesSet = returnStatements.stream().allMatch(ns -> ns.returnValue.isSet());
            if (!allReturnValuesSet) {
                log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
                return false;
            }
            Value value;
            if (returnStatements.size() == 1) {
                value = returnStatements.get(0).returnValue.get();
            } else {
                Boolean isNotNull = methodInfo.isNotNull(typeContext);
                if (isNotNull == null) {
                    log(DELAYED, "Have multiple return values, going to insert an MethodValue, but waiting for @NotNull on {}",
                            methodInfo.distinguishingName());
                    return false;
                }
                value = new MethodValue(methodInfo, UnknownValue.UNKNOWN_VALUE, List.of(), isNotNull);
            }
            methodAnalysis.singleReturnValue.set(value);
            boolean isConstant = value instanceof Constant;
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
            methodAnalysis.annotations.put(constantAnnotation, isConstant);
            log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private boolean methodHasDynamicTypeAnnotations(List<NumberedStatement> returnStatements,
                                                    VariableProperties methodProperties,
                                                    MethodInfo methodInfo,
                                                    MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.dynamicTypeAnnotationsAdded.isSet()) {
            boolean allReturnValuesDefined = returnStatements.stream().allMatch(ns -> ns.returnValue.isSet());
            if (!allReturnValuesDefined) {
                log(DELAYED, "Not all return values defined");
                return false;
            }
            Set<AnnotationExpression> intersection = returnStatements.stream()
                    .map(ns -> ns.returnValue.get())
                    .map(v -> v.dynamicTypeAnnotations(methodProperties))
                    .reduce(INITIAL, (prev, curr) -> {
                        if (prev == null || curr == null) return null;
                        if (prev == INITIAL) return new HashSet<>(curr);
                        prev.retainAll(curr);
                        return prev;
                    });
            if (intersection == null) {
                log(DELAYED, "Have null intersection, delaying for {}", methodInfo.distinguishingName());
                return false;
            }
            boolean wroteOne = false;
            for (AnnotationExpression ae : new AnnotationExpression[]{typeContext.e2Container.get(), typeContext.e2Immutable.get(),
                    typeContext.e1Container.get(), typeContext.e1Immutable.get(), typeContext.container.get()}) {
                boolean dynamic = intersection.contains(ae);
                boolean positive = !wroteOne && dynamic;
                if (!methodAnalysis.annotations.isSet(ae)) {
                    log(E2IMMUTABLE, "Mark method result " + methodInfo.distinguishingName() + " as " + (positive ? "" : "NOT ") + "@" + ae.typeInfo.simpleName);
                    methodAnalysis.annotations.put(ae, positive);
                    if (!wroteOne) wroteOne = true;
                }
            }
            methodAnalysis.dynamicTypeAnnotationsAdded.set(true);
            return true;
        }
        return false;
    }

    private boolean methodIsNotNull(List<NumberedStatement> returnStatements, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        boolean notNull = returnStatements.stream().allMatch(ns -> ns.returnsNotNull.isSet() && ns.returnsNotNull.get() == Boolean.TRUE);
        if (notNull && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
            methodAnalysis.annotations.put(typeContext.notNull.get(), true);
            log(NOT_NULL, "Set @NotNull on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        boolean notNullFalse = returnStatements.stream().anyMatch(ns -> ns.returnsNotNull.isSet() && Boolean.FALSE == ns.returnsNotNull.get());
        if (notNullFalse && !methodAnalysis.annotations.isSet(typeContext.notNull.get())) {
            methodAnalysis.annotations.put(typeContext.notNull.get(), false);
            log(NOT_NULL, "Set NOT @NotNull on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        log(DELAYED, "Not deciding on @NotNull yet for method {}", methodInfo.distinguishingName());
        return false;
    }

    private boolean methodIsFluent(List<NumberedStatement> returnStatements,
                                   MethodInfo methodInfo,
                                   MethodAnalysis methodAnalysis) {
        boolean fluent = returnStatements.stream().allMatch(ns -> ((ReturnStatement) ns.statement).isFluent(typeContext) == Boolean.TRUE);

        if (fluent && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
            methodAnalysis.annotations.put(typeContext.fluent.get(), true);
            log(FLUENT, "Set @Fluent on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        boolean fluentFalse = returnStatements.stream().anyMatch(ns -> ((ReturnStatement) ns.statement).isFluent(typeContext) == Boolean.FALSE);
        if (fluentFalse && !methodAnalysis.annotations.isSet(typeContext.fluent.get())) {
            methodAnalysis.annotations.put(typeContext.fluent.get(), false);
            log(FLUENT, "Set NOT @Fluent on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private boolean methodIsIdentity(List<NumberedStatement> returnStatements,
                                     MethodInfo methodInfo,
                                     MethodAnalysis methodAnalysis) {
        if (methodAnalysis.annotations.isSet(typeContext.identity.get())) return false;
        boolean identity = returnStatements.stream().allMatch(ns ->
                ReturnStatement.isIdentity(typeContext, ((ReturnStatement) ns.statement).expression) == Boolean.TRUE);
        if (identity) {
            methodAnalysis.annotations.put(typeContext.identity.get(), true);
            log(IDENTITY, "Set @Identity on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        boolean identityFalse = returnStatements.stream().anyMatch(ns ->
                ReturnStatement.isIdentity(typeContext, ((ReturnStatement) ns.statement).expression) == Boolean.FALSE);
        if (identityFalse) {
            methodAnalysis.annotations.put(typeContext.identity.get(), false);
            log(IDENTITY, "Set NOT @Identity on method {}", methodInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (!methodAnalysis.annotations.isSet(typeContext.notModified.get())) {
            // first step, check that no fields are being assigned
            boolean fieldAssignments = methodAnalysis.fieldAssignments.stream().anyMatch(Map.Entry::getValue);
            if (fieldAssignments) {
                log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields are being assigned", methodInfo.distinguishingName());
                methodAnalysis.annotations.put(typeContext.notModified.get(), false);
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
                Boolean allMethodCallsNotModified = methodAnalysis.localMethodsCalled.get().stream()
                        .map(mi -> mi.isNotModified(typeContext))
                        .reduce(true, TypeAnalyser.TERNARY_AND);
                if (allMethodCallsNotModified == null) {
                    log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return false;
                }
                isNotModified = allMethodCallsNotModified;
                if (isLogEnabled(NOT_MODIFIED)) {
                    log(NOT_MODIFIED, "Mark method {} as {}@NotModified", methodInfo.distinguishingName(),
                            isNotModified ? "" : "NOT ");
                    if (!isNotModified) {
                        List<String> offendingCalls = methodAnalysis.localMethodsCalled.get().stream()
                                .filter(mi -> !mi.isNotModified(typeContext))
                                .map(MethodInfo::distinguishingName)
                                .collect(Collectors.toList());
                        log(NOT_MODIFIED, "Offending method calls are to: {}", offendingCalls);
                    }
                }
            } // else: already false, why should we analyse this?
            // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
            methodAnalysis.annotations.put(typeContext.notModified.get(), isNotModified);
            return true;
        }
        return false;
    }

    private boolean methodIsIndependent(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.annotations.isSet(typeContext.independent.get())) return false;
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
            boolean e2ImmutableStatusOfFieldRefsIsKnown = variables.stream()
                    .allMatch(v -> !(v instanceof FieldReference) || ((FieldReference) v).fieldInfo.isE2Immutable(typeContext) != null);
            if (!e2ImmutableStatusOfFieldRefsIsKnown) {
                log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known");
                return false;
            }
            returnObjectIsIndependent = variables.stream().allMatch(v -> !(v instanceof FieldReference) ||
                    ((FieldReference) v).fieldInfo.isE2Immutable(typeContext));
        } else {
            returnObjectIsIndependent = true;
        }

        // PART 2: check parameters

        boolean parametersIndependentOfFields = methodAnalysis.fieldsLinkedToFieldsAndVariables.stream()
                .allMatch(e -> Collections.disjoint(e.getValue(), methodInfo.methodInspection.get().parameters));

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.annotations.put(typeContext.independent.get(), independent);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }

    // helper
    private static Boolean onReturnStatement(NumberedStatement ns, Predicate<Expression> predicate) {
        if (ns.statement instanceof ReturnStatement) {
            ReturnStatement ret = (ReturnStatement) ns.statement;
            return predicate.test(ret.expression);
        }
        return false;
    }
}
