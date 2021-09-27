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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.util.CreatePreconditionCompanion;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.ContractMark;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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
                               Map<VariableProperty, Integer> properties,
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
        this.singleReturnValue = singleReturnValue;
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
    public Precondition getPrecondition() {
        return precondition;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getMethodProperty(variableProperty);
    }

    @Override
    public Location location() {
        return new Location(methodInfo);
    }

    public static class Builder extends AbstractAnalysisBuilder implements MethodAnalysis {
        public final ParameterizedType returnType;
        public final MethodInfo methodInfo;
        private final SetOnce<StatementAnalysis> firstStatement = new SetOnce<>();
        public final List<ParameterAnalysis> parameterAnalyses;
        private final AnalysisProvider analysisProvider;
        private final InspectionProvider inspectionProvider;

        // the value here (size will be one)
        public final SetOnce<Optional<Precondition>> preconditionForEventual = new SetOnce<>();
        private final SetOnce<Eventual> eventual = new SetOnce<>();

        public final SetOnce<Expression> singleReturnValue = new SetOnce<>();

        // ************** PRECONDITION

        public final SetOnce<Precondition> precondition = new SetOnce<>();
        public final SetOnceMap<CompanionMethodName, CompanionAnalysis> companionAnalyses = new SetOnceMap<>();

        public final SetOnceMap<CompanionMethodName, MethodInfo> computedCompanions = new SetOnceMap<>();

        public final AnalysisMode analysisMode;

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
            return precondition.getOrDefaultNull();
        }

        @Override
        public boolean eventualIsSet() {
            return eventual.isSet();
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
                    preconditionForEventual.getOrDefault(Optional.empty()).orElse(null),
                    eventual.getOrDefault(NOT_EVENTUAL),
                    precondition.getOrDefault(Precondition.empty(primitives)),
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
            return singleReturnValue.getOrDefaultNull();
        }

        @Override
        public Location location() {
            return new Location(methodInfo);
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getMethodProperty(variableProperty);
        }

        private int formalProperty() {
            return returnType.getProperty(analysisProvider, VariableProperty.IMMUTABLE);
        }

        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            int modified = getProperty(VariableProperty.MODIFIED_METHOD);

            // @Precondition
            if (precondition.isSet()) {
                Precondition pc = precondition.get();
                if (!(pc.expression() instanceof BooleanConstant)) {
                    // generate a companion method, but only when the precondition is non-trivial
                    new CreatePreconditionCompanion(InspectionProvider.defaultFrom(primitives), analysisProvider)
                            .addPreconditionCompanion(methodInfo, this, pc.expression());
                }
            }

            // @Dependent @Independent
            int independent = getProperty(VariableProperty.INDEPENDENT);
            if (independent == MultiLevel.DEPENDENT_1) {
                annotations.put(e2ImmuAnnotationExpressions.dependent1, true);
            } else if (methodInfo.isConstructor || modified == Level.FALSE && allowIndependentOnMethod()) {
                doIndependent(e2ImmuAnnotationExpressions, independent, methodInfo.typeInfo.isInterface());
            }

            if (methodInfo.isConstructor) return;

            // @NotModified, @Modified
            AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                    e2ImmuAnnotationExpressions.modified;
            annotations.put(ae, true);

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            int formallyImmutable = formalProperty();
            int dynamicallyImmutable = getProperty(VariableProperty.IMMUTABLE);
            if (MultiLevel.isBetterImmutable(dynamicallyImmutable, formallyImmutable)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, dynamicallyImmutable, true);
            }

            if (Primitives.isVoidOrJavaLangVoid(returnType)) return;

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
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        }

        private boolean allowIndependentOnMethod() {
            return !Primitives.isVoidOrJavaLangVoid(returnType) &&
                    returnType.isTransparentOrAtLeastEventuallyE2Immutable(analysisProvider, methodInfo.typeInfo) != Boolean.TRUE;
        }

        protected void writeEventual(Eventual eventual) {
            ContractMark contractMark = new ContractMark(eventual.fields());
            preconditionForEventual.set(Optional.of(new Precondition(contractMark, List.of())));
            this.eventual.set(eventual);
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
        public StatementAnalysis getLastStatement() {
            // we're not "caching" it during analysis; it may change (?) over iterations
            StatementAnalysis first = firstStatement.getOrDefaultNull();
            return first == null ? null : first.lastStatement();
        }

        @Override
        public Precondition getPreconditionForEventual() {
            return preconditionForEventual.getOrDefault(Optional.empty()).orElse(null);
        }

        @Override
        public Eventual getEventual() {
            return eventual.getOrDefault(DELAYED_EVENTUAL);
        }

        public void setFirstStatement(StatementAnalysis firstStatement) {
            this.firstStatement.set(firstStatement);
        }

        public void addCompanion(CompanionMethodName companionMethodName, MethodInfo companion) {
            computedCompanions.put(companionMethodName, companion);
        }

        public void setEventual(Eventual eventual) {
            this.eventual.set(eventual);
        }

        @Override
        protected void writeEventual(String markValue, boolean mark, Boolean isAfter, Boolean test) {
            Set<FieldInfo> fields = methodInfo.typeInfo.findFields(inspectionProvider, markValue);
            writeEventual(new MethodAnalysis.Eventual(fields, mark, isAfter, test));
        }
    }

    private static Set<MethodAnalysis> overrides(AnalysisProvider analysisProvider,
                                                 MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis) {
        try {
            return methodInfo.methodResolution.get().overrides().stream()
                    .filter(mi -> !mi.shallowAnalysis() || mi.methodInspection.get().isPublic())
                    .map(mi -> mi == methodInfo ? methodAnalysis : analysisProvider.getMethodAnalysis(mi))
                    .collect(Collectors.toSet());
        } catch (RuntimeException rte) {
            LOGGER.error("Cannot compute method analysis of {}", methodInfo.distinguishingName());
            throw rte;
        }
    }


}
