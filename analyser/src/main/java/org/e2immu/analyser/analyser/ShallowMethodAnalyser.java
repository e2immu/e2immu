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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ShallowMethodAnalyser extends MethodAnalyser {

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
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            List<AnnotationExpression> annotations = builder.getParameterInfo().parameterInspection.get().getAnnotations();
            messages.addAll(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    annotations, e2));
            if (explicitlyEmpty) {
                builder.setProperty(VariableProperty.MODIFIED_VARIABLE, Level.FALSE_DV);
                builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            }
        });

        List<AnnotationExpression> annotations = methodInfo.methodInspection.get().getAnnotations();
        messages.addAll(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, annotations, e2));

        // IMPROVE reading preconditions from AAPI...
        methodAnalysis.setPrecondition(Precondition.empty(analyserContext.getPrimitives()));
        methodAnalysis.preconditionForEventual.set(Optional.empty());

        if (explicitlyEmpty) {
            DV modified = methodInfo.isConstructor ? Level.TRUE_DV : Level.FALSE_DV;
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            computeMethodPropertiesAfterParameters();

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterProperties(builder);
            });
        } else {
            computeMethodPropertyIfNecessary(VariableProperty.MODIFIED_METHOD, this::computeModifiedMethod);

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterProperties(builder);
            });

            computeMethodPropertiesAfterParameters();

            computeMethodPropertyIfNecessary(VariableProperty.INDEPENDENT, this::computeMethodIndependent);
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
        return AnalysisStatus.DONE;
    }

    private void computeParameterProperties(ParameterAnalysisImpl.Builder builder) {
        computeParameterModified(builder);
        computeParameterPropertyIfNecessary(builder, VariableProperty.IMMUTABLE, this::computeParameterImmutable);
        computeParameterPropertyIfNecessary(builder, VariableProperty.INDEPENDENT, this::computeParameterIndependent);
        computeParameterPropertyIfNecessary(builder, VariableProperty.NOT_NULL_PARAMETER, this::computeNotNullParameter);
        computeParameterPropertyIfNecessary(builder, VariableProperty.CONTAINER, this::computeContainerParameter);
        computeParameterPropertyIfNecessary(builder, VariableProperty.IGNORE_MODIFICATIONS,
                this::computeParameterIgnoreModification);
    }

    private DV computeParameterIgnoreModification(ParameterAnalysisImpl.Builder builder) {
        return Level.fromBoolDv(builder.getParameterInfo().parameterizedType.isAbstractInJavaUtilFunction(analyserContext));
    }

    private DV computeNotNullParameter(ParameterAnalysisImpl.Builder builder) {
        ParameterizedType pt = builder.getParameterInfo().parameterizedType;
        if (Primitives.isPrimitiveExcludingVoid(pt)) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.NOT_NULL_PARAMETER);
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(override);
    }

    /*
    @Container on parameters needs to be contracted; but it does inherit
     */
    private DV computeContainerParameter(ParameterAnalysisImpl.Builder builder) {
        return Level.FALSE_DV.maxIgnoreDelay(bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.CONTAINER));
    }

    private void computeMethodPropertiesAfterParameters() {
        computeMethodPropertyIfNecessary(VariableProperty.IMMUTABLE, this::computeMethodImmutable);
        computeMethodPropertyIfNecessary(VariableProperty.NOT_NULL_EXPRESSION, this::computeMethodNotNull);
        computeMethodPropertyIfNecessary(VariableProperty.CONTAINER, this::computeMethodContainer);
        computeMethodPropertyIfNecessary(VariableProperty.FLUENT, () -> bestOfOverridesOrWorstValue(VariableProperty.FLUENT));
        computeMethodPropertyIfNecessary(VariableProperty.IDENTITY, () -> bestOfOverridesOrWorstValue(VariableProperty.IDENTITY));
        computeMethodPropertyIfNecessary(VariableProperty.FINALIZER, () -> bestOfOverridesOrWorstValue(VariableProperty.FINALIZER));
        computeMethodPropertyIfNecessary(VariableProperty.CONSTANT, () -> bestOfOverridesOrWorstValue(VariableProperty.CONSTANT));
    }

    private void computeMethodPropertyIfNecessary(VariableProperty variableProperty, Supplier<DV> computer) {
        DV inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (inMap.isDelayed()) {
            DV computed = computer.get();
            if (computed.isDone()) {
                methodAnalysis.setProperty(variableProperty, computed);
            }
        }
    }

    private static void computeParameterPropertyIfNecessary(ParameterAnalysisImpl.Builder builder,
                                                            VariableProperty variableProperty,
                                                            Function<ParameterAnalysisImpl.Builder, DV> computer) {
        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (inMap.isDelayed()) {
            DV computed = computer.apply(builder);
            if (computed.isDone()) {
                builder.setProperty(variableProperty, computed);
            }
        }
    }

    private DV bestOfOverridesOrWorstValue(VariableProperty variableProperty) {
        DV best = bestOfOverrides(variableProperty);
        return variableProperty.falseDv.maxIgnoreDelay(best);
    }

    private DV computeMethodContainer() {
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType.arrays > 0 || Primitives.isPrimitiveExcludingVoid(returnType) || returnType.isUnboundTypeParameter()) {
            return Level.TRUE_DV;
        }
        if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return DV.MIN_INT_DV; // no decision
        TypeInfo bestType = returnType.bestTypeInfo();
        if (bestType == null) return Level.TRUE_DV; // unbound type parameter
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        DV fromReturnType = typeAnalysis == null ? DV.MIN_INT_DV : typeAnalysis.getProperty(VariableProperty.CONTAINER);
        DV bestOfOverrides = bestOfOverrides(VariableProperty.CONTAINER);
        return Level.FALSE_DV.maxIgnoreDelay(bestOfOverrides.maxIgnoreDelay(fromReturnType));
    }

    private DV computeModifiedMethod() {
        if (methodInfo.isConstructor) return Level.TRUE_DV;
        return Level.FALSE_DV.maxIgnoreDelay(bestOfOverrides(VariableProperty.MODIFIED_METHOD));
    }

    private DV computeMethodImmutable() {
        ParameterizedType returnType = methodInspection.getReturnType();
        DV immutable = returnType.defaultImmutable(analyserContext, true);
        if (immutable.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS)) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                    returnType.toString()));
            return MultiLevel.MUTABLE_DV;
        }
        return immutable;
    }


    private void computeParameterModified(ParameterAnalysisImpl.Builder builder) {
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.MODIFIED_VARIABLE);
        DV typeContainer = analyserContext.getTypeAnalysis(builder.getParameterInfo().owner.typeInfo).getProperty(VariableProperty.CONTAINER);

        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_VARIABLE);
        if (inMap.isDelayed()) {
            DV value;
            if (typeContainer.valueIsTrue()) {
                value = Level.FALSE_DV;
            } else if (override.isDone()) {
                value = override;
            } else {
                ParameterizedType type = builder.getParameterInfo().parameterizedType;
                if (Primitives.isPrimitiveExcludingVoid(type) || Primitives.isJavaLangString(type)) {
                    value = Level.FALSE_DV;
                } else {
                    DV typeIndependent = type.defaultIndependent(analyserContext);
                    value = Level.fromBoolDv(!typeIndependent.equals(MultiLevel.INDEPENDENT_DV));
                }
            }
            builder.setProperty(VariableProperty.MODIFIED_VARIABLE, value);
        } else if (override.valueIsFalse() && inMap.valueIsTrue()) {
            messages.add(Message.newMessage(new Location(builder.getParameterInfo()),
                    Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                    "Override was non-modifying, while this parameter is modifying"));
        } else if (typeContainer.valueIsTrue() && inMap.valueIsTrue()) {
            if (!EXCEPTIONS_TO_CONTAINER.contains(methodInfo.fullyQualifiedName)) {
                messages.add(Message.newMessage(new Location(builder.getParameterInfo()),
                        Message.Label.CONTRADICTING_ANNOTATIONS, "Type is @Container, parameter is @Modified"));
            }
        }
    }

    private DV computeParameterImmutable(ParameterAnalysisImpl.Builder builder) {
        return builder.getParameterInfo().parameterizedType.defaultImmutable(analyserContext, true);
    }

    private DV computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        DV value;
        ParameterizedType type = builder.getParameterInfo().parameterizedType;
        if (Primitives.isPrimitiveExcludingVoid(type)) {
            value = MultiLevel.INDEPENDENT_DV;
        } else {
            // @Modified needs to be marked explicitly
            DV modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
            if (modifiedMethod.valueIsTrue() || methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                TypeInfo bestType = type.bestTypeInfo();
                if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                    value = MultiLevel.INDEPENDENT_1_DV;
                } else {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                    if (typeAnalysis != null) {
                        value = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.INDEPENDENT);
                    } else {
                        value = MultiLevel.DEPENDENT_DV;
                    }
                }
            } else {
                value = MultiLevel.INDEPENDENT_DV;
            }
        }
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.INDEPENDENT);
        return override.maxIgnoreDelay(value);
    }

    private void checkMethodIndependent() {
        DV finalValue = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        DV overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPublic())
                .map(analyserContext::getMethodAnalysis)
                .map(ma -> ma.getProperty(VariableProperty.INDEPENDENT))
                .reduce(DV.MAX_INT_DV, DV::min);
        if (overloads != DV.MAX_INT_DV && finalValue.lt(overloads)) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT, MultiLevel.niceIndependent(finalValue) + " instead of " +
                            MultiLevel.niceIndependent(overloads)));
        }
    }

    private DV computeMethodIndependent() {
        DV returnValueIndependent = computeMethodIndependentReturnValue();

        // typeIndependent is set by hand in AnnotatedAPI files
        DV typeIndependent = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(VariableProperty.INDEPENDENT);
        DV bestOfOverrides = bestOfOverrides(VariableProperty.INDEPENDENT);
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
        DV identity = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.IDENTITY);
        DV modified = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
        if (identity.valueIsTrue() && modified.valueIsFalse()) {
            return MultiLevel.INDEPENDENT_DV; // @Identity + @NotModified -> must be @Independent
        }
        TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
        if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
            // unbound type parameter T, or unbound with array T[], T[][]
            return MultiLevel.INDEPENDENT_1_DV;
        }
        if (Primitives.isPrimitiveExcludingVoid(bestType)) {
            return MultiLevel.INDEPENDENT_DV;
        }
        DV immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (MultiLevel.isAtLeastEffectivelyE2Immutable(immutable)) {

            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
            if (typeAnalysis != null) {
                return typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
            }
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE, bestType.fullyQualifiedName));
            return MultiLevel.DEPENDENT_DV;
        }
        return MultiLevel.DEPENDENT_DV;
    }

    private DV computeMethodNotNull() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) return DV.MIN_INT_DV; // no decision!
        if (Primitives.isPrimitiveExcludingVoid(methodInfo.returnType())) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        DV fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent.valueIsTrue()) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(bestOfOverrides(VariableProperty.NOT_NULL_EXPRESSION));
    }

    private DV bestOfOverrides(VariableProperty variableProperty) {
        DV bestOfOverrides = DV.MIN_INT_DV;
        for (MethodAnalysis override : methodAnalysis.getOverrides(analyserContext)) {
            DV overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(variableProperty);
            bestOfOverrides = bestOfOverrides.maxIgnoreDelay(overrideAsIs);
        }
        return bestOfOverrides;
    }

    private DV bestOfParameterOverrides(ParameterInfo parameterInfo, VariableProperty variableProperty) {
        return methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                .map(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(variableProperty);
                }).reduce(DV.MIN_INT_DV, DV::maxIgnoreDelay);
    }

    @Override
    public void write() {
        // everything contracted, nothing to write
    }

    @Override
    public List<VariableInfo> getFieldAsVariable(FieldInfo fieldInfo, boolean b) {
        return List.of();
    }

    @Override
    public void check() {
        // everything contracted, nothing to check
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo, boolean includeLocalCopies) {
        return Stream.empty();
    }

    @Override
    public StatementAnalyser findStatementAnalyser(String index) {
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
