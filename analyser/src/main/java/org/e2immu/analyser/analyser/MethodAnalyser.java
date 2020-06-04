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
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.analyser.methodanalysercomponent.StaticModifier;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.MethodValue;
import org.e2immu.analyser.model.abstractvalue.TypeValue;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        this.computeLinking = new ComputeLinking(typeContext);
    }

    public void check(MethodInfo methodInfo) {
        // before we check, we copy the properties into annotations
        methodInfo.methodAnalysis.get().transferPropertiesToAnnotations(typeContext);

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, typeContext.independent.get());
        check(methodInfo, NotModified.class, typeContext.notModified.get());

        if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
            check(methodInfo, NotNull.class, typeContext.notNull.get());
            check(methodInfo, Fluent.class, typeContext.fluent.get());
            check(methodInfo, Identity.class, typeContext.identity.get());

            check(methodInfo, E1Immutable.class, typeContext.e1Immutable.get());
            check(methodInfo, E1Container.class, typeContext.e1Container.get());
            check(methodInfo, Container.class, typeContext.container.get());
            check(methodInfo, E2Immutable.class, typeContext.e2Immutable.get());
            check(methodInfo, E2Container.class, typeContext.e2Container.get());

            CheckConstant.checkConstantForMethods(typeContext, methodInfo);
            CheckSize.checkSizeForMethods(typeContext, methodInfo);

            // opposites
            check(methodInfo, Dependent.class, typeContext.dependent.get());
            check(methodInfo, Modified.class, typeContext.modified.get());
            check(methodInfo, Nullable.class, typeContext.nullable.get());
        }

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            typeContext.addMessage(error);
        });
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

        if (!methodInfo.methodAnalysis.get().numberedStatements.isSet()) {
            List<NumberedStatement> numberedStatements = new LinkedList<>();
            Stack<Integer> indices = new Stack<>();
            CreateNumberedStatements.recursivelyCreateNumberedStatements(statements, indices, numberedStatements,
                    new SideEffectContext(methodInfo));
            methodInfo.methodAnalysis.get().numberedStatements.set(ImmutableList.copyOf(numberedStatements));
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
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeLinking.computeVariablePropertiesOfMethod(numberedStatements, methodInfo, methodProperties))
                changes = true;
            if (methodIsIndependent(methodInfo, methodAnalysis)) changes = true;
            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            if (!methodInfo.isConstructor) {
                if (!methodAnalysis.returnStatementSummaries.isEmpty()) {
                    if (propertiesOfReturnStatements(methodInfo, methodAnalysis))
                        changes = true;
                    // methodIsConstant makes use of methodIsNotNull, so order is important
                    if (methodIsConstant(methodInfo, methodAnalysis)) changes = true;
                }
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements, methodInfo, methodAnalysis)) changes = true;
                }
                StaticModifier.detectMissingStaticModifier(typeContext, methodInfo, methodAnalysis);
                if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;
            }
            if (parameterAnalyser.analyse(methodProperties)) changes = true;
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
    private boolean methodIsConstant(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.singleReturnValue.isSet()) return false;

        boolean allReturnValuesSet = methodAnalysis.returnStatementSummaries.stream().allMatch(e -> e.getValue().value.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return false;
        }
        Value value = null;
        if (methodAnalysis.returnStatementSummaries.size() == 1) {
            Value single = methodAnalysis.returnStatementSummaries.stream().findFirst().orElseThrow().getValue().value.get();
            if (single.isConstant()) {
                value = single;
            } else {
                value = checkInlineFunction(single);
            }
        }
        // fallback
        if (value == null) {
            // the object (2nd parameter) is not important here; it will not be used to compute properties
            value = new MethodValue(methodInfo, new TypeValue(methodInfo.typeInfo.asParameterizedType()), List.of());
        }
        boolean isConstant = value.isConstant();
        methodAnalysis.singleReturnValue.set(value);
        AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
        methodAnalysis.annotations.put(constantAnnotation, isConstant);
        methodAnalysis.setProperty(VariableProperty.CONSTANT, Level.TRUE);
        log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
        return true;
    }

    // returns null when we do not recognize an inline function

    private Value checkInlineFunction(Value single) {
        return null; // TODO
    }

    private boolean propertiesOfReturnStatements(MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis) {
        boolean changes = false;
        for (VariableProperty variableProperty : VariableProperty.RETURN_VALUE_PROPERTIES_IN_METHOD_ANALYSER) {
            if (propertyOfReturnStatements(variableProperty, methodInfo, methodAnalysis))
                changes = true;
        }
        return changes;
    }

    private boolean propertyOfReturnStatements(VariableProperty variableProperty,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis) {
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (currentValue != Level.DELAY) return false;

        boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry ->
                entry.getValue().properties.getOtherwise(variableProperty, Level.DELAY) == Level.DELAY);
        if (delays) {
            log(DELAYED, "Return statement value not yet set");
            return false;
        }
        IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                .mapToInt(entry -> entry.getValue().properties.getOtherwise(variableProperty, Level.DELAY));
        int value = variableProperty == VariableProperty.SIZE ?
                safeMinimum(typeContext, new Location(methodInfo), stream) :
                stream.min().orElse(Level.DELAY);
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return true;
    }

    static int safeMinimum(TypeContext typeContext, Location location, IntStream intStream) {
        int res = intStream.reduce(Integer.MAX_VALUE, (v1, v2) -> {
            if (Analysis.haveEquals(v1) && Analysis.haveEquals(v2) && v1 != v2) {
                typeContext.addMessage(Message.newMessage(location, Message.POTENTIAL_SIZE_PROBLEM,
                        "Equal to " + Analysis.decodeSizeEquals(v1) + ", equal to " + Analysis.decodeSizeEquals(v2)));
            }
            return Math.min(v1, v2);
        });
        return res == Integer.MAX_VALUE ? Level.DELAY : res;
    }

    private boolean methodIsNotModified(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.getProperty(VariableProperty.MODIFIED) != Level.DELAY) return false;

        // first step, check field assignments
        boolean fieldAssignments = methodAnalysis.fieldSummaries.stream()
                .map(Map.Entry::getValue)
                .anyMatch(tv -> tv.properties.getOtherwise(VariableProperty.ASSIGNED, Level.DELAY) >= Level.TRUE);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} cannot be @NotModified/is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return true;
        }
        // second step, check that fields modifications
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @Modified yet, delaying because linking not computed",
                    methodInfo.distinguishingName());
            return false;
        }
        boolean isModified = methodAnalysis.fieldSummaries.stream().map(Map.Entry::getValue)
                .anyMatch(tv -> tv.properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) == Level.TRUE);
        if (isModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.fieldSummaries.stream()
                            .filter(e -> e.getValue().properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) == Level.TRUE)
                            .map(e -> e.getKey().fullyQualifiedName()).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified) {
            boolean localMethodsCalled = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.METHOD_CALLED, Level.DELAY) == Level.TRUE;
            if (localMethodsCalled) {
                int thisModified = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY);

                if (thisModified == Level.DELAY) {
                    log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return false;
                }
                isModified = thisModified == Level.TRUE;
                log(NOT_MODIFIED, "Mark method {} as {}", methodInfo.distinguishingName(),
                        isModified ? "@Modified" : "@NotModified");
            }
        } // else: already true, so no need to look at this
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(VariableProperty.MODIFIED, isModified);
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
                    .mapToInt(v -> Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE))
                    .min().orElse(Level.TRUE);
            if (e2ImmutableStatusOfFieldRefs == Level.DELAY) {
                log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                        variables.stream()
                                .filter(v -> v instanceof FieldReference &&
                                        Level.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
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
        List<ParameterInfo> parameters = methodInfo.methodInspection.get().parameters;
        boolean parametersIndependentOfFields = methodAnalysis.fieldSummaries.stream()
                .peek(e -> {
                    if (!e.getValue().linkedVariables.isSet())
                        LOGGER.warn("Field {} has no linked variables set in {}", e.getKey().name, methodInfo.distinguishingName());
                })
                .map(e -> e.getValue().linkedVariables.get())
                .allMatch(set -> Collections.disjoint(set, parameters));

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }
}
