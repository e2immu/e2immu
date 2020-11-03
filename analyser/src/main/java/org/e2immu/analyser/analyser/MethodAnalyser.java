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
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    public final TypeAnalyser myTypeAnalyser;
    public final List<ParameterAnalyser> parameterAnalysers;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final StatementAnalyser firstStatementAnalyser;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final CheckConstant checkConstant;

    private Map<FieldInfo, FieldAnalyser> myFieldAnalysers;

    private record SharedState(int iteration, MethodLevelData methodLevelData) {
    }

    public MethodAnalyser(MethodInfo methodInfo,
                          TypeAnalyser myTypeAnalyser,
                          boolean isSAM,
                          AnalyserContext analyserContext) {
        super("Method " + methodInfo.name, analyserContext);
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives());
        this.methodInfo = methodInfo;
        methodInspection = methodInfo.methodInspection.get();
        ImmutableList.Builder<ParameterAnalyser> parameterAnalysersBuilder = new ImmutableList.Builder<>();
        for (ParameterInfo parameterInfo : methodInspection.parameters) {
            parameterAnalysersBuilder.add(new ParameterAnalyser(analyserContext, parameterInfo));
        }
        parameterAnalysers = parameterAnalysersBuilder.build();
        this.myTypeAnalyser = myTypeAnalyser;
        parameterAnalyses = parameterAnalysers.stream().map(pa -> pa.parameterAnalysis).collect(Collectors.toUnmodifiableList());

        Block block = methodInspection.methodBody.get();
        methodAnalysis = new MethodAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext,
                methodInfo, parameterAnalyses);

        if (block == Block.EMPTY_BLOCK) {
            firstStatementAnalyser = null;
        } else {
            firstStatementAnalyser = StatementAnalyser.recursivelyCreateAnalysisObjects(analyserContext,
                    this, null, block.structure.statements, "", true, false);
            methodAnalysis.setFirstStatement(firstStatementAnalyser.statementAnalysis);
        }
        this.isSAM = isSAM;

        AnalyserComponents.Builder<String, SharedState> builder = new AnalyserComponents.Builder<>();
        if (firstStatementAnalyser != null) {

            AnalysisStatus.AnalysisResultSupplier<SharedState> statementAnalyser = (sharedState) -> {
                StatementAnalyserResult result = firstStatementAnalyser.analyseAllStatementsInBlock(sharedState.iteration,
                        ForwardAnalysisInfo.START_OF_METHOD);
                // apply all modifications
                result.getModifications().forEach(Runnable::run);
                this.messages.addAll(result.messages);
                return result.analysisStatus;
            };

            builder.add(STATEMENT_ANALYSER, statementAnalyser)
                    .add("obtainMostCompletePrecondition", this::obtainMostCompletePrecondition)
                    .add("makeInternalObjectFlowsPermanent", this::makeInternalObjectFlowsPermanent)
                    .add("propertiesOfReturnStatements", (sharedState) -> methodInfo.isConstructor ? DONE : propertiesOfReturnStatements(sharedState))
                    .add("methodIsConstant", (sharedState) -> methodInfo.isConstructor ? DONE : methodIsConstant(sharedState))
                    .add("detectMissingStaticModifier", (iteration) -> methodInfo.isConstructor ? DONE : detectMissingStaticModifier())
                    .add("methodIsModified", (sharedState) -> methodInfo.isConstructor ? DONE : methodIsModified(sharedState))
                    .add("computeOnlyMarkPrepWork", (sharedState) -> methodInfo.isConstructor ? DONE : computeOnlyMarkPrepWork(sharedState))
                    .add("computeOnlyMarkAnnotate", (sharedState) -> methodInfo.isConstructor ? DONE : computeOnlyMarkAnnotate(sharedState))
                    .add("methodIsIndependent", this::methodIsIndependent)
                    .add("computeSize", this::computeSize)
                    .add("computeSizeCopy", this::computeSizeCopy);

            for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                builder.add("Parameter " + parameterAnalyser.parameterInfo.name, (iteration -> parameterAnalyser.analyse()));
            }
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
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis;
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(Independent.class, e2.independent.get());

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(NotNull.class, e2.notNull.get());
                check(Fluent.class, e2.fluent.get());
                check(Identity.class, e2.identity.get());
                check(E1Immutable.class, e2.e1Immutable.get());
                check(E1Container.class, e2.e1Container.get());
                check(Container.class, e2.container.get());
                check(E2Immutable.class, e2.e2Immutable.get());
                check(E2Container.class, e2.e2Container.get());
                check(BeforeMark.class, e2.beforeMark.get());
                checkConstant.checkConstantForMethods(messages, methodInfo, methodAnalysis);

                // checks for dynamic properties of functional interface types
                check(NotModified1.class, e2.notModified1.get());
            }
            check(NotModified.class, e2.notModified.get());

            // opposites
            check(Nullable.class, e2.nullable.get());
            check(Modified.class, e2.modified.get());
        }
        // opposites
        check(Dependent.class, e2.dependent.get());

        CheckSize.checkSizeForMethods(messages, methodInfo, methodAnalysis);
        CheckPrecondition.checkPrecondition(messages, methodInfo);
        CheckOnly.checkOnly(messages, methodInfo, methodAnalysis);
        CheckOnly.checkMark(messages, methodInfo, methodAnalysis);

        getParameterAnalysers().forEach(ParameterAnalyser::check);

        checkWorseThanOverriddenMethod();
    }

    private void checkWorseThanOverriddenMethod() {
        for (VariableProperty variableProperty : VariableProperty.CHECK_WORSE_THAN_PARENT) {
            int valueFromOverrides = methodAnalysis.valueFromOverrides(variableProperty);
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

    @Override
    public AnalysisStatus analyse(int iteration) {
        log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());
        SharedState sharedState = new SharedState(iteration, methodAnalysis.methodLevelData());

        try {
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);

            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration().debugConfiguration.afterMethodAnalyserVisitors;
            if (!visitors.isEmpty()) {
                EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, ConditionManager.INITIAL);
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            evaluationContext, methodInfo, methodAnalysis,
                            parameterAnalyses, analyserComponents.getStatusesAsMap()));
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
        methodAnalysis.transferPropertiesToAnnotations(e2);
        parameterAnalysers.forEach(ParameterAnalyser::write);
    }

    private AnalysisStatus detectMissingStaticModifier() {
        assert !methodAnalysis.complainedAboutMissingStaticModifier.isSet();

        if (!methodInfo.isStatic && !methodInfo.typeInfo.isInterface() && !methodInfo.isTestMethod()) {
            // we need to check if there's fields being read/assigned/
            if (absentUnlessStatic(VariableProperty.READ) &&
                    absentUnlessStatic(VariableProperty.ASSIGNED) &&
                    (methodAnalysis.methodLevelData().thisSummary.get().properties.getOrDefault(VariableProperty.READ, Level.DELAY) < Level.TRUE) &&
                    !methodInfo.hasOverrides(analyserContext.getPrimitives()) &&
                    !methodInfo.isDefaultImplementation) {
                MethodResolution methodResolution = methodInfo.methodResolution.get();
                if (methodResolution.staticMethodCallsOnly.isSet() && methodResolution.staticMethodCallsOnly.get()) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.METHOD_SHOULD_BE_MARKED_STATIC));
                    methodAnalysis.complainedAboutMissingStaticModifier.set(true);
                    return DONE;
                }
            }
        }
        methodAnalysis.complainedAboutMissingStaticModifier.set(false);
        return DONE;
    }

    private boolean absentUnlessStatic(VariableProperty variableProperty) {
        return methodLevelData().fieldSummaries.stream().allMatch(e -> e.getValue()
                .properties.getOrDefault(variableProperty, Level.DELAY) < Level.TRUE || e.getKey().isStatic());
    }

    // simply copy from last statement
    private AnalysisStatus obtainMostCompletePrecondition(SharedState sharedState) {
        assert !methodAnalysis.precondition.isSet();
        if (!sharedState.methodLevelData.combinedPrecondition.isSet()) return DELAYS;
        methodAnalysis.precondition.set(sharedState.methodLevelData.combinedPrecondition.get());
        return DONE;
    }

    private AnalysisStatus makeInternalObjectFlowsPermanent(SharedState sharedState) {
        assert !methodAnalysis.internalObjectFlows.isSet();
        MethodLevelData methodLevelData = sharedState.methodLevelData;

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

        SetOnceMap<String, Value> approvedPreconditions = myTypeAnalyser.typeAnalysis.approvedPreconditions;
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
        List<Value> preconditions = methodAnalysis.preconditionForMarkAndOnly.get();

        boolean mark = false;
        Boolean after = null;
        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration, ConditionManager.INITIAL);
        for (Value precondition : preconditions) {
            String markLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(precondition);
            if (!approvedPreconditions.isSet(markLabel)) {
                // not going to work...
                continue;
            }
            Value before = approvedPreconditions.get(markLabel);
            // TODO parameters have different owners, so a precondition containing them cannot be the same in a different method
            // we need a better solution
            if (before.toString().equals(precondition.toString())) {
                after = false;
            } else {
                Value negated = NegatedValue.negate(evaluationContext, precondition);
                if (before.toString().equals(negated.toString())) {
                    if (after == null) after = true;
                } else {
                    E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
                    log(MARK, "No approved preconditions for {} in {}", precondition, methodInfo.distinguishingName());
                    if (!methodAnalysis.annotations.isSet(e2.mark.get())) {
                        methodAnalysis.annotations.put(e2.mark.get(), false);
                    }
                    if (!methodAnalysis.annotations.isSet(e2.only.get())) {
                        methodAnalysis.annotations.put(e2.only.get(), false);
                    }
                    return DONE;
                }
            }

            if (modified == Level.FALSE) {
                log(MARK, "Method {} is @NotModified, so it'll be @Only rather than @Mark", methodInfo.distinguishingName());
            } else {
                // for the before methods, we need to check again if we were mark or only
                mark = mark || (!after && TypeAnalyser.assignmentIncompatibleWithPrecondition(evaluationContext,
                        precondition, methodInfo, methodLevelData()));
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
            AnnotationExpression markAnnotation = AnnotationExpression.fromAnalyserExpressions(e2.mark.get().typeInfo,
                    List.of(new MemberValuePair("value",
                            new StringConstant(analyserContext.getPrimitives(), jointMarkLabel))));
            methodAnalysis.annotations.put(markAnnotation, true);
            methodAnalysis.annotations.put(e2.only.get(), false);
        } else {
            AnnotationExpression onlyAnnotation = AnnotationExpression.fromAnalyserExpressions(e2.only.get().typeInfo,
                    List.of(new MemberValuePair(after ? "after" : "before",
                            new StringConstant(analyserContext.getPrimitives(), jointMarkLabel))));
            methodAnalysis.annotations.put(onlyAnnotation, true);
            methodAnalysis.annotations.put(e2.mark.get(), false);
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
            ParameterizedType parentClass = typeInfo.typeInspection.getPotentiallyRun().parentClass;
            typeInfo = parentClass.bestTypeInfo();
            if (typeInfo == null) {
                log(MARK, "No @Mark/@Only annotation in {}: found no non-final fields", methodInfo.distinguishingName());
                methodAnalysis.preconditionForMarkAndOnly.set(List.of());
                return DONE;
            }
        }

        if (!methodAnalysis.precondition.isSet()) {
            log(DELAYED, "Delaying compute @Only and @Mark, precondition not set (weird, should be set by now)");
            return DELAYS;
        }
        Value precondition = methodAnalysis.precondition.get();
        if (precondition == UnknownValue.EMPTY) {
            log(MARK, "No @Mark @Only annotation in {}, as no precondition", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return DONE;
        }
        // at this point, the null and size checks on parameters have been removed.
        // we still need to remove other parameter components; what remains can be used for marking/only

        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration, ConditionManager.INITIAL);
        Value.FilterResult filterResult = precondition.filter(evaluationContext, Value.FilterMode.ACCEPT, Value::isIndividualFieldCondition);
        if (filterResult.accepted.isEmpty()) {
            log(MARK, "No @Mark/@Only annotation in {}: found no individual field preconditions", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return DONE;
        }
        List<Value> preconditionParts = new ArrayList<>(filterResult.accepted.values());
        log(MARK, "Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition, filterResult.accepted.keySet(), methodInfo.distinguishingName());
        methodAnalysis.preconditionForMarkAndOnly.set(preconditionParts);
        return DONE;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private AnalysisStatus methodIsConstant(SharedState sharedState) {
        assert !methodAnalysis.singleReturnValue.isSet();
        MethodLevelData methodLevelData = sharedState.methodLevelData;
        if (methodLevelData.returnStatementSummaries.isEmpty()) return DONE;

        boolean allReturnValuesSet = methodLevelData.returnStatementSummaries.stream().allMatch(e -> e.getValue().value.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return DELAYS;
        }
        List<TransferValue> remainingReturnStatementSummaries = methodLevelData.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());

        Value value = null;
        int immutable = Level.DELAY;

        if (remainingReturnStatementSummaries.size() == 1) {
            Value single = remainingReturnStatementSummaries.get(0).value.get();
            if (!methodAnalysis.internalObjectFlows.isSet()) {
                log(DELAYED, "Delaying single return value because internal object flows not yet known, {}", methodInfo.distinguishingName());
                return DELAYS;
            }
            ObjectFlow objectFlow = single.getObjectFlow();
            if (objectFlow != ObjectFlow.NO_FLOW && !methodAnalysis.objectFlow.isSet()) {
                log(OBJECT_FLOW, "Set final object flow object for method {}: {}", methodInfo.distinguishingName(), objectFlow);
                objectFlow.finalize(methodAnalysis.objectFlow.getFirst());
                methodAnalysis.objectFlow.set(objectFlow);
            }
            if (single.isConstant()) {
                value = single;
                immutable = MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            } else {
                int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
                if (modified == Level.DELAY) return DELAYS;
                if (modified == Level.FALSE) {
                    InlineValue.Applicability applicability = applicability(single);
                    if (applicability != InlineValue.Applicability.NONE) {
                        value = new InlineValue(methodInfo, single, applicability);
                        immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    }
                }
            }
        }
        if (!methodAnalysis.objectFlow.isSet()) {
            log(OBJECT_FLOW, "No single object flow; we keep the object that's already there, for {}", methodInfo.distinguishingName());
            methodAnalysis.objectFlow.set(methodAnalysis.objectFlow.getFirst());
        }
        // fallback
        if (value == null) {
            immutable = MultiLevel.MUTABLE;
            value = UnknownValue.RETURN_VALUE;
        }
        boolean isConstant = value.isConstant();

        methodAnalysis.singleReturnValue.set(value);
        methodAnalysis.singleReturnValueImmutable.set(immutable);
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (isConstant) {
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            methodAnalysis.annotations.put(constantAnnotation, true);
        } else {
            methodAnalysis.annotations.put(e2.constant.get(), false);
        }
        methodAnalysis.setProperty(VariableProperty.CONSTANT, isConstant);

        log(CONSTANT, "Mark method {} as " + (isConstant ? "" : "NOT ") + "@Constant", methodInfo.fullyQualifiedName());
        return DONE;
    }

    private InlineValue.Applicability applicabilityField(FieldInfo fieldInfo) {
        List<FieldModifier> fieldModifiers = fieldInfo.fieldInspection.get().modifiers;
        for (FieldModifier fieldModifier : fieldModifiers) {
            if (fieldModifier == FieldModifier.PRIVATE) return InlineValue.Applicability.TYPE;
            if (fieldModifier == FieldModifier.PUBLIC) return InlineValue.Applicability.EVERYWHERE;
        }
        return InlineValue.Applicability.PACKAGE;
    }

    private InlineValue.Applicability applicability(Value value) {
        AtomicReference<InlineValue.Applicability> applicability = new AtomicReference<>(InlineValue.Applicability.EVERYWHERE);
        value.visit(v -> {
            if (v.isUnknown()) {
                applicability.set(InlineValue.Applicability.NONE);
            }
            VariableValue valueWithVariable;
            if ((valueWithVariable = v.asInstanceOf(VariableValue.class)) != null) {
                Variable variable = valueWithVariable.variable;
                if (variable.isLocal()) {
                    // TODO make a distinction between a local variable, and a local var outside a lambda
                    applicability.set(InlineValue.Applicability.NONE);
                } else if (variable instanceof FieldReference) {
                    InlineValue.Applicability fieldApplicability = applicabilityField(((FieldReference) variable).fieldInfo);
                    InlineValue.Applicability current = applicability.get();
                    applicability.set(current.mostRestrictive(fieldApplicability));
                }
            }
        });
        return applicability.get();
    }

    private AnalysisStatus propertiesOfReturnStatements(SharedState sharedState) {
        if (sharedState.methodLevelData.returnStatementSummaries.isEmpty()) return DONE;
        AnalysisStatus analysisStatus = DONE;
        for (VariableProperty variableProperty : VariableProperty.READ_FROM_RETURN_VALUE_PROPERTIES) {
            AnalysisStatus status = propertyOfReturnStatements(sharedState.methodLevelData(), variableProperty);
            analysisStatus = analysisStatus.combine(status);
        }
        return analysisStatus;
    }

    // IMMUTABLE, NOT_NULL, CONTAINER, IDENTITY, FLUENT
    // IMMUTABLE, NOT_NULL can still improve with respect to the static return type computed in methodAnalysis.getProperty()
    private AnalysisStatus propertyOfReturnStatements(MethodLevelData methodLevelData, VariableProperty variableProperty) {
        if (methodInfo.isVoid() || methodInfo.isConstructor) return DONE;
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (currentValue != Level.DELAY && variableProperty != VariableProperty.IMMUTABLE && variableProperty != VariableProperty.NOT_NULL)
            return DONE; // NOT FOR ME

        boolean delays = methodLevelData.returnStatementSummaries.stream().anyMatch(entry -> entry.getValue().isDelayed(variableProperty));
        if (delays) {
            log(DELAYED, "Return statement value not yet set");
            return DELAYS;
        }
        IntStream stream = methodLevelData.returnStatementSummaries.stream().mapToInt(entry -> entry.getValue().getProperty(variableProperty));
        int value = variableProperty == VariableProperty.SIZE ?
                safeMinimumForSize(messages, new Location(methodInfo), stream) :
                stream.min().orElse(Level.DELAY);

        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return DELAYS;
        }
        if (value <= currentValue) return DONE; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return DONE;
    }

    private AnalysisStatus computeSize(SharedState sharedState) {
        assert methodAnalysis.getProperty(VariableProperty.SIZE) == Level.DELAY;

        if (methodInfo.isConstructor) return DONE; // non-modifying constructor would be weird anyway; not for me
        MethodLevelData methodLevelData = sharedState.methodLevelData;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} because waiting for @Modified", methodInfo.distinguishingName());
            return DELAYS;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.returnType().hasSize(analyserContext.getPrimitives(), analyserContext)) {
                // non-modifying method that returns a type with @Size (like Collection, Map, ...)
                log(SIZE, "Return type {} of method {} has a size!", methodInfo.returnType().detailedString(), methodInfo.fullyQualifiedName());

                methodLevelData.returnStatementSummaries.stream().forEach(e -> log(DELAYED, "RSS: {} = {}", e.getKey(), e.getValue()));
                // then try @Size(min, equals)
                boolean delays = methodLevelData.returnStatementSummaries.stream().anyMatch(entry -> entry.getValue().isDelayed(VariableProperty.SIZE));
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for @Size on {}", methodInfo.distinguishingName());
                    return DELAYS;
                }
                IntStream stream = methodLevelData.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().getProperty(VariableProperty.SIZE));
                return writeSize(VariableProperty.SIZE, safeMinimumForSize(messages, new Location(methodInfo), stream));
            } else {
                log(SIZE, "Return type {} of method {} has no size", methodInfo.returnType().detailedString(), methodInfo.fullyQualifiedName());
            }

            // non-modifying method that defines @Size (size(), isEmpty())
            return writeSize(VariableProperty.SIZE, propagateSizeAnnotations(sharedState));
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        //if (methodInfo.typeInfo.hasSize()) {
        //   return sizeModifying(methodInfo, methodAnalysis); TODO

        //}
        return DONE;
    }


    private AnalysisStatus computeSizeCopy(SharedState sharedState) {
        assert methodAnalysis.getProperty(VariableProperty.SIZE_COPY) == Level.DELAY;
        if (methodInfo.isConstructor) return DONE; // non-modifying constructor would be weird anyway

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size(copy) on {} because waiting for @Modified", methodInfo.distinguishingName());
            return DELAYS;
        }
        if (modified == Level.FALSE) {
            if (methodInfo.returnType().hasSize(analyserContext.getPrimitives(), analyserContext)) {
                // first try @Size(copy ...)
                boolean delays = sharedState.methodLevelData.returnStatementSummaries
                        .stream().anyMatch(entry -> entry.getValue().isDelayed(VariableProperty.SIZE_COPY));
                if (delays) {
                    log(DELAYED, "Return statement value not yet set for SIZE_COPY on {}", methodInfo.distinguishingName());
                    return DELAYS;
                }
                IntStream stream = sharedState.methodLevelData.returnStatementSummaries.stream()
                        .mapToInt(entry -> entry.getValue().getProperty(VariableProperty.SIZE_COPY));
                int min = stream.min().orElse(Level.IS_A_SIZE);
                return writeSize(VariableProperty.SIZE_COPY, min);
            }
            // not for me
            return writeSize(VariableProperty.SIZE_COPY, Level.NOT_A_SIZE);
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        //if (methodInfo.typeInfo.hasSize()) {
        //   // TODO not implemented yet
        //}
        return DONE;
    }

    private AnalysisStatus writeSize(VariableProperty variableProperty, int value) {
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return DELAYS;
        }
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (value <= currentValue) return DONE; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(variableProperty, value);
        return DONE;
    }

    private int propagateSizeAnnotations(SharedState sharedState) {
        if (sharedState.methodLevelData.returnStatementSummaries.size() != 1) {
            return Level.NOT_A_SIZE; // TODO
        }
        TransferValue tv = sharedState.methodLevelData.returnStatementSummaries.stream().findFirst().orElseThrow().getValue();
        if (!tv.value.isSet()) {
            return Level.DELAY;
        }
        Value value = tv.value.get();
        ConstrainedNumericValue cnv;
        if (Primitives.isDiscrete(methodInfo.returnType()) && ((cnv = value.asInstanceOf(ConstrainedNumericValue.class)) != null)) {
            // very specific situation, we see if the return statement is a @Size method; if so, we propagate that info
            MethodValue methodValue;
            if ((methodValue = cnv.value.asInstanceOf(MethodValue.class)) != null) {
                MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod(analyserContext.getPrimitives(), analyserContext);
                if (methodValue.methodInfo == theSizeMethod && cnv.lowerBound >= 0 && cnv.upperBound == ConstrainedNumericValue.MAX) {
                    return Level.encodeSizeMin((int) cnv.lowerBound);
                }
            }
        } else if (Primitives.isBooleanOrBoxedBoolean(methodInfo.returnType())) {
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
                        MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod(analyserContext.getPrimitives(), analyserContext);
                        if (methodValue.methodInfo == theSizeMethod) {
                            return Level.encodeSizeEquals(intValue.value);
                        }
                    }
                }
            } else {
                GreaterThanZeroValue gzv;
                if ((gzv = value.asInstanceOf(GreaterThanZeroValue.class)) != null) {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration, ConditionManager.INITIAL);
                    GreaterThanZeroValue.XB xb = gzv.extract(evaluationContext);
                    ConstrainedNumericValue cnvXb;
                    if (!xb.lessThan && ((cnvXb = xb.x.asInstanceOf(ConstrainedNumericValue.class)) != null)) {
                        MethodValue methodValue;
                        if ((methodValue = cnvXb.value.asInstanceOf(MethodValue.class)) != null) {
                            MethodInfo theSizeMethod = methodValue.methodInfo.typeInfo.sizeMethod(analyserContext.getPrimitives(), analyserContext);
                            if (methodValue.methodInfo == theSizeMethod) {
                                return Level.encodeSizeMin((int) xb.b);
                            }
                        }
                    }
                }
            }
        }
        // if DELAY here for a simple method that returns a string, the whole thing remains on DELAY
        return Level.FALSE;
    }

    static int safeMinimumForSize(Messages messages, Location location, IntStream intStream) {
        int res = intStream.reduce(Integer.MAX_VALUE, (v1, v2) -> {
            if (Level.haveEquals(v1) && Level.haveEquals(v2) && v1 != v2) {
                messages.add(Message.newMessage(location, Message.POTENTIAL_SIZE_PROBLEM,
                        "Equal to " + Level.decodeSizeEquals(v1) + ", equal to " + Level.decodeSizeEquals(v2)));
            }
            return Math.min(v1, v2);
        });
        return res == Integer.MAX_VALUE ? Level.DELAY : Math.max(res, Level.IS_A_SIZE);
    }

    private AnalysisStatus methodIsModified(SharedState sharedState) {
        if (methodAnalysis.getProperty(VariableProperty.MODIFIED) != Level.DELAY) return DONE;
        MethodLevelData methodLevelData = sharedState.methodLevelData;

        // first step, check field assignments
        boolean fieldAssignments = methodLevelData.fieldSummaries.stream()
                .map(Map.Entry::getValue)
                .anyMatch(tv -> tv.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return DONE;
        }

        // if there are no field assignments, there may be modifying method calls

        // second step, check that linking has been computed
        if (!methodLevelData.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Method {}: Not deciding on @Modified yet, delaying because linking not computed",
                    methodInfo.distinguishingName());
            return DELAYS;
        }
        boolean isModified = methodLevelData.fieldSummaries.stream().map(Map.Entry::getValue)
                .anyMatch(tv -> tv.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (isModified && isLogEnabled(NOT_MODIFIED)) {
            List<String> fieldsWithContentModifications =
                    methodLevelData.fieldSummaries.stream()
                            .filter(e -> e.getValue().getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                            .map(e -> e.getKey().fullyQualifiedName()).collect(Collectors.toList());
            log(NOT_MODIFIED, "Method {} cannot be @NotModified: some fields have content modifications: {}",
                    methodInfo.fullyQualifiedName(), fieldsWithContentModifications);
        }
        if (!isModified) {
            boolean localMethodsCalled = methodLevelData.thisSummary.get().getProperty(VariableProperty.METHOD_CALLED) == Level.TRUE;
            // IMPORTANT: localMethodsCalled only works on "this"; it does not work for static methods (See IdentityChecks)
            if (localMethodsCalled) {
                int thisModified = methodLevelData.thisSummary.get().getProperty(VariableProperty.MODIFIED);

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

            if (!methodLevelData.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
                log(DELAYED, "Delaying modification on method {}, waiting for calls to undeclared functional interfaces",
                        methodInfo.distinguishingName());
                return DELAYS;
            }
            if (methodLevelData.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get()) {
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

        Optional<MethodInfo> someOtherMethodNotYetDecided = methodInfo.typeInfo.typeInspection.getPotentiallyRun()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(mi ->
                        !mi.methodAnalysis.get().methodLevelData().callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet() ||
                                (!mi.methodAnalysis.get().methodLevelData().callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get() &&
                                        (mi.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY ||
                                                mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext) == null ||
                                                mi.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT) == Level.DELAY)))
                .findFirst();
        if (someOtherMethodNotYetDecided.isPresent()) {
            log(DELAYED, "Delaying modification on method {} which calls an undeclared functional interface, because of {}",
                    methodInfo.distinguishingName(), someOtherMethodNotYetDecided.get().name);
            return null;
        }
        return methodInfo.typeInfo.typeInspection.getPotentiallyRun()
                .methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .anyMatch(mi -> mi.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE ||
                        !mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(analyserContext) &&
                                mi.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT) == Level.FALSE);
    }

    private AnalysisStatus methodIsIndependent(SharedState sharedState) {
        assert methodAnalysis.getProperty(VariableProperty.INDEPENDENT) == Level.DELAY;
        MethodLevelData methodLevelData = sharedState.methodLevelData;

        if (!methodInfo.isConstructor) {
            // we only compute @Independent/@Dependent on methods when the method is @NotModified
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return DELAYS;
            if (modified == Level.TRUE) {
                methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return DONE;
            }
        } // else: for constructors, we assume @Modified so that rule is not that useful

        if (!methodLevelData.variablesLinkedToFieldsAndParameters.isSet()) {
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
            List<ParameterInfo> parameters = new ArrayList<>(methodInfo.methodInspection.get().parameters);
            parameters.removeIf(pi -> pi.parameterizedType.typeInfo == methodInfo.typeInfo);

            boolean allLinkedVariablesSet = methodLevelData.fieldSummaries.stream().allMatch(e -> e.getValue().linkedVariables.isSet());
            if (!allLinkedVariablesSet) {
                log(DELAYED, "Delaying @Independent on {}, linked variables not yet known for all field references", methodInfo.distinguishingName());
                return DELAYS;
            }
            boolean supportDataSet = methodLevelData.fieldSummaries.stream()
                    .flatMap(e -> e.getValue().linkedVariables.get().stream())
                    .allMatch(v -> isImplicitlyImmutableDataTypeSet(v, analyserContext));
            if (!supportDataSet) {
                log(DELAYED, "Delaying @Independent on {}, support data not yet known for all field references", methodInfo.distinguishingName());
                return DELAYS;
            }

            parametersIndependentOfFields = methodLevelData.fieldSummaries.stream()
                    .peek(e -> {
                        if (!e.getValue().linkedVariables.isSet())
                            LOGGER.warn("Field {} has no linked variables set in {}", e.getKey().name, methodInfo.distinguishingName());
                    })
                    .flatMap(e -> e.getValue().linkedVariables.get().stream())
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
        if (methodInfo.isConstructor || methodInfo.isVoid()) return true;
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(methodInfo.typeInfo);
        if (typeAnalysis.getImplicitlyImmutableDataTypes() != null &&
                typeAnalysis.getImplicitlyImmutableDataTypes().contains(methodInfo.returnType())) {
            return true;
        }

        if (!methodLevelData.variablesLinkedToMethodResult.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                    methodInfo.fullyQualifiedName());
            return null;
        }
        // method does not return an implicitly immutable data type
        Set<Variable> variables = methodLevelData.variablesLinkedToMethodResult.get();
        boolean implicitlyImmutableSet = variables.stream().allMatch(v -> isImplicitlyImmutableDataTypeSet(v, analyserContext));
        if (!implicitlyImmutableSet) {
            log(DELAYED, "Delaying @Independent on {}, implicitly immutable status not known for all field references", methodInfo.distinguishingName());
            return null;
        }

        // TODO convert the variables into field analysers

        int e2ImmutableStatusOfFieldRefs = variables.stream()
                .filter(v -> isFieldNotOfImplicitlyImmutableType(v, analyserContext))
                .map(v -> analyserContext.getFieldAnalysers().get(((FieldReference) v).fieldInfo))
                .mapToInt(fa -> MultiLevel.value(fa.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE))
                .min().orElse(MultiLevel.EFFECTIVE);
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.DELAY) {
            log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                    variables.stream()
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
            if (dynamicE2ImmutableStatusOfReturnType == MultiLevel.DELAY) {
                log(DELAYED, "Have dynamic return type, no idea if E2Immutable: {}", methodInfo.distinguishingName());
                return null;
            }
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

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl implements EvaluationContext {

        protected EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
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
        public int getProperty(Value value, VariableProperty variableProperty) {
            return value.getProperty(this, variableProperty);
        }
    }
}
