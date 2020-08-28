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
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckOnly;
import org.e2immu.analyser.analyser.check.CheckPrecondition;
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.analyser.methodanalysercomponent.CreateNumberedStatements;
import org.e2immu.analyser.analyser.methodanalysercomponent.StaticModifier;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.pattern.JoinReturnStatements;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final ParameterAnalyser parameterAnalyser;
    private final ComputeLinking computeLinking = new ComputeLinking();
    private final Messages messages = new Messages();

    public MethodAnalyser(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.parameterAnalyser = new ParameterAnalyser(e2ImmuAnnotationExpressions);
    }

    public void check(MethodInfo methodInfo) {
        // before we check, we copy the properties into annotations
        methodInfo.methodAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(methodInfo, Independent.class, e2ImmuAnnotationExpressions.independent.get());

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(methodInfo, NotNull.class, e2ImmuAnnotationExpressions.notNull.get());
                check(methodInfo, Fluent.class, e2ImmuAnnotationExpressions.fluent.get());
                check(methodInfo, Identity.class, e2ImmuAnnotationExpressions.identity.get());
                check(methodInfo, E1Immutable.class, e2ImmuAnnotationExpressions.e1Immutable.get());
                check(methodInfo, E1Container.class, e2ImmuAnnotationExpressions.e1Container.get());
                check(methodInfo, Container.class, e2ImmuAnnotationExpressions.container.get());
                check(methodInfo, E2Immutable.class, e2ImmuAnnotationExpressions.e2Immutable.get());
                check(methodInfo, E2Container.class, e2ImmuAnnotationExpressions.e2Container.get());
                check(methodInfo, BeforeMark.class, e2ImmuAnnotationExpressions.beforeMark.get());
                CheckConstant.checkConstantForMethods(messages, methodInfo);

                // checks for dynamic properties of functional interface types
                check(methodInfo, NotModified1.class, e2ImmuAnnotationExpressions.notModified1.get());
                check(methodInfo, Exposed.class, e2ImmuAnnotationExpressions.exposed.get());
            }
            check(methodInfo, NotModified.class, e2ImmuAnnotationExpressions.notModified.get());

            // opposites
            check(methodInfo, Nullable.class, e2ImmuAnnotationExpressions.nullable.get());
            check(methodInfo, Modified.class, e2ImmuAnnotationExpressions.modified.get());
        }
        // opposites
        check(methodInfo, Dependent.class, e2ImmuAnnotationExpressions.dependent.get());

        CheckSize.checkSizeForMethods(messages, methodInfo);
        CheckPrecondition.checkPrecondition(messages, methodInfo);
        CheckOnly.checkOnly(messages, methodInfo);
        CheckOnly.checkMark(messages, methodInfo);

        methodInfo.methodInspection.get().parameters.forEach(parameterAnalyser::check);
    }

    private void check(MethodInfo methodInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    public boolean analyse(MethodInfo methodInfo, VariableProperties methodProperties) {
        List<Statement> statements = methodInfo.methodInspection.get().methodBody.get().statements;
        if (!statements.isEmpty()) {
            boolean changes = false;
            log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());

            if (!methodInfo.methodAnalysis.get().numberedStatements.isSet()) {
                List<NumberedStatement> numberedStatements = new LinkedList<>();
                Stack<Integer> indices = new Stack<>();
                CreateNumberedStatements.recursivelyCreateNumberedStatements(null, statements, indices, numberedStatements,
                        new SideEffectContext(methodInfo));
                methodInfo.methodAnalysis.get().numberedStatements.set(ImmutableList.copyOf(numberedStatements));
                changes = true;
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                methodProperties.createLocalVariableOrParameter(parameterInfo);
            }
            if (analyseMethod(methodInfo, methodProperties)) changes = true;
            return changes;
        }
        return false;
    }

    private boolean analyseMethod(MethodInfo methodInfo, VariableProperties methodProperties) {
        try {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeLinking.computeVariablePropertiesOfMethod(numberedStatements, messages, methodInfo, methodProperties))
                changes = true;

            if (obtainMostCompletePrecondition(numberedStatements, methodInfo, methodAnalysis, methodProperties)) {
                changes = true;
            }

            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            if (makeInternalObjectFlowsPermanent(methodInfo, methodAnalysis, methodProperties)) changes = true;
            if (!methodInfo.isConstructor) {
                if (!methodAnalysis.returnStatementSummaries.isEmpty()) {
                    // internal check
                    if (methodInfo.isVoid()) throw new UnsupportedOperationException();
                    if (propertiesOfReturnStatements(methodInfo, methodAnalysis))
                        changes = true;
                    // methodIsConstant makes use of methodIsNotNull, so order is important
                    if (methodIsConstant(methodInfo, methodAnalysis, methodProperties)) changes = true;
                }
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements, methodInfo, methodAnalysis)) changes = true;
                }
                StaticModifier.detectMissingStaticModifier(messages, methodInfo, methodAnalysis);
                if (methodIsNotModified(methodInfo, methodAnalysis)) changes = true;

                // @Only, @Mark comes after modifications
                if (computeOnlyMarkPrepWork(methodInfo, methodAnalysis)) changes = true;
                if (computeOnlyMarkAnnotate(methodInfo, methodAnalysis)) changes = true;
            }
            if (methodIsIndependent(methodInfo, methodAnalysis)) changes = true;

            // size comes after modifications
            if (computeSize(methodInfo, methodAnalysis)) changes = true;
            if (computeSizeCopy(methodInfo, methodAnalysis)) changes = true;

            if (parameterAnalyser.analyse(methodProperties)) changes = true;
            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    private boolean obtainMostCompletePrecondition(List<NumberedStatement> numberedStatements,
                                                   MethodInfo methodInfo,
                                                   MethodAnalysis methodAnalysis,
                                                   VariableProperties methodProperties) {
        if (methodAnalysis.precondition.isSet()) return false; // already done
        if (methodProperties.delayedState()) {
            log(DELAYED, "Delaying preconditions, not all escapes set", methodInfo.distinguishingName());
            return false;
        }

        // TODO: need a guarantee that the precondition will be executed
        // the two ways of collecting them do NOT ensure this at the moment

        Stream<Value> preconditionsFromStatements = numberedStatements.stream()
                .filter(numberedStatement -> numberedStatement.precondition.isSet())
                .map(numberedStatement -> numberedStatement.precondition.get());
        Stream<Value> preconditionsFromMethods = methodProperties.streamPreconditions();
        Value[] preconditions = Stream.concat(preconditionsFromStatements, preconditionsFromMethods).toArray(Value[]::new);

        Value precondition;
        if (preconditions.length == 0) {
            precondition = UnknownValue.EMPTY;
        } else {
            precondition = new AndValue().append(preconditions);
        }
        methodAnalysis.precondition.set(precondition);
        return true;
    }

    private boolean makeInternalObjectFlowsPermanent(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodAnalysis.internalObjectFlows.isSet()) return false; // already done
        boolean noDelays = methodProperties.getInternalObjectFlows().noneMatch(ObjectFlow::isDelayed);
        if (noDelays) {
            Set<ObjectFlow> internalObjectFlowsWithoutParametersAndLiterals = ImmutableSet.copyOf(methodProperties.getInternalObjectFlows()
                    .filter(of -> of.origin != Origin.PARAMETER && of.origin != Origin.LITERAL)
                    .collect(Collectors.toSet()));

            internalObjectFlowsWithoutParametersAndLiterals.forEach(of -> of.finalize(null));
            methodAnalysis.internalObjectFlows.set(internalObjectFlowsWithoutParametersAndLiterals);

            methodProperties.getInternalObjectFlows().filter(of -> of.origin == Origin.PARAMETER).forEach(of -> {
                ParameterAnalysis parameterAnalysis = ((ParameterInfo) of.location.info).parameterAnalysis.get();
                of.finalize(parameterAnalysis.objectFlow.getFirst());
                parameterAnalysis.objectFlow.set(of);
            });

            TypeAnalysis typeAnalysis = methodInfo.typeInfo.typeAnalysis.get();
            methodProperties.getInternalObjectFlows().filter(of -> of.origin == Origin.LITERAL).forEach(of -> {
                ObjectFlow inType = typeAnalysis.ensureConstantObjectFlow(of);
                of.moveAllInto(inType);
            });

            log(OBJECT_FLOW, "Made permanent {} internal object flows in {}", internalObjectFlowsWithoutParametersAndLiterals.size(), methodInfo.distinguishingName());
            return true;
        }
        log(DELAYED, "Not yet setting internal object flows on {}, delaying", methodInfo.distinguishingName());
        return false;
    }

    private boolean computeOnlyMarkAnnotate(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.markAndOnly.isSet()) return false; // done
        if (!methodAnalysis.preconditionForMarkAndOnly.isSet()) return false;
        Value precondition = methodAnalysis.preconditionForMarkAndOnly.get();
        if (precondition == UnknownValue.NO_VALUE) return false;
        SetOnceMap<String, Value> approvedPreconditions = methodInfo.typeInfo.typeAnalysis.get().approvedPreconditions;
        if (approvedPreconditions.isEmpty()) {
            log(DELAYED, "No approved preconditions (yet) for {}", methodInfo.distinguishingName());
            return false;
        }
        String markLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(precondition);
        Value before = approvedPreconditions.get(markLabel);
        boolean after;
        if (before.equals(precondition)) {
            after = false;
        } else {
            Value negated = NegatedValue.negate(precondition);
            if (before.equals(negated)) {
                after = true;
            } else {
                log(MARK, "No approved preconditions for {} in {}", precondition, methodInfo.distinguishingName());
                if (!methodAnalysis.annotations.isSet(e2ImmuAnnotationExpressions.mark.get())) {
                    methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.mark.get(), false);
                }
                if (!methodAnalysis.annotations.isSet(e2ImmuAnnotationExpressions.only.get())) {
                    methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.only.get(), false);
                }
                return false;
            }
        }
        boolean mark;
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            log(MARK, "Method {} is @NotModified, so it'll be @Only rather than @Mark", methodInfo.distinguishingName());
            mark = false;
        } else {
            // for the before methods, we need to check again if we were mark or only
            mark = !after && TypeAnalyser.assignmentIncompatibleWithPrecondition(precondition, methodInfo);
        }
        MethodAnalysis.MarkAndOnly markAndOnly = new MethodAnalysis.MarkAndOnly(precondition, markLabel, mark, after);
        methodAnalysis.markAndOnly.set(markAndOnly);
        log(MARK, "Marking {} with only data {}", methodInfo.distinguishingName(), markAndOnly);
        if (mark) {
            AnnotationExpression markAnnotation = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.mark.get().typeInfo,
                    List.of(new MemberValuePair("value", new StringConstant(markLabel))));
            methodAnalysis.annotations.put(markAnnotation, true);
            methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.only.get(), false);
        } else {
            AnnotationExpression onlyAnnotation = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.only.get().typeInfo,
                    List.of(new MemberValuePair(after ? "after" : "before", new StringConstant(markLabel))));
            methodAnalysis.annotations.put(onlyAnnotation, true);
            methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.mark.get(), false);
        }
        return true;
    }

    private boolean computeOnlyMarkPrepWork(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.preconditionForMarkAndOnly.isSet()) return false; // already done
        TypeInfo typeInfo = methodInfo.typeInfo;
        while (true) {
            boolean haveNonFinalFields = typeInfo.typeInspection.get().fields.stream().anyMatch(field ->
                    field.fieldAnalysis.get().getProperty(VariableProperty.FINAL) == Level.FALSE);
            if (haveNonFinalFields) {
                break;
            }
            ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass;
            typeInfo = parentClass.bestTypeInfo();
            if (typeInfo == null) {
                log(DELAYED, "Delaying/Ignoring @Only and @Mark, cannot find a non-final field in {}", methodInfo.distinguishingName());
                return false;
            }
        }

        if (!methodAnalysis.precondition.isSet()) {
            log(DELAYED, "Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            return false;
        }
        Value precondition = methodAnalysis.precondition.get();
        if (precondition == UnknownValue.EMPTY) {
            log(MARK, "No @Mark @Only annotation in {}, as no precondition", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(UnknownValue.NO_VALUE);
            return true;
        }
        // at this point, the null and size checks on parameters have been removed.
        // we still need to remove other parameter components; what remains can be used for marking/only

        Value.FilterResult filterResult = precondition.filter(Value.FilterMode.ACCEPT, Value::isIndividualFieldCondition);
        if (filterResult.accepted.isEmpty()) {
            log(MARK, "No @Mark/@Only annotation in {}: found no individual field preconditions", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(UnknownValue.NO_VALUE);
            return true;
        }
        Value preconditionPart = new AndValue().append(filterResult.accepted.values().toArray(Value[]::new));
        log(MARK, "Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition, filterResult.accepted.keySet(), methodInfo.distinguishingName());
        methodAnalysis.preconditionForMarkAndOnly.set(preconditionPart);
        return true;
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
    private boolean methodIsConstant(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodAnalysis.singleReturnValue.isSet()) return false;

        boolean allReturnValuesSet = methodAnalysis.returnStatementSummaries.stream().allMatch(e -> e.getValue().value.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return false;
        }
        List<TransferValue> remainingReturnStatementSummaries;
        if (methodAnalysis.returnStatementSummaries.size() > 1) {
            remainingReturnStatementSummaries = applyJoinReturnStatementPatterns(methodInfo, methodAnalysis, methodProperties);
            if (remainingReturnStatementSummaries.isEmpty()) {
                log(DELAYED, "Not everything known for pattern analysis in {}, delaying", methodInfo.distinguishingName());
                return false;
            }
        } else {
            remainingReturnStatementSummaries = methodAnalysis.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
        }

        Value value = null;
        if (remainingReturnStatementSummaries.size() == 1) {
            Value single = remainingReturnStatementSummaries.get(0).value.get();
            if (!methodAnalysis.internalObjectFlows.isSet()) {
                log(DELAYED, "Delaying single return value because internal object flows not yet known, {}", methodInfo.distinguishingName());
                return false;
            }
            ObjectFlow objectFlow = single.getObjectFlow();
            if (objectFlow != ObjectFlow.NO_FLOW && !methodAnalysis.objectFlow.isSet()) {
                log(OBJECT_FLOW, "Set final object flow object for method {}: {}", methodInfo.distinguishingName(), objectFlow);
                objectFlow.finalize(methodAnalysis.objectFlow.getFirst());
                methodAnalysis.objectFlow.set(objectFlow);
            }
            if (single.isConstant()) {
                value = single;
            } else if (methodInfo.isStatic && single.isExpressionOfParameters()) {
                value = new InlineValue(methodInfo, single);
            }
        }
        if (!methodAnalysis.objectFlow.isSet()) {
            log(OBJECT_FLOW, "No single object flow; we keep the object that's already there, for {}", methodInfo.distinguishingName());
            methodAnalysis.objectFlow.set(methodAnalysis.objectFlow.getFirst());
        }
        // fallback
        if (value == null) {
            value = UnknownValue.RETURN_VALUE;
        }
        boolean isConstant = value.isConstant();

        methodAnalysis.singleReturnValue.set(value);
        if (isConstant) {
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(e2ImmuAnnotationExpressions, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.constant.get(), false);
        }
        methodAnalysis.setProperty(VariableProperty.CONSTANT, isConstant);

        log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
        return true;
    }

    @NotNull
    private List<TransferValue> applyJoinReturnStatementPatterns(MethodInfo methodInfo, MethodAnalysis methodAnalysis, EvaluationContext evaluationContext) {
        JoinReturnStatements joinReturnStatements = new JoinReturnStatements(evaluationContext);
        List<NumberedStatement> topLevelStatements = methodAnalysis.numberedStatements.get().stream()
                .filter(ns -> ns.indices.length == 1).collect(Collectors.toList());
        JoinReturnStatements.JoinResult result = joinReturnStatements.joinReturnStatementsInIfThenElse(topLevelStatements);
        if (result != null) {
            log(PATTERN, "Successfully applied JoinReturnStatementsInIfThenElse in {}", methodInfo.distinguishingName());
            return applyJoinResult(methodAnalysis, result);
        }
        JoinReturnStatements.JoinResult result2 = joinReturnStatements.joinReturnStatements(topLevelStatements);
        if (result2 != null) {
            log(PATTERN, "Successfully applied JoinReturnStatements in {}", methodInfo.distinguishingName());
            return applyJoinResult(methodAnalysis, result2);
        }
        return methodAnalysis.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());
    }

    private List<TransferValue> applyJoinResult(MethodAnalysis methodAnalysis, JoinReturnStatements.JoinResult result) {
        if (result.value == UnknownValue.NO_VALUE) return List.of(); // DELAY
        List<TransferValue> old = methodAnalysis.returnStatementSummaries.stream()
                .filter(e -> !result.statementIdsReduced.contains(e.getKey()))
                .map(Map.Entry::getValue).collect(Collectors.toList());
        TransferValue transferValue = new TransferValue();
        transferValue.value.set(result.value);
        // TODO: state of result.value ??
        return ListUtil.immutableConcat(old, List.of(transferValue));
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

    // IMMUTABLE, NOT_NULL, CONTAINER, IDENTITY, FLUENT
    // IMMUTABLE, NOT_NULL can still improve with respect to the static return type computed in methodAnalysis.getProperty()
    private boolean propertyOfReturnStatements(VariableProperty variableProperty,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis) {
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (currentValue != Level.DELAY && variableProperty != VariableProperty.IMMUTABLE && variableProperty != VariableProperty.NOT_NULL)
            return false;

        boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry -> entry.getValue().isDelayed(variableProperty));
        if (delays) {
            log(DELAYED, "Return statement value not yet set");
            return false;
        }
        IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                .mapToInt(entry -> entry.getValue().getProperty(variableProperty));
        int value = variableProperty == VariableProperty.SIZE ?
                safeMinimum(messages, new Location(methodInfo), stream) :
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

    private boolean computeSize(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        int current = methodAnalysis.getProperty(VariableProperty.SIZE);
        if (current != Level.DELAY) return false;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} because waiting for @Modified", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.isConstructor) return false; // non-modifying constructor would be weird anyway
            if (methodInfo.returnType().hasSize()) {
                // non-modifying method that returns a type with @Size (like Collection, Map, ...)

                // then try @Size(min, equals)
                boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry -> entry.getValue().isDelayed(VariableProperty.SIZE));
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for @Size on {}", methodInfo.distinguishingName());
                    return false;
                }
                IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().getProperty(VariableProperty.SIZE));
                return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE, safeMinimum(messages, new Location(methodInfo), stream));
            }

            // non-modifying method that defines @Size (size(), isEmpty())
            return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE, propagateSizeAnnotations(methodInfo, methodAnalysis));
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        if (methodInfo.typeInfo.hasSize()) {
            return sizeModifying(methodInfo, methodAnalysis);

        }
        return false;
    }


    private boolean computeSizeCopy(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        int current = methodAnalysis.getProperty(VariableProperty.SIZE_COPY);
        if (current != Level.DELAY) return false;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size(copy) on {} because waiting for @Modified", methodInfo.distinguishingName());
            return false;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.isConstructor) return false; // non-modifying constructor would be weird anyway
            if (methodInfo.returnType().hasSize()) {
                // first try @Size(copy ...)
                boolean delays = methodAnalysis.returnStatementSummaries.stream().anyMatch(entry -> entry.getValue().isDelayed(VariableProperty.SIZE_COPY));
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for SIZE_COPY on {}", methodInfo.distinguishingName());
                    return false;
                }
                IntStream stream = methodAnalysis.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().getProperty(VariableProperty.SIZE_COPY));
                int min = stream.min().orElse(Level.DELAY);
                return writeSize(methodInfo, methodAnalysis, VariableProperty.SIZE_COPY, min);
            }
            return false;
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        if (methodInfo.typeInfo.hasSize()) {
            // TODO not implemented yet
        }
        return false;
    }

    // there is a modification that alters the @Size of this type (e.g. put() will cause a @Size(min = 1))
    private boolean sizeModifying(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        return false; // TODO NYI
    }

    private boolean writeSize(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperty variableProperty, int value) {
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return true;
    }

    private int propagateSizeAnnotations(MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        if (methodAnalysis.returnStatementSummaries.size() != 1) {
            return Level.DELAY;
        }
        TransferValue tv = methodAnalysis.returnStatementSummaries.stream().findFirst().orElseThrow().getValue();
        if (!tv.value.isSet()) {
            return Level.DELAY;
        }
        Value value = tv.value.get();
        ConstrainedNumericValue cnv;
        if (methodInfo.returnType().isDiscrete() && ((cnv = value.asInstanceOf(ConstrainedNumericValue.class)) != null)) {
            // very specific situation, we see if the return statement is a @Size method; if so, we propagate that info
            MethodValue methodValue;
            if ((methodValue = cnv.value.asInstanceOf(MethodValue.class)) != null) {
                MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                if (methodValue.methodInfo == theSizeMethod && cnv.lowerBound >= 0 && cnv.upperBound == ConstrainedNumericValue.MAX) {
                    return Level.encodeSizeMin((int) cnv.lowerBound);
                }
            }
        } else if (methodInfo.returnType().isBoolean()) {
            // very specific situation, we see if the return statement is a predicate on a @Size method; if so we propagate that info
            // size restrictions are ALWAYS int == size() or -int + size() >= 0
            EqualsValue equalsValue;
            if ((equalsValue = value.asInstanceOf(EqualsValue.class)) != null) {
                IntValue intValue;
                ConstrainedNumericValue cnvRhs;
                if ((intValue = equalsValue.lhs.asInstanceOf(IntValue.class)) != null &&
                        (cnvRhs = equalsValue.rhs.asInstanceOf(ConstrainedNumericValue.class)) != null) {
                    MethodValue methodValue;
                    if ((methodValue = cnvRhs.value.asInstanceOf(MethodValue.class)) != null) {
                        MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                        if (methodValue.methodInfo == theSizeMethod) {
                            return Level.encodeSizeEquals(intValue.value);
                        }
                    }
                }
            } else {
                GreaterThanZeroValue gzv;
                if ((gzv = value.asInstanceOf(GreaterThanZeroValue.class)) != null) {
                    GreaterThanZeroValue.XB xb = gzv.extract();
                    ConstrainedNumericValue cnvXb;
                    if (!xb.lessThan && ((cnvXb = xb.x.asInstanceOf(ConstrainedNumericValue.class)) != null)) {
                        MethodValue methodValue;
                        if ((methodValue = cnvXb.value.asInstanceOf(MethodValue.class)) != null) {
                            MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod();
                            if (methodValue.methodInfo == theSizeMethod) {
                                return Level.encodeSizeMin((int) xb.b);
                            }
                        }
                    }
                }
            }
        }
        return Level.DELAY;
    }

    static int safeMinimum(Messages messages, Location location, IntStream intStream) {
        int res = intStream.reduce(Integer.MAX_VALUE, (v1, v2) -> {
            if (Level.haveEquals(v1) && Level.haveEquals(v2) && v1 != v2) {
                messages.add(Message.newMessage(location, Message.POTENTIAL_SIZE_PROBLEM,
                        "Equal to " + Level.decodeSizeEquals(v1) + ", equal to " + Level.decodeSizeEquals(v2)));
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
                .anyMatch(tv -> tv.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} cannot be @NotModified/is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return true;
        }

        // second step, check that linking has been computed
        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @Modified yet, delaying because linking not computed",
                    methodInfo.distinguishingName());
            return false;
        }
        boolean isModified = methodAnalysis.fieldSummaries.stream().map(Map.Entry::getValue)
                .anyMatch(tv -> tv.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (isModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.fieldSummaries.stream()
                            .filter(e -> e.getValue().getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                            .map(e -> e.getKey().fullyQualifiedName()).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified) {
            boolean localMethodsCalled = methodAnalysis.thisSummary.get().getProperty(VariableProperty.METHOD_CALLED) == Level.TRUE;
            if (localMethodsCalled) {
                int thisModified = methodAnalysis.thisSummary.get().getProperty(VariableProperty.MODIFIED);
                int methodDelay = methodAnalysis.thisSummary.get().properties.getOtherwise(VariableProperty.METHOD_DELAY, Level.FALSE);

                if (thisModified == Level.DELAY && methodDelay == Level.TRUE) {
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
        if (methodAnalysis.getProperty(VariableProperty.INDEPENDENT) != Level.DELAY) {
            return false; // already computed
        }
        if (!methodInfo.isConstructor) {
            // we only compute @Independent/@Dependent on methods when the method is @NotModified
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return false; // wait
            if (modified == Level.TRUE) {
                methodAnalysis.setProperty(VariableProperty.INDEPENDENT, Level.FALSE);
                return true;
            }
        } // else: for constructors, we assume @Modified so that rule is not that useful

        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not yet computed", methodInfo.fullyQualifiedName());
            return false;
        }

        // PART 1: check the return object, if it is there

        // support data types are not set for types that have not been defined; there, we rely on annotations
        boolean returnObjectIsIndependent;
        if (!methodInfo.isConstructor && !methodInfo.isVoid() &&
                methodInfo.typeInfo.typeAnalysis.get().supportDataTypes.isSet() &&
                methodInfo.typeInfo.typeAnalysis.get().supportDataTypes.get().contains(methodInfo.returnType())) {
            // method returns a support data type
            if (!methodAnalysis.variablesLinkedToMethodResult.isSet()) {
                log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                        methodInfo.fullyQualifiedName());
                return false;
            }
            Set<Variable> variables = methodAnalysis.variablesLinkedToMethodResult.get();
            boolean supportDataSet = variables.stream().allMatch(MethodAnalyser::isSupportDataFieldSet);
            if (!supportDataSet) {
                log(DELAYED, "Delaying @Independent on {}, support data not known for all field references", methodInfo.distinguishingName());
                return false;
            }
            int e2ImmutableStatusOfFieldRefs = variables.stream()
                    .filter(MethodAnalyser::isSupportDataField)
                    .mapToInt(v -> MultiLevel.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE))
                    .min().orElse(MultiLevel.EFFECTIVE);
            if (e2ImmutableStatusOfFieldRefs == MultiLevel.DELAY) {
                log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                        variables.stream()
                                .filter(v -> isSupportDataField(v) &&
                                        MultiLevel.value(((FieldReference) v).fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE),
                                                MultiLevel.E2IMMUTABLE) == MultiLevel.DELAY)
                                .map(Variable::detailedString)
                                .collect(Collectors.joining(", ")));
                return false;
            }
            returnObjectIsIndependent = e2ImmutableStatusOfFieldRefs == MultiLevel.EFFECTIVE;
        } else {
            // method does not return a support data type.
            returnObjectIsIndependent = true;
        }

        // PART 2: check parameters, but remove those that are recursively of my own type
        List<ParameterInfo> parameters = new ArrayList<>(methodInfo.methodInspection.get().parameters);
        parameters.removeIf(pi -> pi.parameterizedType.typeInfo == methodInfo.typeInfo);

        boolean supportDataSet = methodAnalysis.fieldSummaries.stream()
                .flatMap(e -> e.getValue().linkedVariables.get().stream())
                .allMatch(MethodAnalyser::isSupportDataFieldSet);
        if (!supportDataSet) {
            log(DELAYED, "Delaying @Independent on {}, support data not known for all field references", methodInfo.distinguishingName());
            return false;
        }

        boolean parametersIndependentOfFields = methodAnalysis.fieldSummaries.stream()
                .peek(e -> {
                    if (!e.getValue().linkedVariables.isSet())
                        LOGGER.warn("Field {} has no linked variables set in {}", e.getKey().name, methodInfo.distinguishingName());
                })
                .flatMap(e -> e.getValue().linkedVariables.get().stream())
                .filter(v -> v instanceof ParameterInfo)
                .map(v -> (ParameterInfo) v)
                .peek(set -> log(LINKED_VARIABLES, "Remaining linked support variables of {} are {}", methodInfo.distinguishingName(), set))
                .noneMatch(parameters::contains);

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }

    public static boolean isSupportDataFieldSet(Variable v) {
        return !(v instanceof FieldReference) || ((FieldReference) v).fieldInfo.fieldAnalysis.get().supportData.isSet();
    }

    public static boolean isSupportDataField(Variable variable) {
        if (!(variable instanceof FieldReference)) return false;
        return ((FieldReference) variable).fieldInfo.fieldAnalysis.get().supportData.get();
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(messages.getMessageStream(), parameterAnalyser.getMessageStream());
    }
}
