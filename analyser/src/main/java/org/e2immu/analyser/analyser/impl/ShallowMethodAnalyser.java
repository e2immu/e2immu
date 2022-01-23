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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.statementanalyser.StatementAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ShallowMethodAnalyser extends MethodAnalyserImpl {

    private static final Set<String> EXCEPTIONS_TO_CONTAINER = Set.of("java.util.Collection.toArray(T[])");
    private final boolean enableVisitors;

    public ShallowMethodAnalyser(MethodInfo methodInfo,
                                 MethodAnalysisImpl.Builder methodAnalysis,
                                 List<ParameterAnalysis> parameterAnalyses,
                                 AnalyserContext analyserContext,
                                 boolean enableVisitors) {
        super(methodInfo, methodAnalysis, List.of(), parameterAnalyses, Map.of(), false, analyserContext);
        this.enableVisitors = enableVisitors;
    }

    @Override
    public void initialize() {
        // no-op
    }


    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            List<AnnotationExpression> annotations = builder.getParameterInfo().parameterInspection.get().getAnnotations();
            analyserResultBuilder.addMessages(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    annotations, e2));
            if (explicitlyEmpty) {
                builder.setProperty(Property.MODIFIED_VARIABLE, DV.FALSE_DV);
                builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            }
        });

        List<AnnotationExpression> annotations = methodInfo.methodInspection.get().getAnnotations();
        analyserResultBuilder.addMessages(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, annotations, e2));

        // IMPROVE reading preconditions from AnnotatedAPIs...
        methodAnalysis.setPrecondition(Precondition.empty(analyserContext.getPrimitives()));
        if (!methodAnalysis.preconditionForEventual.isSet()) {
            methodAnalysis.preconditionForEventual.set(Optional.empty());
        }
        if (explicitlyEmpty) {
            DV modified = methodInfo.isConstructor ? DV.TRUE_DV : DV.FALSE_DV;
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            computeMethodPropertiesAfterParameters();

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterProperties(builder);
            });
        } else {
            computeMethodPropertyIfNecessary(Property.MODIFIED_METHOD, this::computeModifiedMethod);

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterProperties(builder);
            });

            computeMethodPropertiesAfterParameters();

            computeMethodPropertyIfNecessary(Property.INDEPENDENT, this::computeMethodIndependent);
            checkMethodIndependent();
        }
        if (enableVisitors) {
            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterMethodAnalyserVisitors();
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration,
                            null, methodInfo, methodAnalysis,
                            parameterAnalyses, Map.of(),
                            this::getMessageStream));
                }
            }
        }
        return AnalyserResult.EMPTY;
    }

    private void computeParameterProperties(ParameterAnalysisImpl.Builder builder) {
        computeParameterModified(builder);
        computeParameterPropertyIfNecessary(builder, Property.IMMUTABLE, this::computeParameterImmutable);
        computeParameterPropertyIfNecessary(builder, Property.INDEPENDENT, this::computeParameterIndependent);
        computeParameterPropertyIfNecessary(builder, Property.NOT_NULL_PARAMETER, this::computeNotNullParameter);
        computeParameterPropertyIfNecessary(builder, Property.CONTAINER, this::computeContainerParameter);
        computeParameterPropertyIfNecessary(builder, Property.IGNORE_MODIFICATIONS,
                this::computeParameterIgnoreModification);
    }

    private DV computeParameterIgnoreModification(ParameterAnalysisImpl.Builder builder) {
        return DV.fromBoolDv(builder.getParameterInfo().parameterizedType.isAbstractInJavaUtilFunction(analyserContext));
    }

    private DV computeNotNullParameter(ParameterAnalysisImpl.Builder builder) {
        ParameterizedType pt = builder.getParameterInfo().parameterizedType;
        if (pt.isPrimitiveExcludingVoid()) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.NOT_NULL_PARAMETER);
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(override);
    }

    /*
    @Container on parameters needs to be contracted; but it does inherit
     */
    private DV computeContainerParameter(ParameterAnalysisImpl.Builder builder) {
        return DV.FALSE_DV.maxIgnoreDelay(bestOfParameterOverrides(builder.getParameterInfo(), Property.CONTAINER));
    }

    private void computeMethodPropertiesAfterParameters() {
        computeMethodPropertyIfNecessary(Property.IMMUTABLE, this::computeMethodImmutable);
        computeMethodPropertyIfNecessary(Property.NOT_NULL_EXPRESSION, this::computeMethodNotNull);
        computeMethodPropertyIfNecessary(Property.FLUENT, () -> bestOfOverridesOrWorstValue(Property.FLUENT));
        computeMethodPropertyIfNecessary(Property.IDENTITY, () -> bestOfOverridesOrWorstValue(Property.IDENTITY));
        computeMethodPropertyIfNecessary(Property.FINALIZER, () -> bestOfOverridesOrWorstValue(Property.FINALIZER));
        computeMethodPropertyIfNecessary(Property.CONSTANT, () -> bestOfOverridesOrWorstValue(Property.CONSTANT));
        // @Identity must come before @Container
        computeMethodPropertyIfNecessary(Property.CONTAINER, this::computeMethodContainer);
    }

    private void computeMethodPropertyIfNecessary(Property property, Supplier<DV> computer) {
        DV inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(property);
        if (inMap.isDelayed()) {
            DV computed = computer.get();
            if (computed.isDone()) {
                methodAnalysis.setProperty(property, computed);
            }
        }
    }

    private static void computeParameterPropertyIfNecessary(ParameterAnalysisImpl.Builder builder,
                                                            Property property,
                                                            Function<ParameterAnalysisImpl.Builder, DV> computer) {
        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(property);
        if (inMap.isDelayed()) {
            DV computed = computer.apply(builder);
            if (computed.isDone()) {
                builder.setProperty(property, computed);
            }
        }
    }

    private DV bestOfOverridesOrWorstValue(Property property) {
        DV best = bestOfOverrides(property);
        return property.falseDv.maxIgnoreDelay(best);
    }

    /**
     * Container on methods ONLY when the return value is
     * - one of the parameters which has been contracted with @Container.
     * - of a "final" type (so that it cannot be extended) and decorated with @Container (like java.lang.String)
     * - an array type (which cannot be extended)
     * However, if a @Container type is returned, we will assume unless annotated otherwise, that the result is
     * a container.
     * <p>
     * Unbound type parameters are definitely NOT @Container, since you can substitute them for any non-@Container type.
     */
    private DV computeMethodContainer() {
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType.arrays > 0 || returnType.isPrimitiveExcludingVoid()) {
            return DV.TRUE_DV;
        }
        if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return DV.MIN_INT_DV; // no decision
        TypeInfo bestType = returnType.bestTypeInfo();
        if (bestType == null) return DV.FALSE_DV; // unbound type parameter

        // check formal return type
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        DV fromReturnType = typeAnalysis == null ? DV.MIN_INT_DV : typeAnalysis.getProperty(Property.CONTAINER);
        DV bestOfOverrides = bestOfOverrides(Property.CONTAINER);
        DV formal = DV.FALSE_DV.maxIgnoreDelay(bestOfOverrides.maxIgnoreDelay(fromReturnType));
        if (DV.TRUE_DV.equals(formal)) return DV.TRUE_DV;

        // check identity and parameter contract
        if (methodAnalysis.properties.getOrDefault(Property.IDENTITY, DV.FALSE_DV).equals(DV.TRUE_DV)) {
            ParameterAnalysis p0 = parameterAnalyses.get(0);
            return p0.getProperty(Property.CONTAINER);
        }
        return DV.FALSE_DV;
    }

    private DV computeModifiedMethod() {
        if (methodInfo.isConstructor) return DV.TRUE_DV;
        return DV.FALSE_DV.maxIgnoreDelay(bestOfOverrides(Property.MODIFIED_METHOD));
    }

    private DV computeMethodImmutable() {
        ParameterizedType returnType = methodInspection.getReturnType();
        DV immutable = analyserContext.defaultImmutable(returnType, true);
        if (immutable.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS)) {
            analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                    returnType.typeInfo == null ? "Return type of " + methodInfo.fullyQualifiedName :
                            returnType.typeInfo.fullyQualifiedName));
            return MultiLevel.MUTABLE_DV;
        }
        return immutable;
    }


    private void computeParameterModified(ParameterAnalysisImpl.Builder builder) {
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.MODIFIED_VARIABLE);
        DV typeContainer = analyserContext.getTypeAnalysis(builder.getParameterInfo().owner.typeInfo).getProperty(Property.CONTAINER);

        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_VARIABLE);
        if (inMap.isDelayed()) {
            DV value;
            if (typeContainer.valueIsTrue()) {
                value = DV.FALSE_DV;
            } else if (override.isDone()) {
                value = override;
            } else {
                ParameterizedType type = builder.getParameterInfo().parameterizedType;
                if (type.isPrimitiveExcludingVoid() || type.isJavaLangString()) {
                    value = DV.FALSE_DV;
                } else {
                    DV typeIndependent = analyserContext.defaultIndependent(type);
                    value = DV.fromBoolDv(!typeIndependent.equals(MultiLevel.INDEPENDENT_DV));
                }
            }
            builder.setProperty(Property.MODIFIED_VARIABLE, value);
        } else if (override.valueIsFalse() && inMap.valueIsTrue()) {
            analyserResultBuilder.add(Message.newMessage(builder.getParameterInfo().newLocation(),
                    Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                    "Override was non-modifying, while this parameter is modifying"));
        } else if (typeContainer.valueIsTrue() && inMap.valueIsTrue()) {
            if (!EXCEPTIONS_TO_CONTAINER.contains(methodInfo.fullyQualifiedName)) {
                analyserResultBuilder.add(Message.newMessage(builder.getParameterInfo().newLocation(),
                        Message.Label.CONTRADICTING_ANNOTATIONS, "Type is @Container, parameter is @Modified"));
            }
        }
    }

    private DV computeParameterImmutable(ParameterAnalysisImpl.Builder builder) {
        return analyserContext.defaultImmutable(builder.getParameterInfo().parameterizedType, true);
    }

    private DV computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        DV value;
        ParameterizedType type = builder.getParameterInfo().parameterizedType;
        if (type.isPrimitiveExcludingVoid()) {
            value = MultiLevel.INDEPENDENT_DV;
        } else {
            // @Modified needs to be marked explicitly
            DV modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
            if (modifiedMethod.valueIsTrue() || methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                TypeInfo bestType = type.bestTypeInfo();
                if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                    value = MultiLevel.INDEPENDENT_1_DV;
                } else {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                    if (typeAnalysis != null) {
                        value = analyserContext.getTypeAnalysis(bestType).getProperty(Property.INDEPENDENT);
                    } else {
                        value = MultiLevel.DEPENDENT_DV;
                    }
                }
            } else {
                value = MultiLevel.INDEPENDENT_DV;
            }
        }
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.INDEPENDENT);
        return override.maxIgnoreDelay(value);
    }

    private void checkMethodIndependent() {
        DV finalValue = methodAnalysis.getProperty(Property.INDEPENDENT);
        DV overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPublic())
                .map(analyserContext::getMethodAnalysis)
                .map(ma -> ma.getProperty(Property.INDEPENDENT))
                .reduce(DV.MAX_INT_DV, DV::min);
        if (overloads != DV.MAX_INT_DV && finalValue.lt(overloads)) {
            analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT,
                    finalValue.label() + " instead of " + overloads.label()));
        }
    }

    private DV computeMethodIndependent() {
        DV returnValueIndependent = computeMethodIndependentReturnValue();

        // typeIndependent is set by hand in AnnotatedAPI files
        DV typeIndependent = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(Property.INDEPENDENT);
        DV bestOfOverrides = bestOfOverrides(Property.INDEPENDENT);
        return MultiLevel.DEPENDENT_DV
                .maxIgnoreDelay(returnValueIndependent)
                .maxIgnoreDelay(bestOfOverrides)
                .maxIgnoreDelay(typeIndependent);
    }

    private DV computeMethodIndependentReturnValue() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) {
            return MultiLevel.INDEPENDENT_DV;
        }
        if (methodInfo.methodInspection.get().isStatic() && !methodInspection.isFactoryMethod()) {
            // if factory method, we link return value to parameters, otherwise independent by default
            return MultiLevel.INDEPENDENT_DV;
        }
        DV identity = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.IDENTITY);
        DV modified = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
        if (identity.valueIsTrue() && modified.valueIsFalse()) {
            return MultiLevel.INDEPENDENT_DV; // @Identity + @NotModified -> must be @Independent
        }
        TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
        if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
            // unbound type parameter T, or unbound with array T[], T[][]
            return MultiLevel.INDEPENDENT_1_DV;
        }
        if (bestType.isPrimitiveExcludingVoid()) {
            return MultiLevel.INDEPENDENT_DV;
        }
        DV immutable = methodAnalysis.getProperty(Property.IMMUTABLE);
        if (MultiLevel.isAtLeastEffectivelyE2Immutable(immutable)) {

            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
            if (typeAnalysis != null) {
                return typeAnalysis.getProperty(Property.INDEPENDENT);
            }
            analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                    Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE, bestType.fullyQualifiedName));
            return MultiLevel.DEPENDENT_DV;
        }
        return MultiLevel.DEPENDENT_DV;
    }

    private DV computeMethodNotNull() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) return DV.MIN_INT_DV; // no decision!
        if (methodInfo.returnType().isPrimitiveExcludingVoid()) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        DV fluent = methodAnalysis.getProperty(Property.FLUENT);
        if (fluent.valueIsTrue()) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(bestOfOverrides(Property.NOT_NULL_EXPRESSION));
    }

    private DV bestOfOverrides(Property property) {
        DV bestOfOverrides = DV.MIN_INT_DV;
        for (MethodAnalysis override : methodAnalysis.getOverrides(analyserContext)) {
            DV overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(property);
            bestOfOverrides = bestOfOverrides.maxIgnoreDelay(overrideAsIs);
        }
        return bestOfOverrides;
    }

    private DV bestOfParameterOverrides(ParameterInfo parameterInfo, Property property) {
        return methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                .map(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(property);
                }).reduce(DV.MIN_INT_DV, DV::maxIgnoreDelay);
    }

    @Override
    public void write() {
        // everything contracted, nothing to write
    }

    @Override
    public void check() {
        // everything contracted, nothing to check
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo) {
        return Stream.empty();
    }

    @Override
    public StatementAnalyserImpl findStatementAnalyser(String index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing here
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        throw new UnsupportedOperationException("Shallow method analyser has no analyser components");
    }

    @Override
    public void makeImmutable() {
        // nothing here
    }
}
