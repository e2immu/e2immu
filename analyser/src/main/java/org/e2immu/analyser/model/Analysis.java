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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;
import org.e2immu.annotation.AnnotationType;

import java.lang.annotation.ElementType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class Analysis {
    public final SetOnceMap<AnnotationExpression, Boolean> annotations = new SetOnceMap<>();
    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);
    public final boolean hasBeenDefined;
    public final String simpleName; // for debugging purposes

    protected Analysis(boolean hasBeenDefined, String simpleName) {
        this.simpleName = simpleName;
        this.hasBeenDefined = hasBeenDefined;
    }

    // part of the streaming process, purely based on the annotations map
    public void peekIntoAnnotations(AnnotationExpression annotation, Set<TypeInfo> annotationsSeen, StringBuilder sb) {
        AnnotationType annotationType = e2immuAnnotation(annotation);
        if (annotationType != null) {
            // so we have one of our own annotations, and we know its type
            Boolean verified = annotations.getOtherwiseNull(annotation);
            if (verified != null) {
                boolean ok = verified && annotationType == AnnotationType.VERIFY
                        || !verified && annotationType == AnnotationType.VERIFY_ABSENT;
                annotationsSeen.add(annotation.typeInfo);
                if (ok) {
                    sb.append("/*OK*/");
                } else {
                    sb.append("/*FAIL*/");
                }
            } else {
                if (annotationType == AnnotationType.VERIFY) {
                    sb.append("/*FAIL:DELAYED*/");
                } else if (annotationType == AnnotationType.VERIFY_ABSENT) {
                    sb.append("/*OK:DELAYED*/");
                }
            }
        }
    }

    private static AnnotationType e2immuAnnotation(AnnotationExpression annotation) {
        if (annotation.typeInfo.fullyQualifiedName.startsWith("org.e2immu.annotation") && annotation.expressions.isSet()) {
            return annotation.extract("type", AnnotationType.VERIFY);
        }
        return null;
    }

    public abstract AnnotationMode annotationMode();

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, hasBeenDefined ? Level.DELAY : variableProperty.valueWhenAbsent(annotationMode()));
    }

    public abstract Pair<Boolean, Integer> getImmutablePropertyAndBetterThanFormal();

    protected int getPropertyAsIs(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY);
    }

    public void setProperty(VariableProperty variableProperty, int i) {
        if (variableProperty.canImprove) {
            properties.improve(variableProperty, i);
        } else {
            properties.put(variableProperty, i);
        }
    }

    public void setProperty(VariableProperty variableProperty, boolean b) {
        properties.put(variableProperty, Level.fromBool(b));
    }

    public void improveProperty(VariableProperty variableProperty, int i) {
        properties.improve(variableProperty, i);
    }

    public int minimalValue(VariableProperty variableProperty) {
        return Level.UNDEFINED;
    }

    public int maximalValue(VariableProperty variableProperty) {
        return Integer.MAX_VALUE;
    }

    public abstract Map<VariableProperty, AnnotationExpression> oppositesMap(E2ImmuAnnotationExpressions typeContext);

    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions typeContext) {
        ImmutableMap.Builder<VariableProperty, AnnotationExpression> minMapBuilder = new ImmutableMap.Builder<>();
        minMapBuilder.put(VariableProperty.FINAL, typeContext.effectivelyFinal.get());
        minMapBuilder.put(VariableProperty.FLUENT, typeContext.fluent.get());
        minMapBuilder.put(VariableProperty.IDENTITY, typeContext.identity.get());
        minMapBuilder.put(VariableProperty.INDEPENDENT, typeContext.independent.get());
        minMapBuilder.put(VariableProperty.UTILITY_CLASS, typeContext.utilityClass.get());
        minMapBuilder.put(VariableProperty.EXTENSION_CLASS, typeContext.extensionClass.get());
        minMapBuilder.put(VariableProperty.MODIFIED, typeContext.modified.get());
        minMapBuilder.put(VariableProperty.OUTPUT, typeContext.output.get());
        minMapBuilder.put(VariableProperty.SINGLETON, typeContext.singleton.get());

        for (Map.Entry<VariableProperty, AnnotationExpression> entry : minMapBuilder.build().entrySet()) {
            VariableProperty variableProperty = entry.getKey();
            AnnotationExpression annotationExpression = entry.getValue();
            int value = getProperty(variableProperty);
            int minimal = minimalValue(variableProperty);
            if (value == Level.FALSE) {
                annotations.put(annotationExpression, false);
            } else if (value == Level.TRUE && value > minimal) {
                annotations.put(annotationExpression, true);
            }
        }

        for (Map.Entry<VariableProperty, AnnotationExpression> entry : oppositesMap(typeContext).entrySet()) {
            VariableProperty variableProperty = entry.getKey();
            AnnotationExpression annotationExpression = entry.getValue();
            int value = getProperty(variableProperty);
            int maximal = maximalValue(variableProperty);
            if (value == Level.TRUE) {
                annotations.put(annotationExpression, false);
            } else if (value == Level.FALSE && Level.TRUE < maximal) {
                annotations.put(annotationExpression, true);
            }
        }

        boolean isNotAConstructorOrVoidMethod = isNotAConstructorOrVoidMethod();
        if (isNotAConstructorOrVoidMethod) {
            boolean isType = this instanceof TypeAnalysis;

            doImmutableContainer(typeContext, isType);

            boolean doNullable = !isType;

            // not null
            int minNotNull = minimalValue(VariableProperty.NOT_NULL);
            int notNull = getProperty(VariableProperty.NOT_NULL);
            if (notNull >= MultiLevel.EVENTUALLY_CONTENT2_NOT_NULL) {
                if (notNull > minNotNull) annotations.put(typeContext.notNull2.get(), true);
                if (doNullable) annotations.put(typeContext.nullable.get(), false);
            } else {
                if (notNull != Level.DELAY) annotations.put(typeContext.notNull2.get(), false);

                if (notNull >= MultiLevel.EVENTUALLY_CONTENT_NOT_NULL) {
                    if (notNull > minNotNull) annotations.put(typeContext.notNull1.get(), true);
                    if (doNullable) annotations.put(typeContext.nullable.get(), false);
                } else {
                    if (notNull > Level.DELAY) {
                        annotations.put(typeContext.notNull1.get(), false);
                    }
                    if (notNull >= MultiLevel.EVENTUAL && notNull > minNotNull) {
                        annotations.put(typeContext.notNull.get(), true);
                    } else if (notNull > Level.DELAY) {
                        annotations.put(typeContext.notNull.get(), false);
                    }
                    if (doNullable) {
                        int max = maximalValue(VariableProperty.NOT_NULL);
                        boolean nullablePresent = max != Level.FALSE && notNull < MultiLevel.EVENTUAL;
                        // a delay on notNull0 on a non-primitive will get nullable present
                        annotations.put(typeContext.nullable.get(), nullablePresent);
                    }
                }
            }
        }

        // size
        int minSize = minimalValue(VariableProperty.SIZE);
        int size = getProperty(VariableProperty.SIZE);
        if (size > minSize) {
            if (Level.haveEquals(size)) {
                annotations.put(sizeAnnotation(typeContext, "equals", Level.decodeSizeEquals(size)), true);
            } else {
                annotations.put(sizeAnnotation(typeContext, "min", Level.decodeSizeMin(size)), true);
            }
        }

        // size copy
        int minSizeCopy = minimalValue(VariableProperty.SIZE_COPY);
        int sizeCopy = getProperty(VariableProperty.SIZE_COPY);
        if (sizeCopy > minSizeCopy) {
            if (sizeCopy == Level.SIZE_COPY_MIN_TRUE) {
                annotations.put(sizeAnnotationTrue(typeContext, "copyMin"), true);
            } else if (sizeCopy == Level.SIZE_COPY_TRUE) {
                annotations.put(sizeAnnotationTrue(typeContext, "copy"), true);
            }
        }

        // precondition
        preconditionFromAnalysisToAnnotation(typeContext);
    }

    private void doImmutableContainer(E2ImmuAnnotationExpressions typeContext, boolean isType) {
        Pair<Boolean, Integer> pair = getImmutablePropertyAndBetterThanFormal();
        int immutable = pair.v;
        boolean betterThanFormal = pair.k;
        int container = getProperty(VariableProperty.CONTAINER);
        String mark;
        boolean eventual = this instanceof TypeAnalysis && ((TypeAnalysis) this).isEventual();
        if (eventual) {
            mark = ((TypeAnalysis) this).approvedPreconditions.stream().map(Map.Entry::getValue).collect(Collectors.joining(", "));
        } else mark = "";
        Map<Class<?>, Map<String, String>> map = GenerateAnnotationsImmutable.generate(immutable, container, isType, mark, betterThanFormal);
        for (Map.Entry<Class<?>, Map<String, String>> entry : map.entrySet()) {
            List<Expression> list;
            if (entry.getValue() == GenerateAnnotationsImmutable.TRUE) {
                list = List.of();
            } else {
                list = entry.getValue().entrySet().stream().map(e -> new MemberValuePair(e.getKey(), new StringConstant(e.getValue()))).collect(Collectors.toList());
            }
            AnnotationExpression expression = AnnotationExpression.fromAnalyserExpressions(
                    typeContext.getFullyQualified(entry.getKey().getCanonicalName()), list);
            annotations.put(expression, true);
        }
    }

    protected boolean isNotAConstructorOrVoidMethod() {
        return true;
    }

    protected void preconditionFromAnalysisToAnnotation(E2ImmuAnnotationExpressions typeContext) {
        // nothing to be done; override in method analysis
    }

    private AnnotationExpression sizeAnnotationTrue(E2ImmuAnnotationExpressions typeContext, String parameter) {
        return typeContext.size.get().copyWith(parameter, true);
    }

    private AnnotationExpression sizeAnnotation(E2ImmuAnnotationExpressions typeContext, String parameter, int value) {
        return typeContext.size.get().copyWith(parameter, value);
    }

    private final BiConsumer<VariableProperty, Integer> PUT = properties::put;
    private final BiConsumer<VariableProperty, Integer> OVERWRITE = properties::overwrite;

    public Messages fromAnnotationsIntoProperties(boolean hasBeenDefined,
                                                  List<AnnotationExpression> annotations,
                                                  E2ImmuAnnotationExpressions typeContext,
                                                  boolean overwrite) {
        Map<ElementType, Integer> notNullMap = new HashMap<>();
        int immutable = -1;
        boolean container = false;
        Messages messages = new Messages();
        BiConsumer<VariableProperty, Integer> method = overwrite ? OVERWRITE : PUT;
        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationType annotationType = e2immuAnnotation(annotationExpression);
            if (annotationType == AnnotationType.CONTRACT ||
                    // VERIFY is the default in annotated APIs, and non-default method declarations in interfaces...
                    !hasBeenDefined && annotationType == AnnotationType.VERIFY) {
                TypeInfo t = annotationExpression.typeInfo;
                if (typeContext.e1Immutable.get().typeInfo == t) {
                    immutable = Math.max(0, immutable);
                } else if (typeContext.mutable.get().typeInfo == t) {
                    immutable = -1;
                } else if (typeContext.e2Immutable.get().typeInfo == t) {
                    immutable = Math.max(1, immutable);
                } else if (typeContext.e2Container.get().typeInfo == t) {
                    immutable = Math.max(1, immutable);
                    container = true;
                } else if (typeContext.e1Container.get().typeInfo == t) {
                    immutable = Math.max(0, immutable);
                    container = true;
                } else if (typeContext.container.get().typeInfo == t) {
                    container = true;
                } else if (typeContext.modifiesArguments.get().typeInfo == t) {
                    container = false;
                } else if (typeContext.nullable.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, -1));
                } else if (typeContext.notNull.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 0));
                } else if (typeContext.notNull1.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 1));
                } else if (typeContext.notNull2.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 2));
                } else if (typeContext.notModified.get().typeInfo == t) {
                    method.accept(VariableProperty.MODIFIED, Level.FALSE);
                } else if (typeContext.modified.get().typeInfo == t) {
                    method.accept(VariableProperty.MODIFIED, Level.TRUE);
                } else if (typeContext.effectivelyFinal.get().typeInfo == t) {
                    method.accept(VariableProperty.FINAL, Level.TRUE);
                } else if (typeContext.variableField.get().typeInfo == t) {
                    method.accept(VariableProperty.FINAL, Level.FALSE);
                } else if (typeContext.constant.get().typeInfo == t) {
                    method.accept(VariableProperty.CONSTANT, Level.TRUE);
                } else if (typeContext.extensionClass.get().typeInfo == t) {
                    method.accept(VariableProperty.EXTENSION_CLASS, Level.TRUE);
                } else if (typeContext.fluent.get().typeInfo == t) {
                    method.accept(VariableProperty.FLUENT, Level.TRUE);
                } else if (typeContext.identity.get().typeInfo == t) {
                    method.accept(VariableProperty.IDENTITY, Level.TRUE);
                } else if (typeContext.ignoreModifications.get().typeInfo == t) {
                    method.accept(VariableProperty.IGNORE_MODIFICATIONS, Level.TRUE);
                } else if (typeContext.independent.get().typeInfo == t) {
                    method.accept(VariableProperty.INDEPENDENT, Level.TRUE);
                } else if (typeContext.dependent.get().typeInfo == t) {
                    method.accept(VariableProperty.INDEPENDENT, Level.FALSE);
                } else if (typeContext.mark.get().typeInfo == t) {
                    method.accept(VariableProperty.MARK, Level.TRUE);
                } else if (typeContext.only.get().typeInfo == t) {
                    method.accept(VariableProperty.ONLY, Level.TRUE);
                } else if (typeContext.output.get().typeInfo == t) {
                    method.accept(VariableProperty.OUTPUT, Level.TRUE);
                } else if (typeContext.singleton.get().typeInfo == t) {
                    method.accept(VariableProperty.SINGLETON, Level.TRUE);
                } else if (typeContext.utilityClass.get().typeInfo == t) {
                    method.accept(VariableProperty.UTILITY_CLASS, Level.TRUE);
                } else if (typeContext.linked.get().typeInfo == t) {
                    method.accept(VariableProperty.LINKED, Level.TRUE);
                } else if (typeContext.size.get().typeInfo == t) {
                    method.accept(VariableProperty.SIZE, extractSizeMin(messages, annotationExpression));
                    method.accept(VariableProperty.SIZE_COPY, extractSizeCopy(annotationExpression));
                } else if (typeContext.precondition.get().typeInfo == t) {
                    String value = annotationExpression.extract("value", "");
                    writePrecondition(value);
                } else throw new UnsupportedOperationException("TODO: " + t.fullyQualifiedName);
            }
        }
        if (container) {
            method.accept(VariableProperty.CONTAINER, Level.TRUE);
        }
        if (immutable >= 0) {
            int value;
            switch (immutable) {
                case 0:
                    value = MultiLevel.EFFECTIVELY_E1IMMUTABLE;
                    break;
                case 1:
                    value = MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            method.accept(VariableProperty.IMMUTABLE, value);
        }
        for (Map.Entry<ElementType, Integer> entry : notNullMap.entrySet()) {
            VariableProperty variableProperty;
            switch (entry.getKey()) {
                case FIELD:
                    variableProperty = VariableProperty.NOT_NULL_FIELDS;
                    break;
                case METHOD:
                    variableProperty = VariableProperty.NOT_NULL_METHODS;
                    break;
                case PARAMETER:
                    variableProperty = VariableProperty.NOT_NULL_PARAMETERS;
                    break;
                case TYPE:
                    variableProperty = VariableProperty.NOT_NULL;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            int nn;
            switch (entry.getValue()) {
                case -1:
                    nn = MultiLevel.NULLABLE;
                    break;
                case 0:
                    nn = MultiLevel.EFFECTIVELY_NOT_NULL;
                    break;
                case 1:
                    nn = MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;
                    break;
                case 2:
                    nn = MultiLevel.EFFECTIVELY_CONTENT2_NOT_NULL;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            method.accept(variableProperty, nn);
        }
        return messages;
    }

    protected void writePrecondition(String value) {
        throw new UnsupportedOperationException(); // only for methods, please override
    }

    /**
     * Values: -1 = absent; 0 = NOT A SIZE; 1 = min=0,(is a size);  2 = equals 0 (empty) ; 3 = min 1 (not empty); 4 = equals 1; 5 = min 2; 6 = equals 2
     *
     * @param annotationExpression the annotation
     * @return encoded value
     */
    public int extractSizeMin(Messages messages, AnnotationExpression annotationExpression) {
        Integer min = annotationExpression.extract("min", -1);
        if (min >= 0) {
            // min = 0 is FALSE; min = 1 means FALSE at level 1 (value 2), min = 2 means FALSE at level 2 (value 4)
            return Level.encodeSizeMin(min);
        }
        Boolean copy = annotationExpression.extract("copy", false);
        if (copy) return Level.DELAY;
        Boolean copyMin = annotationExpression.extract("copyMin", false);
        if (copyMin) return Level.DELAY;

        Integer equals = annotationExpression.extract("equals", -1);
        if (equals >= 0) {
            // equals 0 means TRUE at level 0, equals 1 means TRUE at level 1 (value 3)

            // @Size is the default
            return Level.encodeSizeEquals(equals);
        }
        // ignore! raise warning
        messages.add(Message.newMessage(location(), Message.SIZE_NEED_PARAMETER));
        return Level.DELAY;
    }

    protected abstract Location location();

    public static int extractSizeCopy(AnnotationExpression annotationExpression) {
        Boolean copy = annotationExpression.extract("copy", false);
        if (copy) return Level.SIZE_COPY_TRUE;
        Boolean copyMin = annotationExpression.extract("copyMin", false);
        if (copyMin) return Level.SIZE_COPY_MIN_TRUE;
        return Level.FALSE;
    }

    static void increaseTo(Map<ElementType, Integer> map, ElementType elementType, int value) {
        Integer current = map.get(elementType);
        map.put(elementType, Math.max(current == null ? 0 : current, value));
    }

    protected static final ElementType[] NOT_NULL_WHERE_ALL = {ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD};
    protected static final ElementType[] NOT_NULL_WHERE_TYPE = {ElementType.TYPE};

    protected Set<ElementType> extractWhere(AnnotationExpression annotationExpression) {
        ElementType[] elements = annotationExpression.extract("where", NOT_NULL_WHERE_TYPE);
        return Arrays.stream(elements).collect(Collectors.toSet());
    }

    public Map<VariableProperty, Integer> getProperties(Set<VariableProperty> properties) {
        Map<VariableProperty, Integer> res = new HashMap<>();
        for (VariableProperty property : properties) {
            int value = getProperty(property);
            res.put(property, value);
        }
        return res;
    }
}
