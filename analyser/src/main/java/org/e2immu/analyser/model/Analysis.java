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
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.IncrementalMap;
import org.e2immu.analyser.util.Lazy;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationType;

import java.lang.annotation.ElementType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public abstract class Analysis {
    public final SetOnceMap<AnnotationExpression, Boolean> annotations = new SetOnceMap<>();
    public final IncrementalMap<VariableProperty> properties = new IncrementalMap<>(Level::acceptIncrement);

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

    public int getProperty(VariableProperty variableProperty) {
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

    public void transferPropertiesToAnnotations(TypeContext typeContext, ToIntFunction<VariableProperty> minimalValue) {
        ImmutableMap.Builder<VariableProperty, AnnotationExpression> mapBuilder = new ImmutableMap.Builder<>();
        mapBuilder.put(VariableProperty.FINAL, typeContext.effectivelyFinal.get());
        mapBuilder.put(VariableProperty.FLUENT, typeContext.fluent.get());
        mapBuilder.put(VariableProperty.IDENTITY, typeContext.identity.get());
        mapBuilder.put(VariableProperty.INDEPENDENT, typeContext.independent.get());
        mapBuilder.put(VariableProperty.UTILITY_CLASS, typeContext.utilityClass.get());
        mapBuilder.put(VariableProperty.EXTENSION_CLASS, typeContext.extensionClass.get());
        mapBuilder.put(VariableProperty.NOT_MODIFIED, typeContext.notModified.get());
        mapBuilder.put(VariableProperty.OUTPUT, typeContext.output.get());
        mapBuilder.put(VariableProperty.SINGLETON, typeContext.singleton.get());

        for (Map.Entry<VariableProperty, AnnotationExpression> entry : mapBuilder.build().entrySet()) {
            VariableProperty variableProperty = entry.getKey();
            AnnotationExpression annotationExpression = entry.getValue();
            int value = getProperty(variableProperty);
            int minimal = minimalValue.applyAsInt(variableProperty);
            if (value == Level.FALSE) {
                annotations.put(annotationExpression, false);
            } else if (value == Level.TRUE && value > minimal) {
                annotations.put(annotationExpression, true);
            }
        }

        // container and immutable
        int container = getProperty(VariableProperty.CONTAINER);
        boolean haveContainer = container == Level.TRUE;
        boolean noContainer = container == Level.FALSE;
        int minContainer = minimalValue.applyAsInt(VariableProperty.CONTAINER);
        int minImmutable = minimalValue.applyAsInt(VariableProperty.IMMUTABLE);

        if (noContainer) annotations.put(typeContext.container.get(), false);
        int immutable = getProperty(VariableProperty.IMMUTABLE);
        if (Level.have(immutable, Level.E2IMMUTABLE)) {
            if (haveContainer) {
                if (immutable > minImmutable || container > minContainer) {
                    annotations.put(typeContext.e2Container.get(), true);
                }
            } else {
                if (noContainer) annotations.put(typeContext.e2Container.get(), false);
                if (immutable > minImmutable) annotations.put(typeContext.e2Immutable.get(), true);
            }
        } else if (Level.have(immutable, Level.E1IMMUTABLE)) {
            if (haveContainer) {
                if (immutable > minImmutable || container > minContainer) {
                    annotations.put(typeContext.e1Container.get(), true);
                }
            } else {
                if (noContainer) annotations.put(typeContext.e1Container.get(), false);
                if (immutable > minImmutable) annotations.put(typeContext.e1Immutable.get(), true);
            }
        } else if (haveContainer && container > minContainer) annotations.put(typeContext.container.get(), true);

        // not null
        int minNotNull = minimalValue.applyAsInt(VariableProperty.NOT_NULL);
        int notNull = getProperty(VariableProperty.NOT_NULL);
        int notNull2 = Level.value(notNull, Level.NOT_NULL_2);
        if (notNull2 == Level.TRUE) {
            if (notNull2 > minNotNull) annotations.put(typeContext.notNull2.get(), true);
        } else {
            if (notNull2 == Level.FALSE) annotations.put(typeContext.notNull2.get(), false);

            int notNull1 = Level.value(notNull, Level.NOT_NULL_1);
            if (notNull1 == Level.TRUE) {
                if (notNull1 > minNotNull) annotations.put(typeContext.notNull1.get(), true);
            } else {
                if (notNull1 == Level.FALSE) annotations.put(typeContext.notNull1.get(), false);
                int notNull0 = Level.value(notNull, Level.NOT_NULL);
                if (notNull0 == Level.TRUE && notNull0 > minNotNull) {
                    annotations.put(typeContext.notNull.get(), true);
                }
                if (notNull0 == Level.FALSE) {
                    annotations.put(typeContext.notNull.get(), false);
                }
            }
        }

    }

    private final BiConsumer<VariableProperty, Integer> PUT = properties::put;
    private final BiConsumer<VariableProperty, Integer> OVERWRITE = properties::overwrite;

    public void fromAnnotationsIntoProperties(boolean hasBeenDefined,
                                              List<AnnotationExpression> annotations,
                                              TypeContext typeContext,
                                              boolean overwrite) {
        Map<ElementType, Integer> notNullMap = new HashMap<>();
        int immutable = -1;
        boolean container = false;
        BiConsumer<VariableProperty, Integer> method = overwrite ? OVERWRITE : PUT;
        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationType annotationType = e2immuAnnotation(annotationExpression);
            if (annotationType == AnnotationType.CONTRACT ||
                    // VERIFY is the default in annotated APIs, and non-default method declarations in interfaces...
                    !hasBeenDefined && annotationType == AnnotationType.VERIFY) {
                TypeInfo t = annotationExpression.typeInfo;
                if (typeContext.e1Immutable.get().typeInfo == t) {
                    immutable = Math.max(0, immutable);
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
                } else if (typeContext.notNull.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 0));
                } else if (typeContext.notNull1.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 1));
                } else if (typeContext.notNull2.get().typeInfo == t) {
                    extractWhere(annotationExpression).forEach(et -> increaseTo(notNullMap, et, 2));
                } else if (typeContext.notModified.get().typeInfo == t) {
                    method.accept(VariableProperty.NOT_MODIFIED, Level.TRUE);
                } else if (typeContext.effectivelyFinal.get().typeInfo == t) {
                    method.accept(VariableProperty.FINAL, Level.TRUE);
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
                } else throw new UnsupportedOperationException("TODO: " + t.fullyQualifiedName);
            }
        }
        if (container) {
            method.accept(VariableProperty.CONTAINER, Level.TRUE);
        }
        if (immutable >= 0) {
            method.accept(VariableProperty.IMMUTABLE, Level.compose(Level.TRUE, immutable));
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
            method.accept(variableProperty, Level.compose(Level.TRUE, entry.getValue()));
        }
    }

    static void increaseTo(Map<ElementType, Integer> map, ElementType elementType, int value) {
        Integer current = map.get(elementType);
        map.put(elementType, Math.max(current == null ? 0 : current, value));
    }

    private static final ElementType[] NOT_NULL_WHERE = {ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD};

    static List<ElementType> extractWhere(AnnotationExpression annotationExpression) {
        ElementType[] elements = annotationExpression.extract("where", NOT_NULL_WHERE);
        return Arrays.stream(elements).collect(Collectors.toList());
    }
}
