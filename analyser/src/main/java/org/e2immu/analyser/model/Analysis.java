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
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    public boolean propertyTrue(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY) == Level.TRUE;
    }

    public int getProperty(VariableProperty variableProperty) {
        return properties.getOtherwise(variableProperty, Level.DELAY);
    }

    public void setProperty(VariableProperty variableProperty, int i) {
        properties.put(variableProperty, i);
    }

    public void setProperty(VariableProperty variableProperty, boolean b) {
        properties.put(variableProperty, Level.fromBool(b));
    }

    public void improveProperty(VariableProperty variableProperty, int i) {
        properties.improve(variableProperty, i);
    }

    public void transferPropertiesToAnnotations(TypeContext typeContext) {
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
            if (value == Level.FALSE) {
                annotations.put(annotationExpression, false);
            } else if (value == Level.TRUE) {
                annotations.put(annotationExpression, true);
            }
        }
        boolean container = getProperty(VariableProperty.CONTAINER) == Level.TRUE;
        boolean noContainer = getProperty(VariableProperty.CONTAINER) == Level.FALSE;
        if (container) annotations.put(typeContext.container.get(), true);
        if (noContainer) annotations.put(typeContext.container.get(), false);
        int immutable = getProperty(VariableProperty.IMMUTABLE);
        if (Level.have(immutable, 0)) {
            if (container) annotations.put(typeContext.e1Container.get(), true);
            if (noContainer) annotations.put(typeContext.e1Container.get(), false);
            annotations.put(typeContext.e1Immutable.get(), true);
        }
        if (Level.have(immutable, 1)) {
            if (container) annotations.put(typeContext.e2Container.get(), true);
            if (noContainer) annotations.put(typeContext.e2Container.get(), false);
            annotations.put(typeContext.e2Immutable.get(), true);
        }
    }

    public void fromAnnotationsIntoProperties(List<AnnotationExpression> annotations, TypeContext typeContext) {
        int notNull = -1;
        int immutable = -1;
        boolean container = false;
        for (AnnotationExpression annotationExpression : annotations) {
            AnnotationType annotationType = e2immuAnnotation(annotationExpression);
            if (annotationType == AnnotationType.CONTRACT) {
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
                    notNull = Math.max(0, notNull);
                } else if (typeContext.notNull1.get().typeInfo == t) {
                    notNull = Math.max(1, notNull);
                } else if (typeContext.notNull2.get().typeInfo == t) {
                    notNull = Math.max(2, notNull);
                } else if (typeContext.notModified.get().typeInfo == t) {
                    properties.put(VariableProperty.NOT_MODIFIED, Level.TRUE);
                } else if (typeContext.effectivelyFinal.get().typeInfo == t) {
                    properties.put(VariableProperty.FINAL, Level.TRUE);
                } else if (typeContext.constant.get().typeInfo == t) {
                    properties.put(VariableProperty.CONSTANT, Level.TRUE);
                } else if (typeContext.extensionClass.get().typeInfo == t) {
                    properties.put(VariableProperty.EXTENSION_CLASS, Level.TRUE);
                } else if (typeContext.fluent.get().typeInfo == t) {
                    properties.put(VariableProperty.FLUENT, Level.TRUE);
                } else if (typeContext.identity.get().typeInfo == t) {
                    properties.put(VariableProperty.IDENTITY, Level.TRUE);
                } else if (typeContext.ignoreModifications.get().typeInfo == t) {
                    properties.put(VariableProperty.IGNORE_MODIFICATIONS, Level.TRUE);
                } else if (typeContext.independent.get().typeInfo == t) {
                    properties.put(VariableProperty.INDEPENDENT, Level.TRUE);
                } else if (typeContext.mark.get().typeInfo == t) {
                    properties.put(VariableProperty.MARK, Level.TRUE);
                } else if (typeContext.only.get().typeInfo == t) {
                    properties.put(VariableProperty.ONLY, Level.TRUE);
                } else if (typeContext.output.get().typeInfo == t) {
                    properties.put(VariableProperty.OUTPUT, Level.TRUE);
                } else if (typeContext.singleton.get().typeInfo == t) {
                    properties.put(VariableProperty.SINGLETON, Level.TRUE);
                } else if (typeContext.utilityClass.get().typeInfo == t) {
                    properties.put(VariableProperty.UTILITY_CLASS, Level.TRUE);
                } else throw new UnsupportedOperationException("TODO: " + t.fullyQualifiedName);
            }
        }
        if (container) {
            properties.put(VariableProperty.CONTAINER, Level.TRUE);
        }
        if (immutable >= 0) {
            properties.put(VariableProperty.IMMUTABLE, Level.compose(Level.TRUE, immutable));
        }
        if (notNull >= 0) {
            properties.put(VariableProperty.NOT_NULL, Level.compose(Level.TRUE, notNull));
        }
    }
}
