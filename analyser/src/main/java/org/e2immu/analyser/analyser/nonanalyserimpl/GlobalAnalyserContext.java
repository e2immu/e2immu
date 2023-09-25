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
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;
import org.e2immu.support.SetOnceMap;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalAnalyserContext implements AnalyserContext {

    private final SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses;
    private final SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses;
    private final SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses;
    private final SetOnceMap<ParameterInfo, ParameterAnalysis> parameterAnalyses;

    private final Primitives primitives;
    private final boolean inAnnotatedAPIAnalysis;
    private final Configuration configuration;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    private final Map<String, TypeAnalysis> hardCodedTypes;
    private final Map<String, MethodAnalysis> hardCodedMethods;
    private final Map<String, ParameterAnalysis> hardCodedParameters;

    public GlobalAnalyserContext(Primitives primitives,
                                 Configuration configuration,
                                 ImportantClasses importantClasses,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                 boolean inAnnotatedAPIAnalysis) {
        this(primitives, configuration, importantClasses, e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis,
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    private GlobalAnalyserContext(Primitives primitives,
                                  Configuration configuration,
                                  ImportantClasses importantClasses,
                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                  boolean inAnnotatedAPIAnalysis,
                                  SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses,
                                  SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses,
                                  SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses,
                                  SetOnceMap<ParameterInfo, ParameterAnalysis> parameterAnalyses) {
        this.primitives = primitives;
        this.configuration = configuration;
        this.importantClasses = importantClasses;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inAnnotatedAPIAnalysis = inAnnotatedAPIAnalysis;
        this.typeAnalyses = typeAnalyses;
        this.methodAnalyses = methodAnalyses;
        this.fieldAnalyses = fieldAnalyses;
        this.parameterAnalyses = parameterAnalyses;

        hardCodedTypes = createHardCodedTypeAnalysis();
        hardCodedMethods = createHardCodedMethodAnalysis();
        hardCodedParameters = createHardCodedParameterAnalysis();
    }

    public enum HardCoded {
        IMMUTABLE(true), IMMUTABLE_HC(true),
        MUTABLE_NOT_CONTAINER(false),
        MUTABLE_CONTAINER(false),
        NO(false),
        NON_MODIFIED(false),
        NOT_MODIFIED_PARAM(false);

        public final boolean eraseDependencies;
        HardCoded(boolean eraseDependencies) {
            this.eraseDependencies = eraseDependencies;
        }
    }

    public static Map<String, HardCoded> HARDCODED_TYPES = Collections.unmodifiableMap(new HashMap<>() {{
        // FIXME purpose: breaking dep cycles, default values; see also noDelay in SMA

        put("java.lang.Annotation", HardCoded.IMMUTABLE_HC);
        put("java.lang.Enum", HardCoded.IMMUTABLE_HC);
        put("java.lang.Object", HardCoded.IMMUTABLE_HC);
        put("java.io.Serializable", HardCoded.IMMUTABLE_HC);
        put("java.util.Comparator", HardCoded.IMMUTABLE_HC);
        put("java.util.Optional", HardCoded.IMMUTABLE_HC);

        put("java.lang.CharSequence", HardCoded.IMMUTABLE_HC);
        put("java.lang.Class", HardCoded.IMMUTABLE);
        put("java.lang.Module", HardCoded.IMMUTABLE);
        put("java.lang.Package", HardCoded.IMMUTABLE);
        put("java.lang.constant.Constable", HardCoded.IMMUTABLE_HC);
        put("java.lang.constant.ConstantDesc", HardCoded.IMMUTABLE_HC);
     //   put("java.lang.constant.DynamicConstantDesc", HardCoded.IMMUTABLE);
     //   put("java.lang.Thread", HardCoded.IMMUTABLE_HC);
      //  put("java.nio.charset.CharsetDecoder", HardCoded.IMMUTABLE);
     //   put("java.lang.invoke.MethodHandles.Lookup", HardCoded.IMMUTABLE);

        // FIXME mutable containers a t m retain their dependencies -> do not help in cycle breaking (List, Set!)
   /*     put("java.lang.StringBuffer", HardCoded.MUTABLE_CONTAINER);
        put("java.lang.StringBuilder", HardCoded.MUTABLE_CONTAINER);
        put("java.io.PrintStream", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.io.PrintWriter", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.io.ObjectInputStream", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.io.ObjectOutputStream", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.util.Set", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.util.List", HardCoded.MUTABLE_CONTAINER); // Throwable
        put("java.lang.AbstractStringBuilder", HardCoded.MUTABLE_CONTAINER);

        put("java.util.function.Consumer", HardCoded.MUTABLE_NOT_CONTAINER); // Optional
        put("java.util.function.Predicate", HardCoded.MUTABLE_NOT_CONTAINER); // Optional
        put("java.util.function.Function", HardCoded.MUTABLE_NOT_CONTAINER); // Optional
        put("java.util.function.Supplier", HardCoded.MUTABLE_CONTAINER); // Optional
        put("java.lang.Runnable", HardCoded.MUTABLE_CONTAINER); // Optional
*/
        put("java.util.Map", HardCoded.MUTABLE_CONTAINER); // ClassValue
        put("java.util.AbstractMap", HardCoded.MUTABLE_CONTAINER); // ClassValue
        put("java.util.WeakHashMap", HardCoded.MUTABLE_CONTAINER); // ClassValue
        put("java.lang.ref.WeakReference", HardCoded.MUTABLE_CONTAINER); // ClassValue

        put("java.util.Collection", HardCoded.MUTABLE_CONTAINER); //  companion

        put("java.lang.Boolean", HardCoded.IMMUTABLE);
        put("java.lang.Byte", HardCoded.IMMUTABLE);
        put("java.lang.Character", HardCoded.IMMUTABLE);
        put("java.lang.Double", HardCoded.IMMUTABLE);
        put("java.lang.Float", HardCoded.IMMUTABLE);
        put("java.lang.Integer", HardCoded.IMMUTABLE);
        put("java.lang.Long", HardCoded.IMMUTABLE);
        put("java.lang.Short", HardCoded.IMMUTABLE);
        put("java.lang.String", HardCoded.IMMUTABLE);
        put("java.lang.Void", HardCoded.IMMUTABLE);
        put("java.lang.Number", HardCoded.IMMUTABLE);

        put("boolean", HardCoded.IMMUTABLE);
        put("byte", HardCoded.IMMUTABLE);
        put("char", HardCoded.IMMUTABLE);
        put("double", HardCoded.IMMUTABLE);
        put("float", HardCoded.IMMUTABLE);
        put("int", HardCoded.IMMUTABLE);
        put("long", HardCoded.IMMUTABLE);
        put("short", HardCoded.IMMUTABLE);
        put("void", HardCoded.IMMUTABLE);
        put("java.lang.Throwable", HardCoded.MUTABLE_NOT_CONTAINER);
        put("org.e2immu.annotatedapi.AnnotatedAPI", HardCoded.IMMUTABLE_HC);
    }});

    public static Map<String, List<String>> PARAMETER_ANALYSES = Map.of("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)",
            List.of("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean):0:test"));

    public static Map<String, HardCoded> HARDCODED_METHODS = Collections.unmodifiableMap(new HashMap<>() {{
        //    put("java.util.Comparator.compare(T,T)", HardCoded.NON_MODIFIED);
        put("java.lang.CharSequence.length()", HardCoded.NON_MODIFIED);
        put("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)", HardCoded.NON_MODIFIED);
        //   put("java.lang.CharSequence.isEmpty()", HardCoded.NON_MODIFIED);
        //    put("java.lang.CharSequence.charAt(int)", HardCoded.NON_MODIFIED);
        //    put("java.lang.Object.equals(Object)", HardCoded.NON_MODIFIED);
        //    put("java.lang.Comparable.compareTo(T)", HardCoded.NON_MODIFIED);
            put("java.util.Collection.size()", HardCoded.NON_MODIFIED);
            put("java.util.Map.size()", HardCoded.NON_MODIFIED);//companion of LinkedHashMap
    }});

    public static Map<String, HardCoded> HARDCODED_PARAMETERS = Collections.unmodifiableMap(new HashMap<>() {{
        //   put("java.lang.String.startsWith(String):0:prefix", HardCoded.NOT_MODIFIED_PARAM);
        //    put("java.lang.String.endsWith(String):0:suffix", HardCoded.NOT_MODIFIED_PARAM);
        //        put("java.lang.String.contains(CharSequence):0:s", HardCoded.NOT_MODIFIED_PARAM);
        //     put("java.lang.CharSequence.charAt(int):0:i", HardCoded.NOT_MODIFIED_PARAM);
        //    put("java.lang.Object.equals(Object):0:obj", HardCoded.NOT_MODIFIED_PARAM);
        //    put("java.lang.Comparable.compareTo(T):0:t", HardCoded.NOT_MODIFIED_PARAM);
        put("org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean):0:test", HardCoded.NOT_MODIFIED_PARAM);
    }});

    private Map<String, TypeAnalysis> createHardCodedTypeAnalysis() {
        return HARDCODED_TYPES.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> hardCodedTypeAnalysis(e.getKey(), e.getValue())));
    }

    private Map<String, MethodAnalysis> createHardCodedMethodAnalysis() {
        return HARDCODED_METHODS.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> hardCodedMethodAnalysis(e.getKey(), e.getValue())));
    }

    private Map<String, ParameterAnalysis> createHardCodedParameterAnalysis() {
        return HARDCODED_PARAMETERS.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                e -> hardCodedParameterAnalysis(e.getKey(), e.getValue())));
    }

    private ParameterAnalysis hardCodedParameterAnalysis(String fullyQualifiedName, HardCoded hardCoded) {
        return new ParameterAnalysis() {
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
                HardCoded hc = HARDCODED_PARAMETERS.get(fullyQualifiedName);
                return switch (hc) {
                    case NOT_MODIFIED_PARAM -> switch (property) {
                        case MODIFIED_VARIABLE -> DV.FALSE_DV;
                        case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                        case NOT_NULL_PARAMETER -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                        case CONTAINER_RESTRICTION -> MultiLevel.NOT_CONTAINER_DV;
                        default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
                    };
                    default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
                };
            }

            @Override
            public Location location(Stage stage) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification, boolean acceptVerifyAsContracted, Collection<AnnotationExpression> annotations, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private MethodAnalysis hardCodedMethodAnalysis(String fullyQualifiedName, HardCoded hardCoded) {
        return new MethodAnalysis() {
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
                for (String fqn: fqns) {
                    ParameterAnalysis pa = hardCodedParameters.get(fqn);
                    if(pa == null) throw new UnsupportedOperationException("Cannot find "+fqn);
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
                HardCoded hc = HARDCODED_METHODS.get(fullyQualifiedName);
                return switch (hc) {
                    case NON_MODIFIED -> switch (property) {
                        case FLUENT, IDENTITY, MODIFIED_METHOD, TEMP_MODIFIED_METHOD, MODIFIED_METHOD_ALT_TEMP,
                                FINALIZER, CONSTANT, STATIC_SIDE_EFFECTS -> DV.FALSE_DV;
                        case IGNORE_MODIFICATIONS -> MultiLevel.NOT_IGNORE_MODS_DV;
                        case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                        case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                        default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
                    };
                    default -> throw new UnsupportedOperationException(fullyQualifiedName + ": " + property);
                };
            }

            @Override
            public Location location(Stage stage) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification, boolean acceptVerifyAsContracted, Collection<AnnotationExpression> annotations, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private TypeAnalysis hardCodedTypeAnalysis(String fullyQualifiedName, HardCoded hardCoded) {
        return new TypeAnalysis() {

            @Override
            public TypeInfo getTypeInfo() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<FieldReference, Expression> getApprovedPreconditionsFinalFields() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Map<FieldReference, Expression> getApprovedPreconditionsImmutable() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean approvedPreconditionsImmutableIsEmpty() {
                throw new UnsupportedOperationException();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<FieldInfo> getEventuallyImmutableFields() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
                throw new UnsupportedOperationException();
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
                return DV.fromBoolDv(hardCoded == HardCoded.IMMUTABLE_HC);
            }

            @Override
            public SetOfTypes getHiddenContentTypes() {
                throw new UnsupportedOperationException();
            }

            @Override
            public CausesOfDelay hiddenContentDelays() {
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
                HardCoded hc = HARDCODED_TYPES.get(fullyQualifiedName);
                return switch (hc) {
                    case IMMUTABLE -> switch (property) {
                        case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
                        case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                        case CONTAINER -> MultiLevel.CONTAINER_DV;
                        default -> throw new UnsupportedOperationException();
                    };
                    case IMMUTABLE_HC -> switch (property) {
                        case IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
                        case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                        case CONTAINER -> MultiLevel.CONTAINER_DV;
                        default -> throw new UnsupportedOperationException();
                    };
                    case MUTABLE_NOT_CONTAINER -> switch (property) {
                        case IMMUTABLE -> MultiLevel.MUTABLE_DV;
                        case INDEPENDENT -> MultiLevel.DEPENDENT_DV;
                        case CONTAINER -> MultiLevel.NOT_CONTAINER_DV;
                        default -> throw new UnsupportedOperationException();
                    };
                    case MUTABLE_CONTAINER -> switch (property) {
                        case IMMUTABLE -> MultiLevel.MUTABLE_DV;
                        case INDEPENDENT -> MultiLevel.DEPENDENT_DV;
                        case CONTAINER -> MultiLevel.CONTAINER_DV;
                        default -> throw new UnsupportedOperationException();
                    };
                    default -> throw new UnsupportedOperationException();
                };
            }

            @Override
            public Location location(Stage stage) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Messages fromAnnotationsIntoProperties(Analyser.AnalyserIdentification analyserIdentification, boolean acceptVerifyAsContracted, Collection<AnnotationExpression> annotations, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
                throw new UnsupportedOperationException();
            }
        };
    }

    public GlobalAnalyserContext with(boolean inAnnotatedAPIAnalysis) {
        return new GlobalAnalyserContext(primitives, configuration, importantClasses,
                e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis, typeAnalyses, methodAnalyses, fieldAnalyses,
                parameterAnalyses);
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
        if (methodAnalyses.isSet(methodInfo)) return methodAnalyses.get(methodInfo);
        MethodAnalysis methodAnalysis = hardCodedMethods.get(methodInfo.fullyQualifiedName);
        if (methodAnalysis == null) {
            throw new UnsupportedOperationException("Cannot find method analysis of " + methodInfo);
        }
        return methodAnalysis;
    }

    @Override
    public MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        if (methodAnalyses.isSet(methodInfo)) return methodAnalyses.get(methodInfo);
        return hardCodedMethods.get(methodInfo.fullyQualifiedName);
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        if (parameterAnalyses.isSet(parameterInfo)) return parameterAnalyses.get(parameterInfo);
        ParameterAnalysis pa = hardCodedParameters.get(parameterInfo.fullyQualifiedName);
        if (pa == null) {
            throw new UnsupportedOperationException("Cannot find parameter analysis of " + parameterInfo);
        }
        return pa;
    }

    @Override
    public ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        if (parameterAnalyses.isSet(parameterInfo)) return parameterAnalyses.get(parameterInfo);
        return hardCodedParameters.get(parameterInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        if (typeAnalyses.isSet(typeInfo)) {
            return typeAnalyses.get(typeInfo);
        }
        TypeAnalysis typeAnalysis = hardCodedTypes.get(typeInfo.fullyQualifiedName);
        if (typeAnalysis == null) {
            throw new UnsupportedOperationException("Cannot find type analysis of " + typeInfo);
        }
        return typeAnalysis;
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        if (typeAnalyses.isSet(typeInfo)) {
            return typeAnalyses.getOrDefaultNull(typeInfo);
        }
        return hardCodedTypes.get(typeInfo.fullyQualifiedName);
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        return fieldAnalyses.get(fieldInfo);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        throw new UnsupportedOperationException();
    }

    public void write(Analysis analysis) {
        if (analysis instanceof TypeAnalysis typeAnalysis) {
            typeAnalyses.put(typeAnalysis.getTypeInfo(), typeAnalysis);
        } else if (analysis instanceof MethodAnalysis methodAnalysis) {
            methodAnalyses.put(methodAnalysis.getMethodInfo(), methodAnalysis);
        } else if (analysis instanceof FieldAnalysis fieldAnalysis) {
            fieldAnalyses.put(fieldAnalysis.getFieldInfo(), fieldAnalysis);
        } else if (analysis instanceof ParameterAnalysis parameterAnalysis) {
            parameterAnalyses.put(parameterAnalysis.getParameterInfo(), parameterAnalysis);
        } else throw new UnsupportedOperationException();
    }
}
