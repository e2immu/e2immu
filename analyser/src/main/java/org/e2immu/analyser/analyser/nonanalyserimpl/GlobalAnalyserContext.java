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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.impl.shallow.ShallowFieldAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.ShallowMethodAnalyser;
import org.e2immu.analyser.analyser.impl.shallow.ShallowTypeAnalyser;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalAnalyserContext implements AnalyserContext {

    private final SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses;
    private final SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses;
    private final SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses;

    private final Primitives primitives;
    private final boolean inAnnotatedAPIAnalysis;
    private final Configuration configuration;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    private final Map<String, TypeAnalysis> hardCodedTypes;
    private final Map<String, MethodAnalysis> hardCodedMethods;
    private final Map<String, ParameterAnalysis> hardCodedParameters;

    private final TypeContext typeContext;
    private final FlipSwitch onDemandMode = new FlipSwitch();

    public GlobalAnalyserContext(TypeContext typeContext,
                                 Configuration configuration,
                                 ImportantClasses importantClasses,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                 boolean inAnnotatedAPIAnalysis) {
        this(typeContext, configuration, importantClasses, e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis,
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    private GlobalAnalyserContext(TypeContext typeContext,
                                  Configuration configuration,
                                  ImportantClasses importantClasses,
                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                  boolean inAnnotatedAPIAnalysis,
                                  SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses,
                                  SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses,
                                  SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses) {
        this.primitives = typeContext.getPrimitives();
        this.typeContext = typeContext;
        this.configuration = configuration;
        this.importantClasses = importantClasses;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inAnnotatedAPIAnalysis = inAnnotatedAPIAnalysis;
        this.typeAnalyses = typeAnalyses;
        this.methodAnalyses = methodAnalyses;
        this.fieldAnalyses = fieldAnalyses;

        hardCodedTypes = createHardCodedTypeAnalysis();
        hardCodedMethods = createHardCodedMethodAnalysis();
        hardCodedParameters = createHardCodedParameterAnalysis();
    }

    public void startOnDemandMode() {
        onDemandMode.set();
    }

    public static Map<String, List<String>> PARAMETER_ANALYSES = Map.of("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)",
            List.of("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean):0:test"));

    private static final Set<String> HARDCODED_METHODS = Set.of(
            "java.lang.CharSequence.length()",
            "org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)",
            "java.util.Collection.size()",
            "java.util.Map.size()");

    // occur in companions
    private static final Set<String> HARDCODED_PARAMETERS = Set.of("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean):0:test");

    private Map<String, TypeAnalysis> createHardCodedTypeAnalysis() {
        return TypeInfo.HARDCODED_TYPES.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> new HardCodedTypeAnalysis(e.getKey(), e.getValue())));
    }

    private Map<String, MethodAnalysis> createHardCodedMethodAnalysis() {
        return HARDCODED_METHODS.stream().collect(Collectors.toUnmodifiableMap(e -> e,
                HardCodedMethodAnalysis::new));
    }

    private Map<String, ParameterAnalysis> createHardCodedParameterAnalysis() {
        return HARDCODED_PARAMETERS.stream().collect(Collectors.toUnmodifiableMap(e -> e,
                HardCodedParameterAnalysis::new));
    }

    record HardCodedParameterAnalysis(String fullyQualifiedName) implements ParameterAnalysis {

        @Override
        public ParameterInfo getParameterInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnnotationExpression annotationGetOrDefaultNull(AnnotationExpression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void internalAllDoneCheck() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPropertyDelayWhenNotFinal(Property property, CausesOfDelay causes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DV getProperty(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapDelayWhenAbsent(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapNeverDelay(Property property) {
            return switch (property) {
                case MODIFIED_VARIABLE -> DV.FALSE_DV;
                case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                case NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                case CONTAINER_RESTRICTION -> MultiLevel.NOT_CONTAINER_DV;
                default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
            };
        }

        @Override
        public Location location(Stage stage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification,
                                                      boolean acceptVerifyAsContracted,
                                                      Collection<AnnotationExpression> annotations,
                                                      E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            throw new UnsupportedOperationException();
        }
    }

    class HardCodedMethodAnalysis implements MethodAnalysis {
        private final String fullyQualifiedName;

        public HardCodedMethodAnalysis(String fullyQualifiedName) {
            this.fullyQualifiedName = fullyQualifiedName;
        }

        @Override
        public MethodInfo getMethodInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression getSingleReturnValue() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ParameterAnalysis> getParameterAnalyses() {
            List<String> fqns = PARAMETER_ANALYSES.get(fullyQualifiedName);
            for (String fqn : fqns) {
                ParameterAnalysis pa = hardCodedParameters.get(fqn);
                if (pa == null) throw new UnsupportedOperationException("Cannot find " + fqn);
            }
            return fqns.stream().map(hardCodedParameters::get).toList();
        }

        @Override
        public Precondition getPreconditionForEventual() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Precondition getPrecondition() {
            return Precondition.empty(primitives);
        }

        @Override
        public Set<PostCondition> getPostConditions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> indicesOfEscapesNotInPreOrPostConditions() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommutableData getCommutableData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CausesOfDelay eventualStatus() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CausesOfDelay preconditionStatus() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ParSeq<ParameterInfo> getParallelGroups() {
            return null;
        }

        @Override
        public List<Expression> sortAccordingToParallelGroupsAndNaturalOrder(List<Expression> parameterExpressions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markFirstIteration() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasBeenAnalysedUpToIteration0() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FieldInfo getSetField() {
            throw new UnsupportedOperationException();
        }

        @Override
        public GetSetEquivalent getSetEquivalent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnnotationExpression annotationGetOrDefaultNull(AnnotationExpression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void internalAllDoneCheck() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPropertyDelayWhenNotFinal(Property property, CausesOfDelay causes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DV getProperty(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapDelayWhenAbsent(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapNeverDelay(Property property) {
            return switch (property) {
                case FLUENT, IDENTITY, MODIFIED_METHOD, TEMP_MODIFIED_METHOD, MODIFIED_METHOD_ALT_TEMP,
                        FINALIZER, CONSTANT, STATIC_SIDE_EFFECTS -> DV.FALSE_DV;
                case IGNORE_MODIFICATIONS -> MultiLevel.NOT_IGNORE_MODS_DV;
                case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                case CONTAINER -> MultiLevel.CONTAINER_DV;
                case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
                default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
            };
        }

        @Override
        public Location location(Stage stage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification,
                                                      boolean acceptVerifyAsContracted,
                                                      Collection<AnnotationExpression> annotations,
                                                      E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            throw new UnsupportedOperationException();
        }
    }

    private record HardCodedTypeAnalysis(String fullyQualifiedName, TypeInfo.HardCoded hardCoded) implements TypeAnalysis {


        @Override
        public TypeInfo getTypeInfo() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsFinalFields() {
            return Map.of();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsImmutable() {
            return Map.of();
        }

        @Override
        public boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean approvedPreconditionsImmutableIsEmpty() {
            return true;
        }

        @Override
        public Expression getApprovedPreconditions(boolean e2, FieldReference fieldInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
            return CausesOfDelay.EMPTY;
        }

        @Override
        public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
            return false;
        }

        @Override
        public Set<FieldInfo> getEventuallyImmutableFields() {
            return Set.of();
        }

        @Override
        public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
            return Set.of();
        }

        @Override
        public FieldInfo translateToVisibleField(FieldReference fieldReference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, MethodInfo> getAspects() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DV immutableDeterminedByTypeParameters() {
            return DV.fromBoolDv(hardCoded == TypeInfo.HardCoded.IMMUTABLE_HC);
        }

        @Override
        public SetOfTypes getHiddenContentTypes() {
            return SetOfTypes.EMPTY;
        }

        @Override
        public CausesOfDelay hiddenContentDelays() {
            return CausesOfDelay.EMPTY;
        }

        @Override
        public AnnotationExpression annotationGetOrDefaultNull(AnnotationExpression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void internalAllDoneCheck() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPropertyDelayWhenNotFinal(Property property, CausesOfDelay causes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DV getProperty(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapDelayWhenAbsent(Property property) {
            return getPropertyFromMapNeverDelay(property);
        }

        @Override
        public DV getPropertyFromMapNeverDelay(Property property) {
            TypeInfo.HardCoded hc = TypeInfo.HARDCODED_TYPES.get(fullyQualifiedName);
            return switch (hc) {
                case IMMUTABLE -> switch (property) {
                    case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
                    case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                    case CONTAINER -> MultiLevel.CONTAINER_DV;
                    case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                    default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
                };
                case IMMUTABLE_HC -> switch (property) {
                    case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
                    case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                    case CONTAINER -> MultiLevel.CONTAINER_DV;
                    case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                    default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
                };
                case IMMUTABLE_HC_INDEPENDENT_HC -> switch (property) {
                    case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
                    case INDEPENDENT -> MultiLevel.INDEPENDENT_HC_DV;
                    case CONTAINER -> MultiLevel.CONTAINER_DV;
                    case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                    default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
                };
                case MUTABLE_NOT_CONTAINER_DO_NOT_ERASE -> switch (property) {
                    case IMMUTABLE -> MultiLevel.MUTABLE_DV;
                    case INDEPENDENT -> MultiLevel.DEPENDENT_DV;
                    case CONTAINER -> MultiLevel.NOT_CONTAINER_DV;
                    case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                    default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
                };
                case MUTABLE_CONTAINER_DO_NOT_ERASE -> switch (property) {
                    case IMMUTABLE -> MultiLevel.MUTABLE_DV;
                    case INDEPENDENT -> MultiLevel.DEPENDENT_DV;
                    case CONTAINER -> MultiLevel.CONTAINER_DV;
                    case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> DV.FALSE_DV;
                    default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
                };
                default -> throw new UnsupportedOperationException();
            };
        }

        @Override
        public Location location(Stage stage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification,
                                                      boolean acceptVerifyAsContracted,
                                                      Collection<AnnotationExpression> annotations,
                                                      E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            throw new UnsupportedOperationException();
        }
    }

    public GlobalAnalyserContext with(boolean inAnnotatedAPIAnalysis) {
        GlobalAnalyserContext context = new GlobalAnalyserContext(typeContext, configuration, importantClasses,
                e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis, typeAnalyses, methodAnalyses, fieldAnalyses);
        context.onDemandMode.copy(this.onDemandMode);
        return context;
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return inAnnotatedAPIAnalysis;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        return Objects.requireNonNull(getMethodAnalysisNullWhenAbsent(methodInfo),
                "Cannot find method analysis of " + methodInfo);
    }

    @Override
    public MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        if (methodInfo.methodAnalysis.isSet()) return methodInfo.methodAnalysis.get();
        if (methodAnalyses.isSet(methodInfo)) return methodAnalyses.get(methodInfo);
        MethodAnalysis ma = hardCodedMethods.get(methodInfo.fullyQualifiedName);
        if (ma != null) return ma;
        if (onDemandMode.isSet()) {
            synchronized (methodAnalyses) {
                LOGGER.debug("On-demand shallow method analysis of {}", methodInfo);
                TypeAnalysis ownerTypeAnalysis = getTypeAnalysis(methodInfo.typeInfo);
                MethodInspection methodInspection = getMethodInspection(methodInfo);
                List<ParameterAnalysis> parameterAnalyses = methodInspection.getParameters().stream()
                        .map(pi -> new ParameterAnalysisImpl.Builder(primitives, this, pi))
                        .map(b -> (ParameterAnalysis) b)
                        .toList();
                MethodAnalysisImpl.Builder methodAnalysisBuilder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                        primitives, this, this, methodInfo, ownerTypeAnalysis, parameterAnalyses);
                ShallowMethodAnalyser shallowMethodAnalyser = new ShallowMethodAnalyser(methodInfo,
                        methodAnalysisBuilder, parameterAnalyses, this);
                shallowMethodAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                MethodAnalysis methodAnalysis = shallowMethodAnalyser.methodAnalysis.build();
                methodInfo.setAnalysis(methodAnalysis);
                methodAnalyses.put(methodInfo, methodAnalysis);
                return methodAnalysis;
            }
        }
        return null;
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return Objects.requireNonNull(getParameterAnalysisNullWhenAbsent(parameterInfo),
                "Cannot find parameter analysis of " + parameterInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        if (parameterInfo.parameterAnalysis.isSet()) {
            return parameterInfo.parameterAnalysis.get();
        }
        MethodAnalysis methodAnalysis = getMethodAnalysisNullWhenAbsent(parameterInfo.getMethod());
        if (methodAnalysis != null) {
            return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
        }
        return hardCodedParameters.get(parameterInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        return Objects.requireNonNull(getTypeAnalysisNullWhenAbsent(typeInfo),
                "Cannot find type analysis of " + typeInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        if (typeInfo.typeAnalysis.isSet()) {
            return typeInfo.typeAnalysis.get();
        }
        if (typeAnalyses.isSet(typeInfo)) {
            return typeAnalyses.get(typeInfo);
        }
        TypeAnalysis hardCoded = hardCodedTypes.get(typeInfo.fullyQualifiedName);
        if (hardCoded != null) {
            return hardCoded;
        }
        if (onDemandMode.isSet()) {
            synchronized (typeAnalyses) {
                LOGGER.debug("On-demand shallow type analysis of {}", typeInfo);
                ShallowTypeAnalyser shallowTypeAnalyser = new ShallowTypeAnalyser(typeInfo, typeInfo.primaryType(),
                        this);
                shallowTypeAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                TypeAnalysis typeAnalysis = shallowTypeAnalyser.typeAnalysis.build();
                typeInfo.setAnalysis(typeAnalysis);
                typeAnalyses.put(typeInfo, typeAnalysis);
                return typeAnalysis;
            }
        }
        return null;
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        if (fieldInfo.fieldAnalysis.isSet()) return fieldInfo.fieldAnalysis.get();
        FieldAnalysis fieldAnalysis = fieldAnalyses.getOrDefaultNull(fieldInfo);
        if (fieldAnalysis != null) return fieldAnalysis;
        if (onDemandMode.isSet()) {
            synchronized (fieldAnalyses) {
                LOGGER.debug("On-demand shallow field analysis of {}", fieldInfo);
                TypeAnalysis ownerTypeAnalysis = getTypeAnalysis(fieldInfo.owner);
                ShallowFieldAnalyser shallowFieldAnalyser = new ShallowFieldAnalyser(fieldInfo,
                        fieldInfo.owner.primaryType(), ownerTypeAnalysis, this);
                shallowFieldAnalyser.analyse(new Analyser.SharedState(0, BreakDelayLevel.NONE, null));
                FieldAnalysis fa = shallowFieldAnalyser.getFieldAnalysis();
                fieldInfo.setAnalysis(fa);
                fieldAnalyses.put(fieldInfo, fa);
                return fa;
            }
        }
        throw new UnsupportedOperationException("Cannot find field analysis for " + fieldInfo);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public void write(Analysis analysis) {
        if (analysis instanceof TypeAnalysis typeAnalysis) {
            typeAnalyses.put(typeAnalysis.getTypeInfo(), typeAnalysis);
        } else if (analysis instanceof MethodAnalysis methodAnalysis) {
            methodAnalyses.put(methodAnalysis.getMethodInfo(), methodAnalysis);
        } else if (analysis instanceof FieldAnalysis fieldAnalysis) {
            fieldAnalyses.put(fieldAnalysis.getFieldInfo(), fieldAnalysis);
        } else if (!(analysis instanceof ParameterAnalysis)) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        if (typeInfo.typeInspection.isSet()) return typeInfo.typeInspection.get();
        return typeContext.getTypeInspection(typeInfo);
    }
}
