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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.ContractMark;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodAnalysisImpl extends AnalysisImpl implements MethodAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalysisImpl.class);

    public final StatementAnalysis firstStatement;
    public final StatementAnalysis lastStatement;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final MethodInfo methodInfo;
    public final ObjectFlow objectFlow;
    public final Set<ObjectFlow> internalObjectFlows;
    public final List<Value> preconditionForMarkAndOnly;
    public final MarkAndOnly markAndOnly;
    public final boolean complainedAboutMissingStaticModifier;
    public final boolean complainedAboutApprovedPreconditions;
    public final Value precondition;
    public final Value singleReturnValue;
    public final Map<CompanionMethodName, CompanionAnalysis> companionAnalyses;

    private MethodAnalysisImpl(MethodInfo methodInfo,
                               StatementAnalysis firstStatement,
                               StatementAnalysis lastStatement,
                               List<ParameterAnalysis> parameterAnalyses,
                               Value singleReturnValue,
                               ObjectFlow objectFlow,
                               Set<ObjectFlow> internalObjectFlows,
                               List<Value> preconditionForMarkAndOnly,
                               MarkAndOnly markAndOnly,
                               boolean complainedAboutMissingStaticModifier,
                               boolean complainedAboutApprovedPreconditions,
                               Value precondition,
                               Map<VariableProperty, Integer> properties,
                               Map<AnnotationExpression, Boolean> annotations,
                               Map<CompanionMethodName, CompanionAnalysis> companionAnalyses) {
        super(properties, annotations);
        this.methodInfo = methodInfo;
        this.firstStatement = firstStatement;
        this.lastStatement = lastStatement;
        this.parameterAnalyses = parameterAnalyses;
        this.objectFlow = objectFlow;
        this.internalObjectFlows = internalObjectFlows;
        this.preconditionForMarkAndOnly = preconditionForMarkAndOnly;
        this.markAndOnly = markAndOnly;
        this.complainedAboutMissingStaticModifier = complainedAboutMissingStaticModifier;
        this.complainedAboutApprovedPreconditions = complainedAboutApprovedPreconditions;
        this.precondition = precondition;
        this.singleReturnValue = singleReturnValue;
        this.companionAnalyses = companionAnalyses;
    }

    @Override
    public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return companionAnalyses;
    }

    @Override
    public Value getSingleReturnValue() {
        return singleReturnValue;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider) {
        return overrides(analysisProvider, methodInfo, this);
    }

    @Override
    public StatementAnalysis getFirstStatement() {
        return firstStatement;
    }

    @Override
    public List<ParameterAnalysis> getParameterAnalyses() {
        return parameterAnalyses;
    }

    @Override
    public StatementAnalysis getLastStatement() {
        return lastStatement;
    }

    @Override
    public List<Value> getPreconditionForMarkAndOnly() {
        return preconditionForMarkAndOnly;
    }

    @Override
    public MarkAndOnly getMarkAndOnly() {
        return markAndOnly;
    }

    @Override
    public Set<ObjectFlow> getInternalObjectFlows() {
        return internalObjectFlows;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Boolean getComplainedAboutMissingStaticModifier() {
        return complainedAboutMissingStaticModifier;
    }

    @Override
    public Boolean getComplainedAboutApprovedPreconditions() {
        return complainedAboutApprovedPreconditions;
    }

    @Override
    public Value getPrecondition() {
        return precondition;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getMethodProperty(AnalysisProvider.DEFAULT_PROVIDER, methodInfo, variableProperty);
    }

    @Override
    public Location location() {
        return new Location(methodInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return methodInfo.typeInfo.typeInspection.get().annotationMode();
    }

    public static class Builder extends AbstractAnalysisBuilder implements MethodAnalysis {
        public final ParameterizedType returnType;
        public final MethodInfo methodInfo;
        private final SetOnce<StatementAnalysis> firstStatement = new SetOnce<>();
        public final List<ParameterAnalysis> parameterAnalyses;
        private final AnalysisProvider analysisProvider;

        // the value here (size will be one)
        public final SetOnce<List<Value>> preconditionForMarkAndOnly = new SetOnce<>();
        public final SetOnce<MarkAndOnly> markAndOnly = new SetOnce<>();

        public final SetOnce<Boolean> complainedAboutMissingStaticModifier = new SetOnce<>();
        public final SetOnce<Boolean> complainedAboutApprovedPreconditions = new SetOnce<>();

        public final SetOnce<Value> singleReturnValue = new SetOnce<>();
        public final SetOnce<Integer> singleReturnValueImmutable = new SetOnce<>();

        // ************* object flow

        public final SetOnce<Set<ObjectFlow>> internalObjectFlows = new SetOnce<>();

        public final FirstThen<ObjectFlow, ObjectFlow> objectFlow;

        // ************** PRECONDITION

        public final SetOnce<Value> precondition = new SetOnce<>();
        public final SetOnceMap<CompanionMethodName, CompanionAnalysis> companionAnalyses = new SetOnceMap<>();

        public final boolean isBeingAnalysed;

        @Override
        public boolean isBeingAnalysed() {
            return isBeingAnalysed;
        }

        public ObjectFlow getObjectFlow() {
            return objectFlow.isFirst() ? objectFlow.getFirst() : objectFlow.get();
        }

        @Override
        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        @Override
        public Boolean getComplainedAboutMissingStaticModifier() {
            return complainedAboutMissingStaticModifier.getOrElse(null);
        }

        @Override
        public Boolean getComplainedAboutApprovedPreconditions() {
            return complainedAboutApprovedPreconditions.getOrElse(null);
        }

        @Override
        public Value getPrecondition() {
            return precondition.getOrElse(null);
        }

        public Builder(boolean isBeingAnalysed,
                       Primitives primitives,
                       AnalysisProvider analysisProvider,
                       MethodInfo methodInfo,
                       List<ParameterAnalysis> parameterAnalyses) {
            super(primitives, methodInfo.name);
            this.isBeingAnalysed = isBeingAnalysed;
            this.parameterAnalyses = parameterAnalyses;
            this.methodInfo = methodInfo;
            this.returnType = methodInfo.returnType();
            this.analysisProvider = analysisProvider;
            if (methodInfo.isConstructor || methodInfo.isVoid()) {
                // we set a NO_FLOW, non-modifiable
                objectFlow = new FirstThen<>(ObjectFlow.NO_FLOW);
                objectFlow.set(ObjectFlow.NO_FLOW);
            } else {
                ObjectFlow initialObjectFlow = new ObjectFlow(new Location(methodInfo), returnType, Origin.INITIAL_METHOD_FLOW);
                objectFlow = new FirstThen<>(initialObjectFlow);
            }
        }

        @Override
        public Analysis build() {
            return new MethodAnalysisImpl(methodInfo,
                    firstStatement.getOrElse(null),
                    getLastStatement(),
                    ImmutableList.copyOf(parameterAnalyses.stream()
                            .map(parameterAnalysis -> parameterAnalysis instanceof ParameterAnalysisImpl.Builder builder ?
                                    (ParameterAnalysis) builder.build() : parameterAnalysis).collect(Collectors.toList())),
                    getSingleReturnValue(),
                    getObjectFlow(),
                    ImmutableSet.copyOf(internalObjectFlows.getOrElse(Set.of())),
                    ImmutableList.copyOf(preconditionForMarkAndOnly.getOrElse(List.of())),
                    markAndOnly.getOrElse(null),
                    complainedAboutMissingStaticModifier.getOrElse(false),
                    complainedAboutApprovedPreconditions.getOrElse(false),
                    precondition.getOrElse(UnknownValue.EMPTY),
                    properties.toImmutableMap(),
                    annotations.toImmutableMap(),
                    getCompanionAnalyses());
        }

        @Override
        public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
            return companionAnalyses.toImmutableMap();
        }

        @Override
        public Value getSingleReturnValue() {
            return singleReturnValue.getOrElse(null);
        }

        @Override
        public Location location() {
            return new Location(methodInfo);
        }

        @Override
        public AnnotationMode annotationMode() {
            return methodInfo.typeInfo.typeInspection.get().annotationMode();
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getMethodProperty(analysisProvider, methodInfo, variableProperty);
        }

        private int dynamicProperty(int formalImmutableProperty) {
            int immutableTypeAfterEventual = MultiLevel.eventual(formalImmutableProperty,
                    getObjectFlow().conditionsMetForEventual(analysisProvider, returnType));
            return Level.best(super.getProperty(VariableProperty.IMMUTABLE), immutableTypeAfterEventual);
        }

        private int formalProperty() {
            return returnType.getProperty(analysisProvider, VariableProperty.IMMUTABLE);
        }

        @Override
        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            int modified = getProperty(VariableProperty.MODIFIED);

            // @Precondition
            if (precondition.isSet()) {
                Value value = precondition.get();
                if (value != UnknownValue.EMPTY) {
                    AnnotationExpression ae = e2ImmuAnnotationExpressions.precondition
                            .copyWith(primitives, "value", value.toString());
                    annotations.put(ae, true);
                }
            }

            // @Dependent @Independent
            if (methodInfo.isConstructor || modified == Level.FALSE && allowIndependentOnMethod()) {
                int independent = getProperty(VariableProperty.INDEPENDENT);
                doIndependent(e2ImmuAnnotationExpressions, independent, methodInfo.typeInfo.isInterface());
            }

            if (methodInfo.isConstructor) return;

            // @NotModified, @Modified
            AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                    e2ImmuAnnotationExpressions.modified;
            annotations.put(ae, true);

            if (Primitives.isVoid(returnType)) return;

            // @Identity
            if (getProperty(VariableProperty.IDENTITY) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.identity, true);
            }

            // all other annotations cannot be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(returnType)) return;

            // @Fluent
            if (getProperty(VariableProperty.FLUENT) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.fluent, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions);

            // dynamic type annotations for functional interface types: @NotModified1
            doNotModified1(e2ImmuAnnotationExpressions);

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            int formallyImmutable = formalProperty();
            int dynamicallyImmutable = dynamicProperty(formallyImmutable);
            if (MultiLevel.isBetterImmutable(dynamicallyImmutable, formallyImmutable)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, dynamicallyImmutable, true);
            }
        }

        private boolean allowIndependentOnMethod() {
            return !Primitives.isVoid(returnType) && returnType.isImplicitlyOrAtLeastEventuallyE2Immutable(analysisProvider) != Boolean.TRUE;
        }

        // the name refers to the @Mark and @Only annotations. It is the data for this annotation.
        protected void writeMarkAndOnly(MarkAndOnly markAndOnly) {
            ContractMark contractMark = new ContractMark(markAndOnly.markLabel);
            preconditionForMarkAndOnly.set(List.of(contractMark));
            this.markAndOnly.set(markAndOnly);
        }

        @Override
        public Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider) {
            return overrides(analysisProvider, methodInfo, this);
        }

        @Override
        public StatementAnalysis getFirstStatement() {
            return firstStatement.getOrElse(null);
        }

        @Override
        public List<ParameterAnalysis> getParameterAnalyses() {
            return parameterAnalyses;
        }

        @Override
        public StatementAnalysis getLastStatement() {
            // we're not "caching" it during analysis; it may change (?) over iterations
            StatementAnalysis first = firstStatement.getOrElse(null);
            return first == null ? null : first.lastStatement();
        }

        @Override
        public List<Value> getPreconditionForMarkAndOnly() {
            return null;
        }

        @Override
        public MarkAndOnly getMarkAndOnly() {
            return null;
        }

        @Override
        public Set<ObjectFlow> getInternalObjectFlows() {
            return null;
        }

        public void setFirstStatement(StatementAnalysis firstStatement) {
            this.firstStatement.set(firstStatement);
        }
    }

    private static Set<MethodAnalysis> overrides(AnalysisProvider analysisProvider, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        try {
            return methodInfo.methodResolution.get().overrides().stream()
                    .map(mi -> mi == methodInfo ? methodAnalysis: analysisProvider.getMethodAnalysis(methodInfo))
                    .collect(Collectors.toSet());
        } catch (RuntimeException rte) {
            LOGGER.error("Cannot compute method analysis of {}", methodInfo.distinguishingName());
            throw rte;
        }
    }


}