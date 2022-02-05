/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.CreatePreconditionCompanion;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.ContractMark;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.UnknownExpression;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class MethodAnalysisImpl extends AnalysisImpl implements MethodAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalysisImpl.class);

    public final StatementAnalysis firstStatement;
    public final StatementAnalysis lastStatement;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final MethodInfo methodInfo;
    public final Precondition preconditionForEventual;
    public final Eventual eventual;
    public final Precondition precondition;
    public final Expression singleReturnValue;
    public final Map<CompanionMethodName, CompanionAnalysis> companionAnalyses;
    public final Map<CompanionMethodName, MethodInfo> computedCompanions;
    public final AnalysisMode analysisMode;

    private MethodAnalysisImpl(MethodInfo methodInfo,
                               StatementAnalysis firstStatement,
                               StatementAnalysis lastStatement,
                               List<ParameterAnalysis> parameterAnalyses,
                               Expression singleReturnValue,
                               Precondition preconditionForEventual,
                               Eventual eventual,
                               Precondition precondition,
                               AnalysisMode analysisMode,
                               Map<Property, DV> properties,
                               Map<AnnotationExpression, AnnotationCheck> annotations,
                               Map<CompanionMethodName, CompanionAnalysis> companionAnalyses,
                               Map<CompanionMethodName, MethodInfo> computedCompanions) {
        super(properties, annotations);
        this.methodInfo = methodInfo;
        this.firstStatement = firstStatement;
        this.lastStatement = lastStatement;
        this.parameterAnalyses = parameterAnalyses;
        this.preconditionForEventual = preconditionForEventual;
        this.eventual = eventual;
        this.precondition = Objects.requireNonNull(precondition);
        this.singleReturnValue = Objects.requireNonNull(singleReturnValue);
        this.companionAnalyses = companionAnalyses;
        this.computedCompanions = computedCompanions;
        this.analysisMode = analysisMode;
    }

    @Override
    public AnalysisMode analysisMode() {
        return analysisMode;
    }

    @Override
    public Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
        return computedCompanions;
    }

    @Override
    public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return companionAnalyses;
    }

    @Override
    public Expression getSingleReturnValue() {
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
    public Precondition getPreconditionForEventual() {
        return preconditionForEventual;
    }

    @Override
    public Eventual getEventual() {
        return eventual;
    }

    @Override
    public boolean eventualIsSet() {
        return eventual != null;
    }

    @Override
    public CausesOfDelay eventualStatus() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public CausesOfDelay preconditionStatus() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public Precondition getPrecondition() {
        return precondition;
    }

    @Override
    public DV getProperty(Property property) {
        return getMethodProperty(property);
    }

    @Override
    public Location location() {
        return methodInfo.newLocation();
    }

    public static class Builder extends AbstractAnalysisBuilder implements MethodAnalysis {
        public final ParameterizedType returnType;
        public final MethodInfo methodInfo;
        private final SetOnce<StatementAnalysis> firstStatement = new SetOnce<>();
        public final List<ParameterAnalysis> parameterAnalyses;
        private final AnalysisProvider analysisProvider;
        private final InspectionProvider inspectionProvider;

        private final EventuallyFinal<Precondition> preconditionForEventual = new EventuallyFinal<>();
        private final EventuallyFinal<Eventual> eventual = new EventuallyFinal<>();
        public final EventuallyFinal<Expression> singleReturnValue = new EventuallyFinal<>();

        // ************** PRECONDITION

        public final EventuallyFinal<Precondition> precondition = new EventuallyFinal<>();
        public final SetOnceMap<CompanionMethodName, CompanionAnalysis> companionAnalyses = new SetOnceMap<>();

        public final SetOnceMap<CompanionMethodName, MethodInfo> computedCompanions = new SetOnceMap<>();

        public final AnalysisMode analysisMode;

        @Override
        protected String markLabelFromType() {
            return analysisProvider.getTypeAnalysis(methodInfo.typeInfo).markLabel();
        }

        @Override
        public AnalysisMode analysisMode() {
            return analysisMode;
        }

        @Override
        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        @Override
        public Precondition getPrecondition() {
            return precondition.get();
        }

        @Override
        public boolean eventualIsSet() {
            return eventual.isFinal();
        }

        public void setPreconditionForEventual(Precondition precondition) {
            assert precondition != null;
            if (precondition.isDelayed()) {
                preconditionForEventual.setVariable(precondition);
            } else {
                preconditionForEventual.setFinal(precondition);
            }
        }

        @Override
        public CausesOfDelay eventualStatus() {
            return eventual.get().causesOfDelay();
        }

        @Override
        public CausesOfDelay preconditionStatus() {
            CausesOfDelay causes = precondition.get().expression().causesOfDelay();
            assert causes.isDone() || precondition.isVariable();
            return causes;
        }

        public Builder(AnalysisMode analysisMode,
                       Primitives primitives,
                       AnalysisProvider analysisProvider,
                       InspectionProvider inspectionProvider,
                       MethodInfo methodInfo,
                       List<ParameterAnalysis> parameterAnalyses) {
            super(primitives, methodInfo.name);
            this.inspectionProvider = inspectionProvider;
            this.analysisMode = analysisMode;
            this.parameterAnalyses = parameterAnalyses;
            this.methodInfo = methodInfo;
            this.returnType = methodInfo.returnType();
            this.analysisProvider = analysisProvider;
            precondition.setVariable(Precondition.empty(primitives));
            Expression delayedPreconditionForEventual = DelayedExpression.forPrecondition(primitives, initialDelay(methodInfo));
            preconditionForEventual.setVariable(Precondition.forDelayed(delayedPreconditionForEventual));
            eventual.setVariable(MethodAnalysis.delayedEventual(initialDelay(methodInfo)));
            if (!methodInfo.hasReturnValue()) {
                UnknownExpression u = UnknownExpression.forNoReturnValue(methodInfo.identifier,
                        primitives.voidParameterizedType());
                singleReturnValue.setFinal(u);
            } else {
                // same as in MethodAnalyserImpl, which we don't have access to here
                DelayedExpression de = DelayedExpression.forMethod(methodInfo, methodInfo.returnType(), LinkedVariables.EMPTY,
                        methodInfo.delay(CauseOfDelay.Cause.SINGLE_RETURN_VALUE));
                singleReturnValue.setVariable(de);
            }
        }

        private static CausesOfDelay initialDelay(MethodInfo methodInfo) {
            return methodInfo.delay(CauseOfDelay.Cause.INITIAL_VALUE);
        }

        @Override
        public Analysis build() {
            return new MethodAnalysisImpl(methodInfo,
                    firstStatement.getOrDefaultNull(),
                    getLastStatement(),
                    List.copyOf(parameterAnalyses.stream()
                            .map(parameterAnalysis -> parameterAnalysis instanceof ParameterAnalysisImpl.Builder builder ?
                                    (ParameterAnalysis) builder.build() : parameterAnalysis).collect(Collectors.toList())),
                    getSingleReturnValue(),
                    preconditionForEventual.get(),
                    eventual.get(),
                    precondition.isFinal() ? precondition.get() : Precondition.empty(primitives),
                    analysisMode(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(),
                    getCompanionAnalyses(),
                    getComputedCompanions());
        }

        @Override
        public Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
            return computedCompanions.toImmutableMap();
        }

        @Override
        public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
            return companionAnalyses.toImmutableMap();
        }

        @Override
        public Expression getSingleReturnValue() {
            return singleReturnValue.get();
        }

        @Override
        public Location location() {
            return methodInfo.newLocation();
        }

        @Override
        public DV getProperty(Property property) {
            return getMethodProperty(property);
        }

        public void transferPropertiesToAnnotations(AnalyserContext analyserContext, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            DV modified = getProperty(Property.MODIFIED_METHOD);

            // @Precondition
            if (precondition.isFinal()) {
                Precondition pc = precondition.get();
                if (!(pc.expression() instanceof BooleanConstant)) {
                    // generate a companion method, but only when the precondition is non-trivial
                    new CreatePreconditionCompanion(analyserContext, analyserContext)
                            .addPreconditionCompanion(methodInfo, this, pc.expression());
                }
            }

            if (methodInfo.isConstructor) return;

            // @NotModified, @Modified
            AnnotationExpression ae = modified.valueIsFalse() ? e2ImmuAnnotationExpressions.notModified :
                    e2ImmuAnnotationExpressions.modified;
            annotations.put(ae, true);

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            DV formallyImmutable = analysisProvider.getProperty(returnType, Property.IMMUTABLE);
            DV dynamicallyImmutable = getProperty(Property.IMMUTABLE);
            DV formallyContainer = analysisProvider.getProperty(returnType, Property.CONTAINER);
            DV dynamicallyContainer = getProperty(Property.CONTAINER);
            if (dynamicallyImmutable.gt(formallyImmutable) || dynamicallyContainer.gt(formallyContainer)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, dynamicallyImmutable, dynamicallyContainer, true);
            }

            if (returnType.isVoidOrJavaLangVoid()) return;

            // @Identity
            if (getProperty(Property.IDENTITY).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.identity, true);
            }

            // all other annotations cannot be added to primitives
            if (returnType.isPrimitiveExcludingVoid()) return;

            // @Fluent
            if (getProperty(Property.FLUENT).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.fluent, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(Property.NOT_NULL_EXPRESSION));

            // @Dependent @Independent
            DV independent = getProperty(Property.INDEPENDENT);
            DV formallyIndependent = analyserContext.defaultIndependent(methodInfo.returnType());
            doIndependent(e2ImmuAnnotationExpressions, independent, formallyIndependent, dynamicallyImmutable);
        }

        protected void writeEventual(Eventual eventual) {
            ContractMark contractMark = new ContractMark(eventual.fields());
            preconditionForEventual.setFinal(new Precondition(contractMark, List.of()));
            this.eventual.setFinal(eventual);
        }

        @Override
        public Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider) {
            return overrides(analysisProvider, methodInfo, this);
        }

        @Override
        public StatementAnalysis getFirstStatement() {
            return firstStatement.getOrDefaultNull();
        }

        @Override
        public List<ParameterAnalysis> getParameterAnalyses() {
            return parameterAnalyses;
        }

        @Override
        public StatementAnalysis getLastStatement(boolean excludeThrows) {
            // we're not "caching" it during analysis; it may change (?) over iterations
            StatementAnalysis first = firstStatement.getOrDefaultNull();
            return first == null ? null : first.lastStatement(excludeThrows);
        }

        @Override
        public Precondition getPreconditionForEventual() {
            return preconditionForEventual.get();
        }

        @Override
        public Eventual getEventual() {
            return eventual.get();
        }

        public void setFirstStatement(StatementAnalysis firstStatement) {
            this.firstStatement.set(firstStatement);
        }

        public void addCompanion(CompanionMethodName companionMethodName, MethodInfo companion) {
            computedCompanions.put(companionMethodName, companion);
        }

        public void setEventualDelay(Eventual eventual) {
            assert eventual.causesOfDelay().isDelayed();
            this.eventual.setVariable(eventual);
        }

        public void setEventual(Eventual eventual) {
            this.eventual.setFinal(eventual);
        }

        @Override
        protected void writeEventual(String markValue, boolean mark, Boolean isAfter, Boolean test) {
            Set<FieldInfo> fields = methodInfo.typeInfo.findFields(inspectionProvider, markValue);
            writeEventual(new MethodAnalysis.Eventual(fields, mark, isAfter, test));
        }

        public void setPrecondition(Precondition pc) {
            if (pc.expression().isDelayed()) {
                precondition.setVariable(pc);
            } else {
                precondition.setFinal(pc);
            }
        }

        public void ensureIsNotEventualUnlessOtherwiseAnnotated() {
            if (!precondition.isFinal()) setPrecondition(Precondition.empty(primitives));
            if (!preconditionForEventual.isFinal()) setPreconditionForEventual(Precondition.empty(primitives));
            if (!eventual.isFinal()) setEventual(MethodAnalysis.NOT_EVENTUAL);
        }
    }

    private static Set<MethodAnalysis> overrides(AnalysisProvider analysisProvider,
                                                 MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis) {
        try {
            return methodInfo.methodResolution.get().overrides().stream()
                    .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                    .map(mi -> mi == methodInfo ? methodAnalysis : analysisProvider.getMethodAnalysis(mi))
                    .collect(Collectors.toSet());
        } catch (RuntimeException rte) {
            LOGGER.error("Cannot compute method analysis of {}", methodInfo.distinguishingName());
            throw rte;
        }
    }
}
