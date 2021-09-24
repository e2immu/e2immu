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
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SMapList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ShallowMethodAnalyser extends MethodAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowMethodAnalyser.class);

    public ShallowMethodAnalyser(MethodInfo methodInfo,
                                 MethodAnalysisImpl.Builder methodAnalysis,
                                 List<ParameterAnalysis> parameterAnalyses,
                                 AnalyserContext analyserContext) {
        super(methodInfo, methodAnalysis, List.of(), parameterAnalyses, Map.of(), false, analyserContext);
    }

    @Override
    public void initialize() {
        // no-op
    }


    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = collectAnnotations();
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            messages.addAll(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    map.getOrDefault(builder.getParameterInfo(), Map.of()).keySet(), e2));
            if (explicitlyEmpty) {
                builder.setProperty(VariableProperty.MODIFIED_VARIABLE, Level.FALSE);
                builder.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            }
        });

        messages.addAll(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, map.getOrDefault(methodInfo, Map.of()).keySet(), e2));

        // IMPROVE reading preconditions from AAPI...
        methodAnalysis.precondition.set(Precondition.empty(analyserContext.getPrimitives()));
        methodAnalysis.preconditionForEventual.set(Optional.empty());

        if (explicitlyEmpty) {
            int modified = methodInfo.isConstructor ? Level.TRUE : Level.FALSE;
            methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
        } else {
            computeMethodModified(); // used in parameter computations

            parameterAnalyses.forEach(parameterAnalysis -> {
                ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                computeParameterIndependent(builder);
            });

            computeMethodIndependent();
        }
        computeMethodImmutable();

        return AnalysisStatus.DONE;
    }

    private void computeMethodModified() {
        int inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
        if (inMap == Level.DELAY) {
            int computed = methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
            if (computed != Level.DELAY)
                methodAnalysis.setProperty(VariableProperty.MODIFIED_METHOD, computed);
        }
    }

    private void computeMethodImmutable() {
        int inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.IMMUTABLE);
        if (inMap == Level.DELAY) {
            ParameterizedType returnType = methodInspection.getReturnType();
            int immutable = returnType.defaultImmutable(analyserContext);
            if (immutable == ParameterizedType.TYPE_ANALYSIS_NOT_AVAILABLE) {
                messages.add(Message.newMessage(new Location(methodInfo), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                        returnType.toString()));
            } else if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                methodAnalysis.setProperty(VariableProperty.IMMUTABLE, immutable);
            }
        }
    }

    private void computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        int inMap = builder.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        if (inMap == Level.DELAY) {
            int value;
            ParameterizedType type = builder.getParameterInfo().parameterizedType;
            if (Primitives.isPrimitiveExcludingVoid(type)) {
                value = MultiLevel.INDEPENDENT;
            } else {
                // @Modified needs to be marked explicitly
                int modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.MODIFIED_METHOD);
                if (modifiedMethod == Level.TRUE) {
                    TypeInfo bestType = type.bestTypeInfo();
                    if (bestType != null) {
                        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                        if(typeAnalysis != null) {
                            int immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
                            if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
                                int independent = analyserContext.getTypeAnalysis(bestType).getProperty(VariableProperty.INDEPENDENT);
                                if (independent == MultiLevel.INDEPENDENT) {
                                    value = MultiLevel.INDEPENDENT;
                                } else {
                                    value = MultiLevel.DEPENDENT_1;
                                }
                            } else {
                                value = MultiLevel.DEPENDENT;
                            }
                        } else {
                            value = MultiLevel.DEPENDENT;
                        }
                    } else {
                        // unbound type parameter
                        value = MultiLevel.DEPENDENT_1;
                    }
                } else {
                    value = MultiLevel.INDEPENDENT;
                }
            }
            builder.setProperty(VariableProperty.INDEPENDENT, value);
        }
    }

    private void computeMethodIndependent() {
        int inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.INDEPENDENT);
        int finalValue;
        if (inMap == Level.DELAY) {
            int worstOverParameters = methodInfo.methodInspection.get().getParameters().stream()
                    .mapToInt(pi -> analyserContext.getParameterAnalysis(pi)
                            .getParameterProperty(analyserContext, pi, VariableProperty.INDEPENDENT))
                    .min().orElse(MultiLevel.INDEPENDENT);
            int returnValue;
            if (methodInfo.isConstructor || methodInfo.isVoid()) {
                returnValue = MultiLevel.INDEPENDENT;
            } else {
                TypeInfo bestType = methodInfo.returnType().bestTypeInfo();
                if (bestType != null) {
                    int immutable = methodAnalysis.getMethodProperty(analyserContext, VariableProperty.IMMUTABLE);
                    if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {

                        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
                        if (typeAnalysis != null) {
                            returnValue = typeAnalysis.getTypeProperty(VariableProperty.INDEPENDENT);
                        } else {
                            messages.add(Message.newMessage(new Location(methodInfo),
                                    Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE, bestType.fullyQualifiedName));
                            returnValue = MultiLevel.DEPENDENT;
                        }

                    } else {
                        returnValue = MultiLevel.DEPENDENT;
                    }
                } else {
                    // unbound type parameter T, or unbound with array T[], T[][]
                    returnValue = MultiLevel.DEPENDENT_1;
                }
            }
            finalValue = Math.min(worstOverParameters, returnValue);
            methodAnalysis.setProperty(VariableProperty.INDEPENDENT, finalValue);
        } else {
            finalValue = inMap;
        }
        int overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPublic())
                .map(analyserContext::getMethodAnalysis)
                .mapToInt(ma -> ma.getMethodProperty(analyserContext, VariableProperty.INDEPENDENT))
                .min().orElse(finalValue);
        if (finalValue < overloads) {
            messages.add(Message.newMessage(new Location(methodInfo),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT, MultiLevel.niceIndependent(finalValue) + " instead of " +
                            MultiLevel.niceIndependent(overloads)));
        }
    }

    private Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> collectAnnotations() {
        Map<WithInspectionAndAnalysis, Map<AnnotationExpression, List<MethodInfo>>> map = new HashMap<>();

        Map<AnnotationExpression, List<MethodInfo>> methodMap = new HashMap<>();
        map.put(methodInfo, methodMap);

        Stream.concat(Stream.of(methodInfo), methodInfo.methodResolution.get(methodInfo.fullyQualifiedName).overrides().stream()).forEach(mi -> {

            MethodInspection mii = mi.methodInspection.get();
            mii.getAnnotations().forEach(annotationExpression -> SMapList.add(methodMap, annotationExpression, mi));

            mii.getParameters().forEach(parameterInfo -> {
                Map<AnnotationExpression, List<MethodInfo>> parameterMap = map.computeIfAbsent(parameterInfo, k -> new HashMap<>());
                parameterInfo.parameterInspection.get().getAnnotations().forEach(annotationExpression ->
                        SMapList.add(parameterMap, annotationExpression, mi));
            });
        });

        map.forEach(this::checkContradictions);
        return map;
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
