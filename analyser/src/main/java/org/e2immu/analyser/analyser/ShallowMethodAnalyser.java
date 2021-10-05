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
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.CONTAINER;

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
                builder.setProperty(VariableProperty.MODIFIED_VARIABLE, Level.FALSE);
                builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            }
        });

        List<AnnotationExpression> annotations = methodInfo.methodInspection.get().getAnnotations();
        messages.addAll(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, annotations, e2));

        // IMPROVE reading preconditions from AAPI...
        methodAnalysis.precondition.set(Precondition.empty(analyserContext.getPrimitives()));
        methodAnalysis.preconditionForEventual.set(Optional.empty());

        if (explicitlyEmpty) {
            int modified = methodInfo.isConstructor ? Level.TRUE : Level.FALSE;
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            computeMethodPropertiesAfterParameters();
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

    private int computeParameterIgnoreModification(ParameterAnalysisImpl.Builder builder) {
        TypeInfo bestType = builder.getParameterInfo().parameterizedType.bestTypeInfo();
        if (bestType != null && bestType.isPrimaryType()
                && "java.util.function".equals(bestType.packageName())
                && bestType.isAbstract(analyserContext)) {
            return Level.TRUE;
        }
        return Level.FALSE;
    }

    private int computeNotNullParameter(ParameterAnalysisImpl.Builder builder) {
        ParameterizedType pt = builder.getParameterInfo().parameterizedType;
        if (Primitives.isPrimitiveExcludingVoid(pt)) return MultiLevel.EFFECTIVELY_NOT_NULL;
        int override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.NOT_NULL_PARAMETER);
        return Math.max(MultiLevel.NULLABLE, override);
    }

    private int computeContainerParameter(ParameterAnalysisImpl.Builder builder) {
        TypeInfo bestType = builder.getParameterInfo().parameterizedType.bestTypeInfo(analyserContext);
        if (bestType == null) return Level.TRUE;
        if (Primitives.isPrimitiveExcludingVoid(bestType)) return Level.TRUE;
        int override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.CONTAINER);
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        int typeContainer;
        if (typeAnalysis == null) {
            typeContainer = Level.FALSE;
            messages.add(Message.newMessage(new Location(bestType), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE));
        } else {
            typeContainer = typeAnalysis.getProperty(CONTAINER);
        }
        return Math.max(Level.FALSE, Math.max(typeContainer, override));
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

    private void computeMethodPropertyIfNecessary(VariableProperty variableProperty, IntSupplier computer) {
        int inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (inMap == Level.DELAY) {
            int computed = computer.getAsInt();
            if (computed > Level.DELAY) {
                methodAnalysis.setProperty(variableProperty, computed);
            }
        }
    }

    private static void computeParameterPropertyIfNecessary(ParameterAnalysisImpl.Builder builder,
                                                            VariableProperty variableProperty,
                                                            ToIntFunction<ParameterAnalysisImpl.Builder> computer) {
        int inMap = builder.getPropertyFromMapDelayWhenAbsent(variableProperty);
        if (inMap == Level.DELAY) {
            int computed = computer.applyAsInt(builder);
            if (computed > Level.DELAY) {
                builder.setProperty(variableProperty, computed);
            }
        }
    }

    private int bestOfOverridesOrWorstValue(VariableProperty variableProperty) {
        int best = bestOfOverrides(variableProperty);
        return Math.max(variableProperty.falseValue, best);
    }

    private int computeMethodContainer() {
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType.arrays > 0 || Primitives.isPrimitiveExcludingVoid(returnType) || returnType.isUnboundTypeParameter()) {
            return Level.TRUE;
        }
        if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR) return Level.DELAY; // no decision
        TypeInfo bestType = returnType.bestTypeInfo();
        if (bestType == null) return Level.TRUE; // unbound type parameter
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        int fromReturnType = typeAnalysis == null ? Level.DELAY : typeAnalysis.getProperty(VariableProperty.CONTAINER);
        int bestOfOverrides = bestOfOverrides(VariableProperty.CONTAINER);
        return Math.max(Level.FALSE, Math.max(bestOfOverrides, fromReturnType));
    }

    private int computeModifiedMethod() {
        if (methodInfo.isConstructor) return Level.TRUE;
        return Math.max(Level.FALSE, bestOfOverrides(VariableProperty.MODIFIED_METHOD));
    }

    private int computeMethodImmutable() {
        ParameterizedType returnType = methodInspection.getReturnType();
        int immutable = returnType.defaultImmutable(analyserContext, true);
        if (immutable == ParameterizedType.TYPE_ANALYSIS_NOT_AVAILABLE) {
            messages.add(Message.newMessage(new Location(methodInfo), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                    returnType.toString()));
            return MultiLevel.MUTABLE;
        }
        return immutable;
    }


    private void computeParameterModified(ParameterAnalysisImpl.Builder builder) {
        int override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.MODIFIED_VARIABLE);
        int typeContainer = analyserContext.getTypeAnalysis(builder.getParameterInfo().owner.typeInfo).getProperty(VariableProperty.CONTAINER);

        int inMap = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_VARIABLE);
        if (inMap == Level.DELAY) {
            int value;
            if (typeContainer == Level.TRUE) {
                value = Level.FALSE;
            } else if (override != Level.DELAY) {
                value = override;
            } else {
                ParameterizedType type = builder.getParameterInfo().parameterizedType;
                if (Primitives.isPrimitiveExcludingVoid(type) || Primitives.isJavaLangString(type)) {
                    value = Level.FALSE;
                } else {
                    int typeIndependent = type.defaultIndependent(analyserContext);
                    if (typeIndependent == MultiLevel.INDEPENDENT) {
                        value = Level.FALSE;
                    } else {
                        value = Level.TRUE;
                    }
                }
            }
            builder.setProperty(VariableProperty.MODIFIED_VARIABLE, value);
        } else if (override == Level.FALSE && inMap == Level.TRUE) {
            messages.add(Message.newMessage(new Location(builder.getParameterInfo()),
                    Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                    "Override was non-modifying, while this parameter is modifying"));
        } else if (typeContainer == Level.TRUE && inMap == Level.TRUE) {
            if (!EXCEPTIONS_TO_CONTAINER.contains(methodInfo.fullyQualifiedName)) {
                messages.add(Message.newMessage(new Location(builder.getParameterInfo()),
                        Message.Label.CONTRADICTING_ANNOTATIONS, "Type is @Container, parameter is @Modified"));
            }
        }
    }

    private int computeParameterImmutable(ParameterAnalysisImpl.Builder builder) {
        return builder.getParameterInfo().parameterizedType.defaultImmutable(analyserContext, true);
    }

    private int computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        int value;
        ParameterizedType type = builder.getParameterInfo().parameterizedType;
        if (Primitives.isPrimitiveExcludingVoid(type)) {
            value = MultiLevel.INDEPENDENT;
        } else {
            // @Modified needs to be marked explicitly
            int modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
            if (modifiedMethod == Level.TRUE) {
                TypeInfo bestType = type.bestTypeInfo();
                if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                    value = MultiLevel.DEPENDENT_1;
                } else {
                    TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                    if (typeAnalysis != null) {
                        value = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.INDEPENDENT);
                    } else {
                        value = MultiLevel.DEPENDENT;
                    }
                }
            } else {
                value = MultiLevel.INDEPENDENT;
            }
        }
        int override = bestOfParameterOverrides(builder.getParameterInfo(), VariableProperty.INDEPENDENT);
        return Math.max(override, value);
    }

    private void checkMethodIndependent() {
        int finalValue = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
        int overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPublic())
                .map(analyserContext::getMethodAnalysis)
                .mapToInt(ma -> ma.getProperty(VariableProperty.INDEPENDENT))
                .min().orElse(finalValue);
        if (finalValue < overloads) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT, MultiLevel.niceIndependent(finalValue) + " instead of " +
                            MultiLevel.niceIndependent(overloads)));
        }
    }

    private int computeMethodIndependent() {
        int returnValueIndependent;
        if (methodInfo.isConstructor || methodInfo.isVoid() || methodInfo.methodInspection.get().isStatic()) {
            returnValueIndependent = MultiLevel.INDEPENDENT;
        } else {
            int identity = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.IDENTITY);
            int modified = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
            if (identity == Level.TRUE && modified == Level.FALSE) {
                returnValueIndependent = MultiLevel.INDEPENDENT; // @Identity + @NotModified -> must be @Independent
            } else {
                TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
                if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
                    // unbound type parameter T, or unbound with array T[], T[][]
                    returnValueIndependent = MultiLevel.DEPENDENT_1;
                } else {
                    if (Primitives.isPrimitiveExcludingVoid(bestType)) {
                        returnValueIndependent = MultiLevel.INDEPENDENT;
                    } else {
                        int immutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                        if (immutable >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) {

                            TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                            if (typeAnalysis != null) {
                                returnValueIndependent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
                            } else {
                                messages.add(Message.newMessage(new Location(methodInfo),
                                        Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE, bestType.fullyQualifiedName));
                                returnValueIndependent = MultiLevel.DEPENDENT;
                            }

                        } else {
                            returnValueIndependent = MultiLevel.DEPENDENT;
                        }
                    }
                }
            }
        }
        // typeIndependent is set by hand in AnnotatedAPI files
        int typeIndependent = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(VariableProperty.INDEPENDENT);
        int bestOfOverrides = bestOfOverrides(VariableProperty.INDEPENDENT);
        return Math.max(MultiLevel.DEPENDENT, Math.max(returnValueIndependent, Math.max(bestOfOverrides, typeIndependent)));
    }

    private int computeMethodNotNull() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) return Level.DELAY; // no decision!
        if (Primitives.isPrimitiveExcludingVoid(methodInfo.returnType())) {
            return MultiLevel.EFFECTIVELY_NOT_NULL;
        }
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.TRUE) return MultiLevel.EFFECTIVELY_NOT_NULL;
        return Math.max(MultiLevel.NULLABLE, bestOfOverrides(VariableProperty.NOT_NULL_EXPRESSION));
    }

    private int bestOfOverrides(VariableProperty variableProperty) {
        int bestOfOverrides = Level.DELAY;
        for (MethodAnalysis override : methodAnalysis.getOverrides(analyserContext)) {
            int overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(variableProperty);
            bestOfOverrides = Math.max(bestOfOverrides, overrideAsIs);
        }
        return bestOfOverrides;
    }

    private int bestOfParameterOverrides(ParameterInfo parameterInfo, VariableProperty variableProperty) {
        return methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                .mapToInt(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(variableProperty);
                }).max().orElse(Level.DELAY);
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression, List<MethodInfo>> annotations) {
        if (annotations.size() < 2) return;
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        checkContradictions(where, annotations, e2.notModified, e2.modified);
        checkContradictions(where, annotations, e2.notNull, e2.nullable);
    }

    private void checkContradictions(WithInspectionAndAnalysis where,
                                     Map<AnnotationExpression, List<MethodInfo>> annotations,
                                     AnnotationExpression left,
                                     AnnotationExpression right) {
        List<MethodInfo> leftMethods = annotations.getOrDefault(left, List.of());
        List<MethodInfo> rightMethods = annotations.getOrDefault(right, List.of());
        if (!leftMethods.isEmpty() && !rightMethods.isEmpty()) {
            messages.add(Message.newMessage(new Location(where), Message.Label.CONTRADICTING_ANNOTATIONS,
                    left + " in " + leftMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; ")) +
                            "; " + right + " in " + rightMethods.stream()
                            .map(mi -> mi.fullyQualifiedName).collect(Collectors.joining("; "))));
        }
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

    @Override
    protected String where(String componentName) {
        throw new UnsupportedOperationException();
    }
}
