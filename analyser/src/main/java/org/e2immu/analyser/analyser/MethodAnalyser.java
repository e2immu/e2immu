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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckOnly;
import org.e2immu.analyser.analyser.check.CheckPrecondition;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser extends AbstractAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);
    public static final String STATEMENT_ANALYSER = "StatementAnalyser";

    public final MethodInfo methodInfo;
    public final MethodInspection methodInspection;
    public final boolean isSAM;
    public final MethodAnalysisImpl.Builder methodAnalysis;

    private final TypeAnalysis typeAnalysis;
    public final List<ParameterAnalyser> parameterAnalysers;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final Map<CompanionMethodName, CompanionAnalyser> companionAnalysers;
    public final Map<CompanionMethodName, CompanionAnalysis> companionAnalyses;

    public final StatementAnalyser firstStatementAnalyser;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final CheckConstant checkConstant;

    private Map<FieldInfo, FieldAnalyser> myFieldAnalysers;

    /*
    Note that MethodLevelData is not part of the shared state, as the "lastStatement", where it resides,
    is only computed in the first step of the analyser components.
     */
    private record SharedState(EvaluationContext evaluationContext) {
    }

    public MethodAnalyser(MethodInfo methodInfo,
                          TypeAnalysis typeAnalysis,
                          boolean isSAM,
                          AnalyserContext analyserContext) {
        super("Method " + methodInfo.name, analyserContext);
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());
        this.methodInfo = methodInfo;
        methodInspection = methodInfo.methodInspection.get();

        ImmutableMap.Builder<CompanionMethodName, CompanionAnalyser> companionAnalysersBuilder = new ImmutableMap.Builder<>();
        for (Map.Entry<CompanionMethodName, MethodInfo> entry : methodInspection.getCompanionMethods().entrySet()) {
            companionAnalysersBuilder.put(entry.getKey(),
                    new CompanionAnalyser(analyserContext, typeAnalysis, entry.getKey(), entry.getValue(), methodInfo, AnnotationParameters.DEFAULT));
        }
        companionAnalysers = companionAnalysersBuilder.build();
        companionAnalyses = companionAnalysers.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> e.getValue().companionAnalysis));

        ImmutableList.Builder<ParameterAnalyser> parameterAnalysersBuilder = new ImmutableList.Builder<>();
        for (ParameterInfo parameterInfo : methodInspection.getParameters()) {
            parameterAnalysersBuilder.add(new ParameterAnalyser(analyserContext, parameterInfo));
        }
        parameterAnalysers = parameterAnalysersBuilder.build();
        parameterAnalyses = parameterAnalysers.stream().map(pa -> pa.parameterAnalysis).collect(Collectors.toUnmodifiableList());

        this.typeAnalysis = typeAnalysis;
        Block block = methodInspection.getMethodBody();
        methodAnalysis = new MethodAnalysisImpl.Builder(true, analyserContext.getPrimitives(), analyserContext,
                methodInfo, parameterAnalyses);

        if (block == Block.EMPTY_BLOCK) {
            firstStatementAnalyser = null;
        } else {
            firstStatementAnalyser = StatementAnalyser.recursivelyCreateAnalysisObjects(analyserContext,
                    this, null, block.structure.statements(), "", true,
                    methodInfo.isSynchronized() || methodInfo.isConstructor ||
                            methodInfo.methodResolution.get().partOfConstruction() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION);
            methodAnalysis.setFirstStatement(firstStatementAnalyser.statementAnalysis);
        }
        this.isSAM = isSAM;

        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<>();
        if (firstStatementAnalyser != null) {

            for (CompanionAnalyser companionAnalyser : companionAnalysers.values()) {
                builder.add(companionAnalyser.companionMethodName.toString(), (sharedState ->
                        companionAnalyser.analyse(sharedState.evaluationContext.getIteration())));
            }
            AnalysisStatus.AnalysisResultSupplier<SharedState> statementAnalyser = (sharedState) -> {
                StatementAnalyserResult result = firstStatementAnalyser.analyseAllStatementsInBlock(sharedState.evaluationContext.getIteration(),
                        ForwardAnalysisInfo.startOfMethod(analyserContext.getPrimitives()),
                        sharedState.evaluationContext.getClosure());
                // apply all modifications
                result.getModifications().forEach(Runnable::run);
                this.messages.addAll(result.messages);
                return result.analysisStatus;
            };

            builder.add(STATEMENT_ANALYSER, statementAnalyser)
                    .add("obtainMostCompletePrecondition", (sharedState) -> obtainMostCompletePrecondition())
                    .add("makeInternalObjectFlowsPermanent", this::makeInternalObjectFlowsPermanent)
                    .add("computeModified", (sharedState) -> methodInfo.isConstructor ? DONE : computeModified())
                    .add("computeReturnValue", (sharedState) -> methodInfo.noReturnValue() ? DONE : computeReturnValue())
                    .add("detectMissingStaticModifier", (iteration) -> methodInfo.isConstructor ? DONE : detectMissingStaticModifier())
                    .add("computeOnlyMarkPrepWork", (sharedState) -> methodInfo.isConstructor ? DONE : computeOnlyMarkPrepWork(sharedState))
                    .add("computeOnlyMarkAnnotate", (sharedState) -> methodInfo.isConstructor ? DONE : computeOnlyMarkAnnotate(sharedState))
                    .add("methodIsIndependent", this::methodIsIndependent);

            for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                builder.add("Parameter " + parameterAnalyser.parameterInfo.name, (sharedState -> parameterAnalyser.analyse()));
            }
        } else {
            methodAnalysis.minimalInfoForEmptyMethod();
        }
        analyserComponents = builder.build();
    }

    @Override
    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
        return analyserComponents;
    }

    public Collection<ParameterAnalyser> getParameterAnalysers() {
        return parameterAnalysers;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return methodInfo;
    }

    @Override
    public void initialize() {
        ImmutableMap.Builder<FieldInfo, FieldAnalyser> myFieldAnalysers = new ImmutableMap.Builder<>();
        analyserContext.getFieldAnalysers().values().forEach(analyser -> {
            if (analyser.fieldInfo.owner == methodInfo.typeInfo) {
                myFieldAnalysers.put(analyser.fieldInfo, analyser);
            }
        });
        this.myFieldAnalysers = myFieldAnalysers.build();

        // copy CONTRACT annotations into the properties
        methodAnalysis.fromAnnotationsIntoProperties(false, methodInspection.getAnnotations(),
                analyserContext.getE2ImmuAnnotationExpressions());

        parameterAnalysers.forEach(pa -> pa.initialize(analyserContext.getFieldAnalysers()));
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis;
    }

    public boolean fromFieldToParametersIsDone() {
        return !hasCode() || getParameterAnalysers().stream().allMatch(parameterAnalyser ->
                parameterAnalyser.getParameterAnalysis().isAssignedToFieldDelaysResolved());
    }

    public boolean hasCode() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return firstStatement != null;
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(Independent.class, e2.independent);

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(NotNull.class, e2.notNull);
                check(Fluent.class, e2.fluent);
                check(Identity.class, e2.identity);
                check(E1Immutable.class, e2.e1Immutable);
                check(E1Container.class, e2.e1Container);
                check(Container.class, e2.container);
                check(E2Immutable.class, e2.e2Immutable);
                check(E2Container.class, e2.e2Container);
                check(BeforeMark.class, e2.beforeMark);
                checkConstant.checkConstantForMethods(messages, methodInfo, methodAnalysis);

                // checks for dynamic properties of functional interface types
                check(NotModified1.class, e2.notModified1);
            }
            check(NotModified.class, e2.notModified);

            // opposites
            check(Nullable.class, e2.nullable);
            check(Modified.class, e2.modified);
        }
        // opposites
        check(Dependent.class, e2.dependent);

        CheckPrecondition.checkPrecondition(messages, methodInfo, methodAnalysis, companionAnalyses);

        CheckOnly.checkOnly(messages, methodInfo, methodAnalysis);
        CheckOnly.checkMark(messages, methodInfo, methodAnalysis);

        getParameterAnalysers().forEach(ParameterAnalyser::check);

        checkWorseThanOverriddenMethod();
    }

    private void checkWorseThanOverriddenMethod() {
        for (VariableProperty variableProperty : VariableProperty.CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = methodAnalysis.valueFromOverrides(analyserContext, variableProperty);
            int value = methodAnalysis.getProperty(variableProperty);
            if (valueFromOverrides != Level.DELAY && value != Level.DELAY) {
                boolean complain = variableProperty == VariableProperty.MODIFIED ? value > valueFromOverrides : value < valueFromOverrides;
                if (complain) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.WORSE_THAN_OVERRIDDEN_METHOD, variableProperty.name));
                }
            }
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(methodAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    // called from primary type analyser
    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), closure);
        SharedState sharedState = new SharedState(evaluationContext);

        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);

            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration().debugConfiguration.afterMethodAnalyserVisitors;
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            evaluationContext, methodInfo, methodAnalysis,
                            parameterAnalyses, analyserComponents.getStatusesAsMap(),
                            this::getMessageStream));
                }
            }
            return analysisStatus;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    @Override
    public void write() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        // before we check, we copy the properties into annotations
        methodAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
        parameterAnalysers.forEach(ParameterAnalyser::write);
    }

    private AnalysisStatus detectMissingStaticModifier() {
        assert !methodAnalysis.complainedAboutMissingStaticModifier.isSet();

        if (!methodInfo.methodInspection.get().isStatic() && !methodInfo.typeInfo.isInterface() && !methodInfo.isTestMethod()) {
            // we need to check if there's fields being read/assigned/
            if (absentUnlessStatic(VariableInfo::isRead) &&
                    absentUnlessStatic(VariableInfo::isAssigned) &&
                    !getThisAsVariable().isRead() &&
                    methodInfo.isNotOverridingAnyOtherMethod() &&
                    !methodInfo.methodInspection.get().isDefault()) {
                MethodResolution methodResolution = methodInfo.methodResolution.get();
                if (methodResolution.staticMethodCallsOnly()) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.METHOD_SHOULD_BE_MARKED_STATIC));
                    methodAnalysis.complainedAboutMissingStaticModifier.set(true);
                    return DONE;
                }
            }
        }
        methodAnalysis.complainedAboutMissingStaticModifier.set(false);
        return DONE;
    }

    private boolean absentUnlessStatic(Predicate<VariableInfo> variableProperty) {
        return methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .allMatch(vi -> !variableProperty.test(vi) || vi.variable().isStatic());
    }

    // simply copy from last statement
    private AnalysisStatus obtainMostCompletePrecondition() {
        assert !methodAnalysis.precondition.isSet();
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();
        if (!methodLevelData.combinedPrecondition.isSet()) return DELAYS;
        methodAnalysis.precondition.set(methodLevelData.combinedPrecondition.get());
        return DONE;
    }

    private AnalysisStatus makeInternalObjectFlowsPermanent(SharedState sharedState) {
        assert !methodAnalysis.internalObjectFlows.isSet();
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        boolean delays = !methodLevelData.internalObjectFlows.isFrozen();
        if (delays) {
            log(DELAYED, "Not yet setting internal object flows on {}, delaying", methodInfo.distinguishingName());
            return DELAYS;
        }

        Set<ObjectFlow> internalObjectFlowsWithoutParametersAndLiterals = ImmutableSet.copyOf(methodLevelData.internalObjectFlows.stream()
                .filter(of -> of.origin != Origin.PARAMETER && of.origin != Origin.LITERAL)
                .collect(Collectors.toSet()));

        internalObjectFlowsWithoutParametersAndLiterals.forEach(of -> of.finalize(null));
        methodAnalysis.internalObjectFlows.set(internalObjectFlowsWithoutParametersAndLiterals);

        methodLevelData.internalObjectFlows.stream().filter(of -> of.origin == Origin.PARAMETER).forEach(of -> {
            ParameterAnalysisImpl.Builder parameterAnalysis = getParameterAnalyser(((ParameterInfo) of.location.info)).parameterAnalysis;
            if (!parameterAnalysis.objectFlow.isSet()) {
                of.finalize(parameterAnalysis.objectFlow.getFirst());
                parameterAnalysis.objectFlow.set(of);
            }
        });
        // in the type analyser, we deal with the internal object flows of constant objects

        log(OBJECT_FLOW, "Made permanent {} internal object flows in {}", internalObjectFlowsWithoutParametersAndLiterals.size(), methodInfo.distinguishingName());
        return DONE;
    }

    ParameterAnalyser getParameterAnalyser(ParameterInfo info) {
        ParameterAnalyser parameterAnalyser = parameterAnalysers.get(info.index);
        assert parameterAnalyser.parameterInfo == info;
        return parameterAnalyser;
    }

    private AnalysisStatus computeOnlyMarkAnnotate(SharedState sharedState) {
        assert !methodAnalysis.markAndOnly.isSet();

        SetOnceMap<String, Expression> approvedPreconditions = ((TypeAnalysisImpl.Builder) typeAnalysis).approvedPreconditions;
        if (!approvedPreconditions.isFrozen()) {
            log(DELAYED, "No decision on approved preconditions yet for {}", methodInfo.distinguishingName());
            return DELAYS;
        }
        if (approvedPreconditions.size() == 0) {
            log(DELAYED, "No approved preconditions for {}, so no @Mark, @Only", methodInfo.distinguishingName());
            return DONE;
        }
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return DELAYS;
        }
        if (!methodAnalysis.preconditionForMarkAndOnly.isSet()) {
            log(DELAYED, "Waiting for preconditions to be resolved in {}", methodInfo.distinguishingName());
            return DELAYS;
        }
        List<Expression> preconditions = methodAnalysis.preconditionForMarkAndOnly.get();

        boolean mark = false;
        Boolean after = null;
        for (Expression precondition : preconditions) {
            String markLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(precondition);
            if (!approvedPreconditions.isSet(markLabel)) {
                // not going to work...
                continue;
            }
            Expression before = approvedPreconditions.get(markLabel);
            // TODO parameters have different owners, so a precondition containing them cannot be the same in a different method
            // we need a better solution
            if (before.toString().equals(precondition.toString())) {
                after = false;
            } else {
                Expression negated = Negation.negate(sharedState.evaluationContext, precondition);
                if (before.toString().equals(negated.toString())) {
                    if (after == null) after = true;
                } else {
                    E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
                    log(MARK, "No approved preconditions for {} in {}", precondition, methodInfo.distinguishingName());
                    if (!methodAnalysis.annotations.isSet(e2.mark)) {
                        methodAnalysis.annotations.put(e2.mark, false);
                    }
                    if (!methodAnalysis.annotations.isSet(e2.only)) {
                        methodAnalysis.annotations.put(e2.only, false);
                    }
                    return DONE;
                }
            }

            if (modified == Level.FALSE) {
                log(MARK, "Method {} is @NotModified, so it'll be @Only rather than @Mark", methodInfo.distinguishingName());
            } else {
                // for the before methods, we need to check again if we were mark or only
                mark = mark || (!after && TypeAnalyser.assignmentIncompatibleWithPrecondition(sharedState.evaluationContext,
                        precondition, this));
            }
        }
        if (after == null) {

            // this bit of code is here temporarily as a backup; it is the code in the type analyser that should
            // keep approvedPreconditions empty
            if (modified == Level.TRUE && !methodAnalysis.complainedAboutApprovedPreconditions.isSet()) {
                methodAnalysis.complainedAboutApprovedPreconditions.set(true);
                messages.add(Message.newMessage(new Location(methodInfo), Message.NO_APPROVED_PRECONDITIONS));
            }
            return DONE;
        }

        String jointMarkLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(preconditions);
        MethodAnalysis.MarkAndOnly markAndOnly = new MethodAnalysis.MarkAndOnly(preconditions, jointMarkLabel, mark, after);
        methodAnalysis.markAndOnly.set(markAndOnly);
        log(MARK, "Marking {} with only data {}", methodInfo.distinguishingName(), markAndOnly);
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (mark) {
            AnnotationExpression markAnnotation = new AnnotationExpressionImpl(e2.mark.typeInfo(),
                    List.of(new MemberValuePair("value",
                            new StringConstant(analyserContext.getPrimitives(), jointMarkLabel))));
            methodAnalysis.annotations.put(markAnnotation, true);
            methodAnalysis.annotations.put(e2.only, false);
        } else {
            AnnotationExpression onlyAnnotation = new AnnotationExpressionImpl(e2.only.typeInfo(),
                    List.of(new MemberValuePair(after ? "after" : "before",
                            new StringConstant(analyserContext.getPrimitives(), jointMarkLabel))));
            methodAnalysis.annotations.put(onlyAnnotation, true);
            methodAnalysis.annotations.put(e2.mark, false);
        }
        return DONE;
    }

    private AnalysisStatus computeOnlyMarkPrepWork(SharedState sharedState) {
        assert !methodAnalysis.preconditionForMarkAndOnly.isSet();

        TypeInfo typeInfo = methodInfo.typeInfo;
        while (true) {
            boolean haveNonFinalFields = myFieldAnalysers.values().stream().anyMatch(fa -> fa.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE);
            if (haveNonFinalFields) {
                break;
            }
            ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass();
            if (Primitives.isJavaLangObject(parentClass)) {
                log(MARK, "No @Mark/@Only annotation in {}: found no non-final fields", methodInfo.distinguishingName());
                methodAnalysis.preconditionForMarkAndOnly.set(List.of());
                return DONE;
            }
            typeInfo = parentClass.bestTypeInfo();
        }

        if (!methodAnalysis.precondition.isSet()) {
            log(DELAYED, "Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            return DELAYS;
        }
        Expression precondition = methodAnalysis.precondition.get();
        if (precondition.isBoolValueTrue()) {
            log(MARK, "No @Mark @Only annotation in {}, as no precondition", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return DONE;
        }
        // at this point, the null and size checks on parameters have been removed.
        // we still need to remove other parameter components; what remains can be used for marking/only

        Filter filter = new Filter(sharedState.evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<FieldReference> filterResult = filter.filter(precondition, filter.individualFieldClause());
        if (filterResult.accepted().isEmpty()) {
            log(MARK, "No @Mark/@Only annotation in {}: found no individual field preconditions", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return DONE;
        }
        List<Expression> preconditionParts = new ArrayList<>(filterResult.accepted().values());
        log(MARK, "Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition,
                filterResult.accepted().keySet(), methodInfo.distinguishingName());
        methodAnalysis.preconditionForMarkAndOnly.set(preconditionParts);
        return DONE;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private AnalysisStatus computeReturnValue() {
        assert !methodAnalysis.singleReturnValue.isSet();

        VariableInfo variableInfo = getReturnAsVariable();
        Expression value = variableInfo.getValue();
        if (value == EmptyExpression.NO_VALUE || value.isInitialReturnExpression()) {

            // it is possible that none of the return statements are reachable... in which case there should be no delay,
            // and no SRV
            if (noReturnStatementReachable()) {
                methodAnalysis.singleReturnValue.set(new UnknownExpression(methodInfo.returnType(), "does not return a value"));
                methodAnalysis.setProperty(VariableProperty.IDENTITY, Level.FALSE);
                methodAnalysis.setProperty(VariableProperty.FLUENT, Level.FALSE);
                methodAnalysis.setProperty(VariableProperty.NOT_NULL, VariableProperty.NOT_NULL.best);
                methodAnalysis.setProperty(VariableProperty.IMMUTABLE, VariableProperty.IMMUTABLE.best);
                methodAnalysis.setProperty(VariableProperty.CONTAINER, VariableProperty.CONTAINER.best);
                return DONE;
            }
            log(DELAYED, "Method {} has return value {}, delaying", methodInfo.distinguishingName(), value.debugOutput());
            return DELAYS;
        }

        if (!methodAnalysis.internalObjectFlows.isSet()) {
            log(DELAYED, "Delaying single return value because internal object flows not yet known, {}", methodInfo.distinguishingName());
            return DELAYS;
        }
        ObjectFlow objectFlow = value.getObjectFlow();
        if (objectFlow == null)
            throw new UnsupportedOperationException("Null object flow for value of " + value.getClass());
        if (objectFlow != ObjectFlow.NO_FLOW && !methodAnalysis.objectFlow.isSet()) {
            log(OBJECT_FLOW, "Set final object flow object for method {}: {}", methodInfo.distinguishingName(), objectFlow);
            objectFlow.finalize(methodAnalysis.objectFlow.getFirst());
            methodAnalysis.objectFlow.set(objectFlow);
        }

        // try to compute the dynamic immutable status of value
        Expression valueBeforeInlining = value;
        int immutable;
        if (value.isConstant()) {
            immutable = MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        } else {
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) {
                log(DELAYED, "Delaying return value of {}, waiting for MODIFIED (we may try to inline!)", methodInfo.distinguishingName());
                return DELAYS;
            }
            if (modified == Level.FALSE) {
                InlinedMethod.Applicability applicability = applicability(value);
                if (applicability != InlinedMethod.Applicability.NONE) {
                    value = new InlinedMethod(methodInfo, value, applicability);
                    immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                } else {
                    immutable = MultiLevel.MUTABLE; // no idea
                }
            } else {
                immutable = MultiLevel.MUTABLE; // no idea
            }
        }

        boolean isConstant = value.isConstant();

        methodAnalysis.singleReturnValue.set(value);
        methodAnalysis.singleReturnValueImmutable.set(immutable);
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (isConstant) {
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(e2.constant, false);
        }
        methodAnalysis.setProperty(VariableProperty.CONSTANT, Level.fromBool(isConstant));
        log(CONSTANT, "Mark method {} as @Constant? {}", methodInfo.fullyQualifiedName(), isConstant);

        boolean isFluent = valueBeforeInlining instanceof VariableExpression vv &&
                vv.variable() instanceof This thisVar &&
                thisVar.typeInfo == methodInfo.typeInfo;
        methodAnalysis.setProperty(VariableProperty.FLUENT, Level.fromBool(isFluent));
        log(FLUENT, "Mark method {} as @Fluent? {}", methodInfo.fullyQualifiedName(), isFluent);

        for (VariableProperty variableProperty : VariableProperty.READ_FROM_RETURN_VALUE_PROPERTIES) {
            int v = variableInfo.getProperty(variableProperty);
            if (v == Level.DELAY) v = variableProperty.falseValue;
            methodAnalysis.setProperty(variableProperty, v);
            log(NOT_NULL, "Set {} of {} to value {}", variableProperty, methodInfo.fullyQualifiedName, v);
        }

        return DONE;
    }

    private boolean noReturnStatementReachable() {
        StatementAnalysis firstStatement = methodAnalysis.getFirstStatement();
        return !recursivelyFindReachableReturnStatement(firstStatement);
    }

    private static boolean recursivelyFindReachableReturnStatement(StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        while (!sa.flowData.isUnreachable()) {
            if (sa.statement instanceof ReturnStatement) return true;
            for (Optional<StatementAnalysis> first : sa.navigationData.blocks.get()) {
                if (first.isPresent() && recursivelyFindReachableReturnStatement(first.get())) {
                    return true;
                }
            }
            if (sa.navigationData.next.get().isEmpty()) break;
            sa = sa.navigationData.next.get().get();
        }
        return false;
    }

    private InlinedMethod.Applicability applicabilityField(FieldInfo fieldInfo) {
        Set<FieldModifier> fieldModifiers = fieldInfo.fieldInspection.get().getModifiers();
        if (fieldModifiers.contains(FieldModifier.PRIVATE)) return InlinedMethod.Applicability.TYPE;
        if (fieldModifiers.contains(FieldModifier.PUBLIC)) return InlinedMethod.Applicability.EVERYWHERE;
        return InlinedMethod.Applicability.PACKAGE;
    }

    private InlinedMethod.Applicability applicability(Expression value) {
        AtomicReference<InlinedMethod.Applicability> applicability = new AtomicReference<>(InlinedMethod.Applicability.EVERYWHERE);
        value.visit(v -> {
            if (v.isUnknown()) {
                applicability.set(InlinedMethod.Applicability.NONE);
            }
            VariableExpression valueWithVariable;
            if ((valueWithVariable = v.asInstanceOf(VariableExpression.class)) != null) {
                Variable variable = valueWithVariable.variable();
                if (variable.isLocal()) {
                    // TODO make a distinction between a local variable, and a local var outside a lambda
                    applicability.set(InlinedMethod.Applicability.NONE);
                } else if (variable instanceof FieldReference) {
                    InlinedMethod.Applicability fieldApplicability = applicabilityField(((FieldReference) variable).fieldInfo);
                    InlinedMethod.Applicability current = applicability.get();
                    applicability.set(current.mostRestrictive(fieldApplicability));
                }
            }
            return true; // go deeper
        });
        return applicability.get();
    }

    private AnalysisStatus computeModified() {
        if (methodAnalysis.getProperty(VariableProperty.MODIFIED) != Level.DELAY) return DONE;
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        // first step, check field assignments
        boolean fieldAssignments = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .anyMatch(VariableInfo::isAssigned);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return DONE;
        }

        // if there are no field assignments, there may be modifying method calls

        // second step, check that linking has been computed
        if (!methodLevelData.linksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @Modified yet, delaying because linking not computed",
                    methodInfo.distinguishingName());
            return DELAYS;
        }
        boolean isModified = methodAnalysis.getLastStatement().variableStream()
                .filter(vi -> vi.variable() instanceof FieldReference)
                .anyMatch(vi -> vi.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (isModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodAnalysis.getLastStatement().variableStream()
                            .filter(vi -> vi.variable() instanceof FieldReference)
                            .filter(vi -> vi.getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                            .map(VariableInfo::name).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified && !methodInfo.methodInspection.get().isStatic()) {
            boolean localMethodsCalled = getThisAsVariable().getProperty(VariableProperty.METHOD_CALLED) == Level.TRUE;
            // IMPORTANT: localMethodsCalled only works on "this"; it does not work for static methods (See IdentityChecks)
            if (localMethodsCalled) {
                int thisModified = getThisAsVariable().getProperty(VariableProperty.MODIFIED);

                if (thisModified == Level.DELAY) {
                    log(DELAYED, "In {}: other local methods are called, but no idea if they are @NotModified yet, delaying",
                            methodInfo.distinguishingName());
                    return DELAYS;
                }
                isModified = thisModified == Level.TRUE;
                log(NOT_MODIFIED, "Mark method {} as {}", methodInfo.distinguishingName(),
                        isModified ? "@Modified" : "@NotModified");
            }
        } // else: already true, so no need to look at this

        if (!isModified) {
            // if there are no modifying method calls, we still may have a modifying method
            // this will be due to calling undeclared SAMs, or calling non-modifying methods in a circular type situation
            // (A.nonModifying() calls B.modifying() on a parameter (NOT a field, so nonModifying is just that) which itself calls A.modifying()
            // NOTE that in this situation we cannot have a container, as we require a modifying! (TODO check this statement is correct)
            Boolean circular = methodLevelData.getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod();
            if (circular == null) {
                log(DELAYED, "Delaying modification on method {}, waiting for calls to undeclared functional interfaces",
                        methodInfo.distinguishingName());
                return DELAYS;
            }
            if (circular) {
                Boolean haveModifying = findOtherModifyingElements();
                if (haveModifying == null) return DELAYS;
                isModified = haveModifying;
            }
        }
        if (!isModified) {
            OptionalInt maxModified = methodLevelData.copyModificationStatusFrom.stream().mapToInt(mi -> mi.getKey().methodAnalysis.get().getProperty(VariableProperty.MODIFIED)).max();
            if (maxModified.isPresent()) {
                int mm = maxModified.getAsInt();
                if (mm == Level.DELAY) {
                    log(DELAYED, "Delaying modification on method {}, waiting to copy", methodInfo.distinguishingName());
                    return DELAYS;
                }
                isModified = maxModified.getAsInt() == Level.TRUE;
            }
        }
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(VariableProperty.MODIFIED, isModified ? Level.TRUE : Level.FALSE);
        return DONE;
    }

    private Boolean findOtherModifyingElements() {
        boolean nonPrivateFields = myFieldAnalysers.values().stream()
                .filter(fa -> fa.fieldInfo.type.isFunctionalInterface() && fa.fieldAnalysis.isDeclaredFunctionalInterface())
                .anyMatch(fa -> !fa.fieldInfo.isPrivate());
        if (nonPrivateFields) {
            return true;
        }
        // We also check independence (maybe the user calls a method which returns one of the fields,
        // and calls a modification directly)

        Optional<MethodInfo> someOtherMethodNotYetDecided = methodInfo.typeInfo.typeInspection.get()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(mi ->
                        analyserContext.getMethodAnalysis(mi).methodLevelData().getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod() == null ||
                                (analyserContext.getMethodAnalysis(mi).methodLevelData().getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod() && (
                                        analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.MODIFIED) == Level.DELAY ||
                                                mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext) == null ||
                                                analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.INDEPENDENT) == Level.DELAY)))
                .findFirst();
        if (someOtherMethodNotYetDecided.isPresent()) {
            log(DELAYED, "Delaying modification on method {} which calls an undeclared functional interface, because of {}",
                    methodInfo.distinguishingName(), someOtherMethodNotYetDecided.get().name);
            return null;
        }
        return methodInfo.typeInfo.typeInspection.get()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .anyMatch(mi -> analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.MODIFIED) == Level.TRUE ||
                        !mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext) &&
                                analyserContext.getMethodAnalysis(mi).getProperty(VariableProperty.INDEPENDENT) == Level.FALSE);
    }

    private AnalysisStatus methodIsIndependent(SharedState sharedState) {
        assert methodAnalysis.getProperty(VariableProperty.INDEPENDENT) == Level.DELAY;
        MethodLevelData methodLevelData = methodAnalysis.methodLevelData();

        if (!methodInfo.isConstructor) {
            // we only compute @Independent/@Dependent on methods when the method is @NotModified
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.TRUE) {
                methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return DONE;
            }
        } // else: for constructors, we assume @Modified so that rule is not that useful

        if (!methodLevelData.linksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not yet computed", methodInfo.fullyQualifiedName());
            return DELAYS;
        }

        // PART 1: check the return object, if it is there

        // support data types are not set for types that have not been defined; there, we rely on annotations
        Boolean returnObjectIsIndependent = independenceStatusOfReturnType(methodInfo, methodLevelData);
        if (returnObjectIsIndependent == null) {
            return DELAYS;
        }

        // CONSTRUCTOR ...
        boolean parametersIndependentOfFields;
        if (methodInfo.isConstructor) {
            // TODO check ExplicitConstructorInvocations

            // PART 2: check parameters, but remove those that are recursively of my own type
            List<ParameterInfo> parameters = new ArrayList<>(methodInfo.methodInspection.get().getParameters());
            parameters.removeIf(pi -> pi.parameterizedType.typeInfo == methodInfo.typeInfo);

            boolean allLinkedVariablesSet = methodAnalysis.getLastStatement().variableStream()
                    .filter(vi -> vi.variable() instanceof FieldReference)
                    .allMatch(vi -> vi.getLinkedVariables() != LinkedVariables.DELAY);
            if (!allLinkedVariablesSet) {
                log(DELAYED, "Delaying @Independent on {}, linked variables not yet known for all field references", methodInfo.distinguishingName());
                return DELAYS;
            }
            boolean supportDataSet = methodAnalysis.getLastStatement().variableStream()
                    .filter(vi -> vi.variable() instanceof FieldReference)
                    .flatMap(vi -> vi.getLinkedVariables().variables().stream())
                    .allMatch(v -> isImplicitlyImmutableDataTypeSet(v, analyserContext));
            if (!supportDataSet) {
                log(DELAYED, "Delaying @Independent on {}, support data not yet known for all field references", methodInfo.distinguishingName());
                return DELAYS;
            }

            parametersIndependentOfFields = methodAnalysis.getLastStatement().variableStream()
                    .filter(vi -> vi.variable() instanceof FieldReference)
                    .peek(vi -> {
                        if (vi.getLinkedVariables() == LinkedVariables.DELAY)
                            LOGGER.warn("Field {} has no linked variables set in {}", vi.name(), methodInfo.distinguishingName());
                    })
                    .flatMap(vi -> vi.getLinkedVariables().variables().stream())
                    .filter(v -> v instanceof ParameterInfo)
                    .map(v -> (ParameterInfo) v)
                    .peek(set -> log(LINKED_VARIABLES, "Remaining linked support variables of {} are {}", methodInfo.distinguishingName(), set))
                    .noneMatch(parameters::contains);

        } else parametersIndependentOfFields = true;

        // conclusion

        boolean independent = parametersIndependentOfFields && returnObjectIsIndependent;
        methodAnalysis.setProperty(VariableProperty.INDEPENDENT, independent ? MultiLevel.EFFECTIVE : MultiLevel.FALSE);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return DONE;
    }

    private Boolean independenceStatusOfReturnType(MethodInfo methodInfo, MethodLevelData methodLevelData) {
        if (methodInfo.noReturnValue()) return true;
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(methodInfo.typeInfo);
        if (typeAnalysis.getImplicitlyImmutableDataTypes() != null &&
                typeAnalysis.getImplicitlyImmutableDataTypes().contains(methodInfo.returnType())) {
            return true;
        }

        if (!methodLevelData.linksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                    methodInfo.fullyQualifiedName());
            return null;
        }
        // method does not return an implicitly immutable data type
        VariableInfo returnVariable = getReturnAsVariable();
        LinkedVariables linkedVariables = returnVariable.getLinkedVariables();
        boolean implicitlyImmutableSet = linkedVariables != LinkedVariables.DELAY &&
                linkedVariables.variables().stream().allMatch(v -> isImplicitlyImmutableDataTypeSet(v, analyserContext));
        if (!implicitlyImmutableSet) {
            log(DELAYED, "Delaying @Independent on {}, implicitly immutable status not known for all field references", methodInfo.distinguishingName());
            return null;
        }

        // TODO convert the variables into field analysers

        int e2ImmutableStatusOfFieldRefs = linkedVariables.variables().stream()
                .filter(v -> isFieldNotOfImplicitlyImmutableType(v, analyserContext))
                .map(v -> analyserContext.getFieldAnalysers().get(((FieldReference) v).fieldInfo))
                .mapToInt(fa -> MultiLevel.value(fa.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE))
                .min().orElse(MultiLevel.EFFECTIVE);
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.DELAY) {
            log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                    linkedVariables.variables().stream()
                            .filter(v -> isFieldNotOfImplicitlyImmutableType(v, analyserContext) &&
                                    MultiLevel.value(analyserContext.getFieldAnalysers()
                                                    .get(((FieldReference) v).fieldInfo).fieldAnalysis.getProperty(VariableProperty.IMMUTABLE),
                                            MultiLevel.E2IMMUTABLE) == MultiLevel.DELAY)
                            .map(Variable::fullyQualifiedName)
                            .collect(Collectors.joining(", ")));
            return null;
        }
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.EFFECTIVE) return true;
        int immutable = methodInfo.returnType().getProperty(analyserContext, VariableProperty.IMMUTABLE);
        int formalE2ImmutableStatusOfReturnType = MultiLevel.value(immutable, MultiLevel.E2IMMUTABLE);
        if (formalE2ImmutableStatusOfReturnType == MultiLevel.DELAY) {
            log(DELAYED, "Have formal return type, no idea if E2Immutable: {}", methodInfo.distinguishingName());
            return null;
        }
        if (formalE2ImmutableStatusOfReturnType >= MultiLevel.EVENTUAL) {
            log(INDEPENDENT, "Method {} is independent, formal return type is E2Immutable", methodInfo.distinguishingName());
            return true;
        }

        if (methodAnalysis.singleReturnValue.isSet()) {
            int imm = methodAnalysis.singleReturnValueImmutable.get();
            int dynamicE2ImmutableStatusOfReturnType = MultiLevel.value(imm, MultiLevel.E2IMMUTABLE);
            if (dynamicE2ImmutableStatusOfReturnType >= MultiLevel.EVENTUAL) {
                log(INDEPENDENT, "Method {} is independent, dynamic return type is E2Immutable", methodInfo.distinguishingName());
                return true;
            }
        }
        return false;
    }

    public static boolean isImplicitlyImmutableDataTypeSet(Variable v, AnalysisProvider analysisProvider) {
        return !(v instanceof FieldReference) ||
                analysisProvider.getFieldAnalysis(((FieldReference) v).fieldInfo).isOfImplicitlyImmutableDataType() != null;
    }

    public static boolean isFieldNotOfImplicitlyImmutableType(Variable variable, AnalysisProvider analysisProvider) {
        if (!(variable instanceof FieldReference)) return false;
        FieldAnalysis fieldAnalysis = analysisProvider.getFieldAnalysis(((FieldReference) variable).fieldInfo);
        return fieldAnalysis.isOfImplicitlyImmutableDataType() == Boolean.FALSE;
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(super.getMessageStream(), getParameterAnalysers().stream().flatMap(ParameterAnalyser::getMessageStream));
    }


    public MethodLevelData methodLevelData() {
        return methodAnalysis.methodLevelData();
    }

    public void logAnalysisStatuses() {
        AnalysisStatus statusOfStatementAnalyser = analyserComponents.getStatus(MethodAnalyser.STATEMENT_ANALYSER);
        if (statusOfStatementAnalyser == AnalysisStatus.DELAYS) {
            StatementAnalyser lastStatement = firstStatementAnalyser.lastStatement();
            AnalyserComponents<String, StatementAnalyser.SharedState> analyserComponentsOfStatement = lastStatement.getAnalyserComponents();
            LOGGER.warn("Analyser components of last statement {} of {}:\n{}", lastStatement.index(),
                    methodInfo.fullyQualifiedName(),
                    analyserComponentsOfStatement.details());
            AnalysisStatus statusOfMethodLevelData = analyserComponentsOfStatement.getStatus(StatementAnalyser.ANALYSE_METHOD_LEVEL_DATA);
            if (statusOfMethodLevelData == AnalysisStatus.DELAYS) {
                AnalyserComponents<String, MethodLevelData.SharedState> analyserComponentsOfMethodLevelData =
                        lastStatement.statementAnalysis.methodLevelData.analyserComponents;
                LOGGER.warn("Analyser components of method level data of last statement {} of {}:\n{}", lastStatement.index(),
                        methodInfo.fullyQualifiedName(),
                        analyserComponentsOfMethodLevelData.details());
            }
        }
    }

    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean includeLocalCopies) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? List.of() :
                methodAnalysis.getLastStatement().latestInfoOfVariablesReferringTo(fieldInfo, includeLocalCopies);
    }

    // occurs as often in a flatMap as not, so a stream version is useful
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        return lastStatement == null ? Stream.empty() :
                methodAnalysis.getLastStatement().streamOfLatestInfoOfVariablesReferringTo(fieldInfo, includeLocalCopies);
    }

    public VariableInfo getThisAsVariable() {
        return methodAnalysis.getLastStatement().getLatestVariableInfo(methodInfo.typeInfo.fullyQualifiedName + ".this");
    }

    public VariableInfo getReturnAsVariable() {
        StatementAnalysis lastStatement = methodAnalysis.getLastStatement();
        assert lastStatement != null; // either constructor, and then we shouldn't ask; or compilation error
        return lastStatement.getLatestVariableInfo(methodInfo.fullyQualifiedName());
    }

    public StatementAnalysis findStatementAnalysis(String index) {
        return firstStatementAnalyser.statementAnalysis.navigateTo(index);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(iteration, conditionManager, closure);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            if (variable instanceof FieldReference fieldReference) {
                return getFieldAnalysis(fieldReference.fieldInfo).getProperty(variableProperty);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                return getParameterAnalysis(parameterInfo).getProperty(variableProperty);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty) {
            return value.getProperty(this, variableProperty);
        }
    }
}
