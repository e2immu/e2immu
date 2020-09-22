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
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.isLogEnabled;
import static org.e2immu.analyser.util.Logger.log;

public class MethodAnalyser extends AbstractAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalyser.class);

    private final Messages messages = new Messages();

    public final MethodInfo methodInfo;
    public final MethodInspection methodInspection;
    public final boolean isSAM;
    public final MethodAnalysis methodAnalysis;

    public final List<ParameterAnalyser> parameterAnalysers;
    private Map<ParameterInfo, ParameterAnalyser> parameterAnalyserMap;
    private Map<FieldInfo, FieldAnalyser> fieldAnalyserMap;
    private List<FieldAnalyser> myFieldAnalysers;

    public List<ParameterAnalyser> getParameterAnalysers() {
        return parameterAnalysers;
    }

    public MethodAnalyser(MethodInfo methodInfo, boolean isSAM, Configuration configuration, PatternMatcher patternMatcher, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        super(e2ImmuAnnotationExpressions, patternMatcher, configuration);
        this.methodInfo = methodInfo;
        methodInspection = methodInfo.methodInspection.get();
        ImmutableList.Builder<ParameterAnalyser> parameterAnalysers = new ImmutableList.Builder<>();
        for (ParameterInfo parameterInfo : methodInspection.parameters) {
            parameterAnalysers.add(new ParameterAnalyser(parameterInfo, e2ImmuAnnotationExpressions));
        }
        this.parameterAnalysers = parameterAnalysers.build();
        methodAnalysis = new MethodAnalysis(methodInfo);
        this.isSAM = isSAM;
    }

    @Override
    public boolean isSAM() {
        return isSAM;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return methodInfo;
    }

    @Override
    public void initialize(List<Analyser> analysers, Map<ParameterInfo, ParameterAnalyser> parameterAnalysers,
                           Map<FieldInfo, FieldAnalyser> fieldAnalysers) {
        parameterAnalysers.values().forEach(parameterAnalyser -> parameterAnalyser.initialize(fieldAnalysers));
        this.parameterAnalyserMap = parameterAnalysers;
        this.fieldAnalyserMap = fieldAnalysers;

        ImmutableList.Builder<FieldAnalyser> myFieldAnalysers = new ImmutableList.Builder<>();
        analysers.forEach(analyser -> {
            if (analyser instanceof FieldAnalyser) {
                FieldAnalyser fieldAnalyser = (FieldAnalyser) analyser;
                if (fieldAnalyser.fieldInfo.owner == methodInfo.typeInfo) {
                    myFieldAnalysers.add(fieldAnalyser);
                }
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
        // before we check, we copy the properties into annotations
        methodInfo.methodAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

        log(ANALYSER, "Checking method {}", methodInfo.fullyQualifiedName());

        check(Independent.class, e2ImmuAnnotationExpressions.independent.get());

        if (!methodInfo.isConstructor) {
            if (!methodInfo.isVoid()) {
                check(NotNull.class, e2ImmuAnnotationExpressions.notNull.get());
                check(Fluent.class, e2ImmuAnnotationExpressions.fluent.get());
                check(Identity.class, e2ImmuAnnotationExpressions.identity.get());
                check(E1Immutable.class, e2ImmuAnnotationExpressions.e1Immutable.get());
                check(E1Container.class, e2ImmuAnnotationExpressions.e1Container.get());
                check(Container.class, e2ImmuAnnotationExpressions.container.get());
                check(E2Immutable.class, e2ImmuAnnotationExpressions.e2Immutable.get());
                check(E2Container.class, e2ImmuAnnotationExpressions.e2Container.get());
                check(BeforeMark.class, e2ImmuAnnotationExpressions.beforeMark.get());
                CheckConstant.checkConstantForMethods(messages, methodInfo);

                // checks for dynamic properties of functional interface types
                check(NotModified1.class, e2ImmuAnnotationExpressions.notModified1.get());
            }
            check(NotModified.class, e2ImmuAnnotationExpressions.notModified.get());

            // opposites
            check(Nullable.class, e2ImmuAnnotationExpressions.nullable.get());
            check(Modified.class, e2ImmuAnnotationExpressions.modified.get());
        }
        // opposites
        check(Dependent.class, e2ImmuAnnotationExpressions.dependent.get());

        CheckSize.checkSizeForMethods(messages, methodInfo);
        CheckPrecondition.checkPrecondition(messages, methodInfo);
        CheckOnly.checkOnly(messages, methodInfo);
        CheckOnly.checkMark(messages, methodInfo);

        parameterAnalysers.forEach(ParameterAnalyser::check);
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        methodInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(methodInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
    public boolean analyse(int iteration) {
        VariableProperties methodProperties = new VariableProperties(e2ImmuAnnotationExpressions,
                iteration, configuration, patternMatcher, methodInfo);
        List<Statement> statements = methodInfo.methodInspection.get().methodBody.get().structure.statements;
        if (!statements.isEmpty()) {
            boolean changes = false;
            log(ANALYSER, "Analysing method {}", methodInfo.fullyQualifiedName());

            if (!methodInfo.methodAnalysis.get().numberedStatements.isSet()) {
                List<NumberedStatement> numberedStatements = new LinkedList<>();
                Stack<Integer> indices = new Stack<>();
                CreateNumberedStatements.recursivelyCreateNumberedStatements(null, statements, indices, numberedStatements, true);
                methodInfo.methodAnalysis.get().numberedStatements.set(ImmutableList.copyOf(numberedStatements));
                changes = true;
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                methodProperties.createLocalVariableOrParameter(parameterInfo);
            }
            if (analyseMethod(methodProperties)) changes = true;

            for (MethodAnalyserVisitor methodAnalyserVisitor : configuration.debugConfiguration.afterMethodAnalyserVisitors) {
                methodAnalyserVisitor.visit(iteration, methodInfo);
            }

            return changes;
        }
        return false;
    }

    private boolean analyseMethod(EvaluationContext evaluationContext) {
        VariableProperties methodProperties = (VariableProperties) evaluationContext;
        try {
            boolean changes = false;
            List<NumberedStatement> numberedStatements = methodAnalysis.numberedStatements.get();

            // implicit null checks on local variables, (explicitly or implicitly)-final fields, and parameters
            if (computeVariablePropertiesOfMethod(numberedStatements, methodProperties))
                changes = true;

            if (obtainMostCompletePrecondition(numberedStatements, methodProperties)) {
                changes = true;
            }

            if (StaticModifier.computeStaticMethodCallsOnly(methodInfo, methodAnalysis, numberedStatements))
                changes = true;

            if (makeInternalObjectFlowsPermanent(methodProperties)) changes = true;
            if (!methodInfo.isConstructor) {
                if (!methodAnalysis.returnStatementSummaries.isEmpty()) {
                    // internal check
                    if (methodInfo.isVoid()) throw new UnsupportedOperationException();
                    if (propertiesOfReturnStatements(methodProperties))
                        changes = true;
                    // methodIsConstant makes use of methodIsNotNull, so order is important
                    if (methodIsConstant()) changes = true;
                }
                if (methodInfo.isStatic) {
                    if (methodCreatesObjectOfSelf(numberedStatements)) changes = true;
                }
                StaticModifier.detectMissingStaticModifier(messages, methodInfo, methodAnalysis);
                if (methodIsModified(methodProperties)) changes = true;

                // @Only, @Mark comes after modifications
                if (computeOnlyMarkPrepWork()) changes = true;
                if (computeOnlyMarkAnnotate()) changes = true;
            }
            if (methodIsIndependent(methodProperties)) changes = true;

            // size comes after modifications
            if (computeSize(methodProperties)) changes = true;
            if (computeSizeCopy(methodProperties)) changes = true;

            for (ParameterAnalyser parameterAnalyser : parameterAnalysers) {
                if (parameterAnalyser.analyse(methodProperties)) changes = true;
            }

            return changes;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", methodInfo.distinguishingName());
            throw rte;
        }
    }

    private boolean obtainMostCompletePrecondition(List<NumberedStatement> numberedStatements,
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

    private boolean makeInternalObjectFlowsPermanent(VariableProperties methodProperties) {
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
                if (!parameterAnalysis.objectFlow.isSet()) {
                    of.finalize(parameterAnalysis.objectFlow.getFirst());
                    parameterAnalysis.objectFlow.set(of);
                }
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

    private boolean computeOnlyMarkAnnotate() {
        if (methodAnalysis.markAndOnly.isSet()) return false; // done
        SetOnceMap<String, Value> approvedPreconditions = methodInfo.typeInfo.typeAnalysis.get().approvedPreconditions;
        if (approvedPreconditions.isEmpty()) {
            log(DELAYED, "No approved preconditions (yet) for {}", methodInfo.distinguishingName());
            return false;
        }
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Only, @Mark, don't know @Modified status in {}", methodInfo.distinguishingName());
            return false;
        }
        if (!methodAnalysis.preconditionForMarkAndOnly.isSet()) {
            log(DELAYED, "Waiting for preconditions to be resolved in {}", methodInfo.distinguishingName());
            return false;
        }
        List<Value> preconditions = methodAnalysis.preconditionForMarkAndOnly.get();

        boolean mark = false;
        Boolean after = null;
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
                Value negated = NegatedValue.negate(precondition);
                if (before.toString().equals(negated.toString())) {
                    if (after == null) after = true;
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

            if (modified == Level.FALSE) {
                log(MARK, "Method {} is @NotModified, so it'll be @Only rather than @Mark", methodInfo.distinguishingName());
            } else {
                // for the before methods, we need to check again if we were mark or only
                mark = mark || (!after && TypeAnalyser.assignmentIncompatibleWithPrecondition(precondition, methodAnalysis));
            }
        }
        if (after == null) {

            // this bit of code is here temporarily as a backup; it is the code in the type analyser that should
            // keep approvedPreconditions empty
            if (modified == Level.TRUE && !methodAnalysis.complainedAboutApprovedPreconditions.isSet()) {
                methodAnalysis.complainedAboutApprovedPreconditions.set(true);
                messages.add(Message.newMessage(new Location(methodInfo), Message.NO_APPROVED_PRECONDITIONS));
            }
            return false;
        }

        String jointMarkLabel = TypeAnalyser.labelOfPreconditionForMarkAndOnly(preconditions);
        MethodAnalysis.MarkAndOnly markAndOnly = new MethodAnalysis.MarkAndOnly(preconditions, jointMarkLabel, mark, after);
        methodAnalysis.markAndOnly.set(markAndOnly);
        log(MARK, "Marking {} with only data {}", methodInfo.distinguishingName(), markAndOnly);
        if (mark) {
            AnnotationExpression markAnnotation = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.mark.get().typeInfo,
                    List.of(new MemberValuePair("value", new StringConstant(jointMarkLabel))));
            methodAnalysis.annotations.put(markAnnotation, true);
            methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.only.get(), false);
        } else {
            AnnotationExpression onlyAnnotation = AnnotationExpression.fromAnalyserExpressions(e2ImmuAnnotationExpressions.only.get().typeInfo,
                    List.of(new MemberValuePair(after ? "after" : "before", new StringConstant(jointMarkLabel))));
            methodAnalysis.annotations.put(onlyAnnotation, true);
            methodAnalysis.annotations.put(e2ImmuAnnotationExpressions.mark.get(), false);
        }
        return true;
    }

    private boolean computeOnlyMarkPrepWork() {
        if (methodAnalysis.preconditionForMarkAndOnly.isSet()) return false; // already done
        TypeInfo typeInfo = methodInfo.typeInfo;
        while (true) {
            boolean haveNonFinalFields = myFieldAnalysers.stream().anyMatch(fa -> fa.fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE);
            if (haveNonFinalFields) {
                break;
            }
            ParameterizedType parentClass = typeInfo.typeInspection.getPotentiallyRun().parentClass;
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
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return true;
        }
        // at this point, the null and size checks on parameters have been removed.
        // we still need to remove other parameter components; what remains can be used for marking/only

        Value.FilterResult filterResult = precondition.filter(Value.FilterMode.ACCEPT, Value::isIndividualFieldCondition);
        if (filterResult.accepted.isEmpty()) {
            log(MARK, "No @Mark/@Only annotation in {}: found no individual field preconditions", methodInfo.distinguishingName());
            methodAnalysis.preconditionForMarkAndOnly.set(List.of());
            return true;
        }
        List<Value> preconditionParts = new ArrayList<>(filterResult.accepted.values());
        log(MARK, "Did prep work for @Only, @Mark, found precondition on variables {} in {}", precondition, filterResult.accepted.keySet(), methodInfo.distinguishingName());
        methodAnalysis.preconditionForMarkAndOnly.set(preconditionParts);
        return true;
    }

    // part of @UtilityClass computation in the type analyser
    private boolean methodCreatesObjectOfSelf(List<NumberedStatement> numberedStatements) {
        if (!methodAnalysis.createObjectOfSelf.isSet()) {
            boolean createSelf = numberedStatements.stream().flatMap(ns -> ns.statement.collect(NewObject.class).stream())
                    .anyMatch(no -> no.parameterizedType.typeInfo == methodInfo.typeInfo);
            log(UTILITY_CLASS, "Is {} a static non-constructor method that creates self? {}", methodInfo.fullyQualifiedName(), createSelf);
            methodAnalysis.createObjectOfSelf.set(createSelf);
            return true;
        }
        return false;
    }

    // singleReturnValue is associated with @Constant; to be able to grab the actual Value object
    // but we cannot assign this value too early: first, there should be no evaluation anymore with NO_VALUES in them
    private boolean methodIsConstant() {
        if (methodAnalysis.singleReturnValue.isSet()) return false;

        boolean allReturnValuesSet = methodAnalysis.returnStatementSummaries.stream().allMatch(e -> e.getValue().value.isSet());
        if (!allReturnValuesSet) {
            log(DELAYED, "Not all return values have been set yet for {}, delaying", methodInfo.distinguishingName());
            return false;
        }
        List<TransferValue> remainingReturnStatementSummaries = methodAnalysis.returnStatementSummaries.stream().map(Map.Entry::getValue).collect(Collectors.toList());

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
            } else {
                int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
                if (modified == Level.DELAY) return false;
                if (modified == Level.FALSE) {
                    InlineValue.Applicability applicability = applicability(single);
                    if (applicability != InlineValue.Applicability.NONE) {
                        value = new InlineValue(methodInfo, single, applicability);
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
            ValueWithVariable valueWithVariable;
            if ((valueWithVariable = v.asInstanceOf(ValueWithVariable.class)) != null) {
                Variable variable = valueWithVariable.variable;
                if (variable instanceof LocalVariableReference) {
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

    private boolean propertiesOfReturnStatements(EvaluationContext evaluationContext) {
        boolean changes = false;
        for (VariableProperty variableProperty : VariableProperty.RETURN_VALUE_PROPERTIES_IN_METHOD_ANALYSER) {
            if (propertyOfReturnStatements(variableProperty, evaluationContext))
                changes = true;
        }
        return changes;
    }

    // IMMUTABLE, NOT_NULL, CONTAINER, IDENTITY, FLUENT
    // IMMUTABLE, NOT_NULL can still improve with respect to the static return type computed in methodAnalysis.getProperty()
    private boolean propertyOfReturnStatements(VariableProperty variableProperty, EvaluationContext evaluationContext) {
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
                safeMinimumForSize(messages, new Location(methodInfo), stream) :
                stream.min().orElse(Level.DELAY);

        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(evaluationContext, variableProperty, value);
        return true;
    }

    private boolean computeSize(EvaluationContext evaluationContext) {
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
                return writeSize(VariableProperty.SIZE, safeMinimumForSize(messages, new Location(methodInfo), stream), evaluationContext);
            }

            // non-modifying method that defines @Size (size(), isEmpty())
            return writeSize(VariableProperty.SIZE,
                    propagateSizeAnnotations(), evaluationContext);
        }

        // modifying method
        // we can write size copy (if there is a modification that copies a map) or size equals, min if the modification is of that nature
        // the size copy will need to be written on the PARAMETER from which the copying has taken place
        if (methodInfo.typeInfo.hasSize()) {
            return sizeModifying(methodInfo, methodAnalysis);

        }
        return false;
    }


    private boolean computeSizeCopy(EvaluationContext evaluationContext) {
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
                return writeSize(VariableProperty.SIZE_COPY, min, evaluationContext);
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

    private boolean writeSize(VariableProperty variableProperty,
                              int value,
                              EvaluationContext evaluationContext) {
        if (value == Level.DELAY) {
            log(DELAYED, "Not deciding on {} yet for method {}", variableProperty, methodInfo.distinguishingName());
            return false;
        }
        int currentValue = methodAnalysis.getProperty(variableProperty);
        if (value <= currentValue) return false; // not improving.
        log(NOT_NULL, "Set value of {} to {} for method {}", variableProperty, value, methodInfo.distinguishingName());
        methodAnalysis.setProperty(evaluationContext, variableProperty, value);
        return true;
    }

    private int propagateSizeAnnotations() {
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

    private boolean methodIsModified(EvaluationContext evaluationContext) {
        if (methodAnalysis.getProperty(VariableProperty.MODIFIED) != Level.DELAY) return false;

        // first step, check field assignments
        boolean fieldAssignments = methodAnalysis.fieldSummaries.stream()
                .map(Map.Entry::getValue)
                .anyMatch(tv -> tv.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE);
        if (fieldAssignments) {
            log(NOT_MODIFIED, "Method {} is @Modified: fields are being assigned", methodInfo.distinguishingName());
            methodAnalysis.setProperty(evaluationContext, VariableProperty.MODIFIED, Level.TRUE);
            return true;
        }

        // if there are no field assignments, there may be modifying method calls

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

        if (!isModified) {
            // if there are no modifying method calls, we still may have a modifying method
            // this will be due to calling undeclared SAMs, or calling non-modifying methods in a circular type situation
            // (A.nonModifying() calls B.modifying() on a parameter (NOT a field, so nonModifying is just that) which itself calls A.modifying()
            // NOTE that in this situation we cannot have a container, as we require a modifying! (TODO check this statement is correct)

            if (!methodAnalysis.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
                log(DELAYED, "Delaying modification on method {}, waiting for calls to undeclared functional interfaces",
                        methodInfo.distinguishingName());
                return false;
            }
            if (methodAnalysis.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get()) {
                Boolean haveModifying = findOtherModifyingElements(methodInfo);
                if (haveModifying == null) return false;
                isModified = haveModifying;
            }
        }
        if (!isModified) {
            OptionalInt maxModified = methodAnalysis.copyModificationStatusFrom.stream().mapToInt(mi -> mi.getKey().methodAnalysis.get().getProperty(VariableProperty.MODIFIED)).max();
            if (maxModified.isPresent()) {
                int mm = maxModified.getAsInt();
                if (mm == Level.DELAY) {
                    log(DELAYED, "Delaying modification on method {}, waiting to copy", methodInfo.distinguishingName());
                    return false;
                }
                isModified = maxModified.getAsInt() == Level.TRUE;
            }
        }
        // (we could call non-@NM methods on parameters or local variables, but that does not influence this annotation)
        methodAnalysis.setProperty(evaluationContext, VariableProperty.MODIFIED, isModified ? Level.TRUE : Level.FALSE);
        return true;
    }

    private Boolean findOtherModifyingElements(MethodInfo methodInfo) {
        boolean nonPrivateFields = myFieldAnalysers.stream()
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
                        !mi.methodAnalysis.get().callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet() ||
                                (!mi.methodAnalysis.get().callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.get() &&
                                        (mi.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY ||
                                                mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(mi.typeInfo) == null ||
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
                        !mi.returnType().isImplicitlyOrAtLeastEventuallyE2Immutable(mi.typeInfo) &&
                                mi.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT) == Level.FALSE);
    }

    private boolean methodIsIndependent(VariableProperties methodProperties) {
        if (methodAnalysis.getProperty(VariableProperty.INDEPENDENT) != Level.DELAY) {
            return false; // already computed
        }
        if (!methodInfo.isConstructor) {
            // we only compute @Independent/@Dependent on methods when the method is @NotModified
            int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
            if (modified == Level.DELAY) return false; // wait
            if (modified == Level.TRUE) {
                methodAnalysis.setProperty(methodProperties, VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return true;
            }
        } // else: for constructors, we assume @Modified so that rule is not that useful

        if (!methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, links not yet computed", methodInfo.fullyQualifiedName());
            return false;
        }

        // PART 1: check the return object, if it is there

        // support data types are not set for types that have not been defined; there, we rely on annotations
        Boolean returnObjectIsIndependent = independenceStatusOfReturnType(methodInfo, methodAnalysis, methodProperties);
        if (returnObjectIsIndependent == null) {
            return false; // delay
        }

        // CONSTRUCTOR ...
        boolean parametersIndependentOfFields;
        if (methodInfo.isConstructor) {
            // TODO check ExplicitConstructorInvocations

            // PART 2: check parameters, but remove those that are recursively of my own type
            List<ParameterInfo> parameters = new ArrayList<>(methodInfo.methodInspection.get().parameters);
            parameters.removeIf(pi -> pi.parameterizedType.typeInfo == methodInfo.typeInfo);

            boolean allLinkedVariablesSet = methodAnalysis.fieldSummaries.stream().allMatch(e -> e.getValue().linkedVariables.isSet());
            if (!allLinkedVariablesSet) {
                log(DELAYED, "Delaying @Independent on {}, linked variables not yet known for all field references", methodInfo.distinguishingName());
                return false;// DELAY
            }
            boolean supportDataSet = methodAnalysis.fieldSummaries.stream()
                    .flatMap(e -> e.getValue().linkedVariables.get().stream())
                    .allMatch(MethodAnalyser::isImplicitlyImmutableDataTypeSet);
            if (!supportDataSet) {
                log(DELAYED, "Delaying @Independent on {}, support data not yet known for all field references", methodInfo.distinguishingName());
                return false;
            }

            parametersIndependentOfFields = methodAnalysis.fieldSummaries.stream()
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
        methodAnalysis.setProperty(methodProperties, VariableProperty.INDEPENDENT, independent ? MultiLevel.EFFECTIVE : MultiLevel.FALSE);
        log(INDEPENDENT, "Mark method/constructor {} " + (independent ? "" : "not ") + "@Independent",
                methodInfo.fullyQualifiedName());
        return true;
    }

    private Boolean independenceStatusOfReturnType(MethodInfo methodInfo, MethodAnalysis methodAnalysis, VariableProperties methodProperties) {
        if (methodInfo.isConstructor || methodInfo.isVoid() ||
                methodInfo.typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.isSet() &&
                        methodInfo.typeInfo.typeAnalysis.get().implicitlyImmutableDataTypes.get().contains(methodInfo.returnType())) {
            return true;
        }

        if (!methodAnalysis.variablesLinkedToMethodResult.isSet()) {
            log(DELAYED, "Delaying @Independent on {}, variables linked to method result not computed",
                    methodInfo.fullyQualifiedName());
            return null;
        }
        // method does not return an implicitly immutable data type
        Set<Variable> variables = methodAnalysis.variablesLinkedToMethodResult.get();
        boolean implicitlyImmutableSet = variables.stream().allMatch(MethodAnalyser::isImplicitlyImmutableDataTypeSet);
        if (!implicitlyImmutableSet) {
            log(DELAYED, "Delaying @Independent on {}, implicitly immutable status not known for all field references", methodInfo.distinguishingName());
            return null;
        }

        // TODO convert the variables into field analysers
        
        int e2ImmutableStatusOfFieldRefs = variables.stream()
                .filter(MethodAnalyser::isFieldNotOfImplicitlyImmutableType)
                .map(v -> fieldAnalyserMap.get(((FieldReference) v).fieldInfo))
                .mapToInt(fa -> MultiLevel.value(fa.fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE))
                .min().orElse(MultiLevel.EFFECTIVE);
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.DELAY) {
            log(DELAYED, "Have a dependency on a field whose E2Immutable status is not known: {}",
                    variables.stream()
                            .filter(v -> isFieldNotOfImplicitlyImmutableType(v) &&
                                    MultiLevel.value(fieldAnalyserMap.get(((FieldReference) v).fieldInfo).fieldAnalysis.getProperty(VariableProperty.IMMUTABLE),
                                            MultiLevel.E2IMMUTABLE) == MultiLevel.DELAY)
                            .map(Variable::detailedString)
                            .collect(Collectors.joining(", ")));
            return null;
        }
        if (e2ImmutableStatusOfFieldRefs == MultiLevel.EFFECTIVE) return true;

        int formalE2ImmutableStatusOfReturnType = MultiLevel.value(methodInfo.returnType().getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (formalE2ImmutableStatusOfReturnType == MultiLevel.DELAY) {
            log(DELAYED, "Have formal return type, no idea if E2Immutable: {}", methodInfo.distinguishingName());
            return null;
        }
        if (formalE2ImmutableStatusOfReturnType >= MultiLevel.EVENTUAL) {
            log(INDEPENDENT, "Method {} is independent, formal return type is E2Immutable", methodInfo.distinguishingName());
            return true;
        }

        if (methodAnalysis.singleReturnValue.isSet()) {
            int imm = methodAnalysis.singleReturnValue.get().getProperty(methodProperties, VariableProperty.IMMUTABLE);
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

    public static boolean isImplicitlyImmutableDataTypeSet(Variable v) {
        return !(v instanceof FieldReference) || ((FieldReference) v).fieldInfo.fieldAnalysis.get().isOfImplicitlyImmutableDataType.isSet();
    }

    public static boolean isFieldNotOfImplicitlyImmutableType(Variable variable) {
        if (!(variable instanceof FieldReference)) return false;
        return !((FieldReference) variable).fieldInfo.fieldAnalysis.get().isOfImplicitlyImmutableDataType.get();
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(messages.getMessageStream(), parameterAnalysers.stream().flatMap(ParameterAnalyser::getMessageStream));
    }

    public boolean computeVariablePropertiesOfMethod(List<NumberedStatement> statements, VariableProperties methodProperties) {
        boolean changes = false;
        try {
            StatementAnalyser statementAnalyser = new StatementAnalyser(methodInfo);
            NumberedStatement startStatement = statements.get(0);
            if (statementAnalyser.computeVariablePropertiesOfBlock(startStatement, methodProperties)) changes = true;
            messages.addAll(statementAnalyser.getMessageStream());
            // this method computes, ONLY THE FIRST TIME, the values for READ, ASSIGNED, METHOD_CALLED on fields and this
            if (copyFieldAndThisProperties(methodProperties)) changes = true;

            // this one can be delayed, it copies the field assignment values
            if (copyFieldAssignmentValue(methodProperties)) changes = true;

            // SIZE, NOT_NULL into fieldSummaries
            if (copyContextProperties(methodProperties)) changes = true;

            // this method computes, unless delayed, the values for
            // - linksComputed
            // - variablesLinkedToFieldsAndParameters
            // - fieldsLinkedToFieldsAndVariables
            if (establishLinks(methodProperties)) changes = true;
            if (!methodInfo.isConstructor && updateVariablesLinkedToMethodResult())
                changes = true;

            if (computeContentModifications(methodProperties)) changes = true;

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

    private boolean updateVariablesLinkedToMethodResult() {

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
                FieldAnalysis fieldAnalysis = ((FieldReference) variable).fieldInfo.fieldAnalysis.get();
                if (!fieldAnalysis.variablesLinkedToMe.isSet()) {
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

    private boolean establishLinks(VariableProperties methodProperties) {
        if (methodAnalysis.variablesLinkedToFieldsAndParameters.isSet()) return false;

        // final fields need to have a value set; all the others act as local variables
        boolean someVariablesHaveNotBeenEvaluated = methodProperties.variableProperties().stream()
                .anyMatch(av -> av.getCurrentValue() == UnknownValue.NO_VALUE);
        if (someVariablesHaveNotBeenEvaluated) {
            log(DELAYED, "Some variables have not yet been evaluated -- delaying establishing links");
            return false;
        }
        if (methodProperties.isDelaysInDependencyGraph()) {
            log(DELAYED, "Dependency graph suffers delays -- delaying establishing links");
            return false;
        }
        boolean allFieldsFinalDetermined = methodInfo.typeInfo.typeInspection.getPotentiallyRun().fields.stream().allMatch(fieldInfo ->
                fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL) != Level.DELAY);
        if (!allFieldsFinalDetermined) {
            log(DELAYED, "Delay, we don't know about final values for some fields");
            return false;
        }
        AtomicBoolean changes = new AtomicBoolean();
        Map<Variable, Set<Variable>> variablesLinkedToFieldsAndParameters = new HashMap<>();

        methodProperties.dependencyGraph.visit((variable, dependencies) -> {
            Set<Variable> fieldAndParameterDependencies = new HashSet<>(methodProperties.dependencyGraph.dependencies(variable));
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

    private boolean computeContentModifications(VariableProperties methodProperties) {
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
                            fieldModified = Level.FALSE;
                        } else fieldModified = summary;
                        if (fieldModified == Level.DELAY) {
                            log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.detailedString(), methodInfo.distinguishingName());
                        } else {
                            log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
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
                                parameterAnalysis.setProperty(methodProperties, VariableProperty.MODIFIED, summary);
                                changes = true;
                            }
                        }
                    }
                }
            }
        }
        return changes;
    }

    private static int summarizeModification(VariableProperties methodProperties, Set<Variable> linkedVariables) {
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
     * @param methodProperties context
     * @return if any change happened to methodAnalysis
     */
    private boolean copyFieldAndThisProperties(VariableProperties methodProperties) {
        boolean changes = false;
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
                int methodDelay = aboutVariable.getProperty(VariableProperty.METHOD_DELAY);
                int methodCalled = aboutVariable.getProperty(VariableProperty.METHOD_CALLED);

                if (methodDelay != Level.TRUE && methodCalled == Level.TRUE) {
                    int modified = aboutVariable.getProperty(VariableProperty.MODIFIED);
                    TransferValue tv = methodAnalysis.thisSummary.get();
                    tv.properties.put(VariableProperty.MODIFIED, modified);
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

    private boolean copyFieldAssignmentValue(VariableProperties methodProperties) {
        boolean changes = false;
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            if (variable instanceof FieldReference && aboutVariable.getProperty(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE) {
                FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);
                Value value = aboutVariable.getCurrentValue();
                if (value != UnknownValue.NO_VALUE && !tv.value.isSet()) {
                    changes = true;
                    tv.value.set(value);
                }
                // the values of IMMUTABLE, CONTAINER, NOT_NULL, SIZE will be obtained from the value, they need not copying.
                Value stateOnAssignment = aboutVariable.getStateOnAssignment();
                if (stateOnAssignment != UnknownValue.NO_VALUE && stateOnAssignment != UnknownValue.EMPTY && !tv.stateOnAssignment.isSet()) {
                    tv.stateOnAssignment.set(stateOnAssignment);
                }
            }
        }
        return changes;
    }

    // a DELAY should only be possible for good reasons
    // context can generally only be delayed when there is a method delay

    private boolean copyContextProperties(VariableProperties methodProperties) {
        boolean changes = false;
        boolean anyDelay = false;
        for (AboutVariable aboutVariable : methodProperties.variableProperties()) {
            Variable variable = aboutVariable.variable;
            int methodDelay = aboutVariable.getProperty(VariableProperty.METHOD_DELAY);
            boolean haveDelay = methodDelay == Level.TRUE || aboutVariable.getCurrentValue() == UnknownValue.NO_VALUE;
            if (haveDelay) anyDelay = true;
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
                if (currentDelayResolved == Level.FALSE && !haveDelay) {
                    log(DELAYED, "Delays on {} have now been resolved", aboutVariable.name);
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.TRUE);
                }
                if (currentDelayResolved == Level.DELAY && haveDelay) {
                    log(DELAYED, "Marking that delays need resolving on {}", aboutVariable.name);
                    tv.properties.put(VariableProperty.METHOD_DELAY_RESOLVED, Level.FALSE);
                }
            } else if (variable instanceof ParameterInfo) {
                ParameterInfo parameterInfo = (ParameterInfo) variable;

                if (parameterInfo.parameterizedType.hasSize()) {
                    int size = aboutVariable.getProperty(VariableProperty.SIZE);
                    if (size == Level.DELAY && !haveDelay) {
                        // we could not find anything related to size, let's advertise that
                        int sizeInParam = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.SIZE);
                        if (sizeInParam == Level.DELAY) {
                            parameterInfo.parameterAnalysis.get().setProperty(methodProperties, VariableProperty.SIZE, Level.IS_A_SIZE);
                        }
                    }
                }
            }
        }
        if (!anyDelay && !methodAnalysis.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            methodAnalysis.callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(false);
        }
        return changes;
    }
}
