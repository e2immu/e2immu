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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.Logger;
import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BinaryOperator;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class TypeAnalyser {
    private final MethodAnalyser methodAnalyser;
    private final FieldAnalyser fieldAnalyser;
    private final TypeContext typeContext;
    public static final BinaryOperator<Boolean> TERNARY_OR = (val, acc) -> val == null || acc == null ? null : val || acc;
    public static final BinaryOperator<Boolean> TERNARY_AND = (val, acc) -> val == null || acc == null ? null : val && acc;

    public TypeAnalyser(TypeContext typeContext) {
        fieldAnalyser = new FieldAnalyser(typeContext);
        methodAnalyser = new MethodAnalyser(typeContext);
        this.typeContext = typeContext;
    }

    public void check(SortedType sortedType) {
        for (WithInspectionAndAnalysis m : sortedType.methodsAndFields) {
            if (m instanceof MethodInfo) methodAnalyser.check((MethodInfo) m);
            else if (m instanceof FieldInfo) {
                FieldInfo fieldInfo = (FieldInfo) m;
                if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                    if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                        methodAnalyser.check(fieldInitialiser.implementationOfSingleAbstractMethod);
                    }
                }
                fieldAnalyser.check(fieldInfo);
            }
        }
        check(sortedType, UtilityClass.class, typeContext.utilityClass.get());
        check(sortedType, E2Final.class, typeContext.e2Final.get());
        check(sortedType, ExtensionClass.class, typeContext.extensionClass.get());
        check(sortedType, Container.class, typeContext.container.get());
        check(sortedType, E2Immutable.class, typeContext.e2Immutable.get());
    }

    private void check(SortedType sortedType, Class<?> annotation, AnnotationExpression annotationExpression) {
        sortedType.typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Type " + sortedType.typeInfo.fullyQualifiedName +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getTypeName()));
    }

    public void analyse(SortedType sortedType) {
        log(ANALYSER, "Analysing type {}", sortedType.typeInfo.fullyQualifiedName);
        boolean changes = true;
        int cnt = 0;
        while (changes) {
            cnt++;
            log(ANALYSER, "Starting iteration {} of the type analyser on {}", cnt, sortedType.typeInfo.fullyQualifiedName);
            changes = false;

            This thisVariable = new This(sortedType.typeInfo);
            VariableProperties fieldProperties = initializeVariableProperties(sortedType, thisVariable, null);

            for (WithInspectionAndAnalysis member : sortedType.methodsAndFields) {
                if (member instanceof MethodInfo) {
                    VariableProperties methodProperties = initializeVariableProperties(sortedType, thisVariable, (MethodInfo) member);
                    if (methodAnalyser.analyse((MethodInfo) member, methodProperties))
                        changes = true;
                } else {
                    FieldInfo fieldInfo = (FieldInfo) member;

                    // these are the "hidden" methods: fields of functional interfaces
                    if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                            VariableProperties methodProperties = initializeVariableProperties(sortedType, thisVariable, fieldInitialiser.implementationOfSingleAbstractMethod);
                            if (methodAnalyser.analyse(fieldInitialiser.implementationOfSingleAbstractMethod, methodProperties)) {
                                changes = true;
                            }
                        }
                    }

                    if (fieldAnalyser.analyse(fieldInfo, thisVariable, fieldProperties))
                        changes = true;
                }
            }
            if (sortedType.typeInfo.hasBeenDefined()) {
                if (detectE2Immutable(sortedType)) changes = true;
                if (detectE2Final(sortedType)) changes = true;
                if (detectContainer(sortedType)) changes = true;
                if (detectUtilityClass(sortedType)) changes = true;
            }
        }
    }

    private boolean detectUtilityClass(SortedType sortedType) {
        if (sortedType.typeInfo.typeAnalysis.annotations.isSet(typeContext.utilityClass.get())) return false;
        boolean isUtilityClass = true;
        for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().methods) {
            if (!methodInfo.isStatic) {
                log(UTILITY_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                isUtilityClass = false;
                break;
            }
        }
        if (isUtilityClass) {
            // this is technically enough, but we'll verify the constructors (should be static)
            for (MethodInfo constructor : sortedType.typeInfo.typeInspection.get().constructors) {
                if (!constructor.methodInspection.get().modifiers.contains(MethodModifier.PRIVATE)) {
                    log(UTILITY_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName +
                            " looks like a @UtilityClass, but its constructors are not all private");
                    isUtilityClass = false;
                    break;
                }
            }
        }
        if (isUtilityClass && sortedType.typeInfo.typeInspection.get().constructors.isEmpty()) {
            log(UTILITY_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            isUtilityClass = false;
        }
        if (isUtilityClass) {
            // and there should be no means of generating an object
            for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().methods) {
                if (!methodInfo.methodAnalysis.createObjectOfSelf.isSet()) {
                    log(UTILITY_CLASS, "Not yet deciding on @Utility class for {}, createObjectOfSelf not yet set on method {}",
                            sortedType.typeInfo.fullyQualifiedName, methodInfo.name);
                    return false;
                }
                if (methodInfo.methodAnalysis.createObjectOfSelf.get()) {
                    log(UTILITY_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName +
                            " looks like a @UtilityClass, but an object of the class is created in method "
                            + methodInfo.fullyQualifiedName());
                    isUtilityClass = false;
                    break;
                }
            }
        }
        sortedType.typeInfo.typeAnalysis.annotations.put(typeContext.utilityClass.get(), isUtilityClass);
        log(UTILITY_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName + " marked " + (isUtilityClass ? "" : "not ")
                + "@UtilityClass");
        return true;
    }

    private VariableProperties initializeVariableProperties(SortedType sortedType, This thisVariable, MethodInfo currentMethod) {
        VariableProperties fieldProperties = new VariableProperties(typeContext, thisVariable, currentMethod);
        fieldProperties.create(thisVariable, new VariableValue(thisVariable));

        for (WithInspectionAndAnalysis member : sortedType.methodsAndFields) {
            if (member instanceof FieldInfo) {
                FieldInfo fieldInfo = (FieldInfo) member;
                createFieldReference(thisVariable, fieldProperties, fieldInfo);
            }
        }
        // fields from sub-types... how do they fit in? It is well possible that the subtype has already been analysed,
        // or will be analysed later. However, we need to "know" the fields because they may transfer information

        // note that only fields of sub-types should be accessible for modification; fields of other types that are
        // accessible will be forced to be public final
        for (TypeInfo subType : sortedType.typeInfo.typeInspection.get().subTypes) {
            for (FieldInfo fieldInfo : subType.typeInspection.get().fields) {
                createFieldReference(thisVariable, fieldProperties, fieldInfo);
            }
        }
        return fieldProperties;
    }

    // this method is responsible for one of the big feed-back loops in the evaluation chain

    private void createFieldReference(This thisVariable, VariableProperties fieldProperties, FieldInfo fieldInfo) {
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
        Value value;

        Boolean isFinal = fieldInfo.isFinal(typeContext);
        if (Boolean.TRUE == isFinal) {
            if (fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
                value = fieldInfo.fieldAnalysis.effectivelyFinalValue.get();
                // most likely value here is "variable value", definitely not NO_VALUE, UNKNOWN_VALUE
                if (value instanceof UnknownValue) throw new UnsupportedOperationException();
            } else {
                value = new VariableValue(fieldReference, true);
            }
        } else if (Boolean.FALSE == isFinal) {
            value = new VariableValue(fieldReference);
        } else {
            value = new VariableValue(fieldReference, true);
        }

        VariableProperty[] properties;
        if (fieldInfo.isNotNull(typeContext) == Boolean.TRUE) {
            properties = new VariableProperty[]{VariableProperty.PERMANENTLY_NOT_NULL};
        } else {
            properties = new VariableProperty[0];
        }

        fieldProperties.create(fieldReference, value, properties);
    }

    private boolean detectContainer(SortedType sortedType) {
        if (sortedType.typeInfo.typeAnalysis.annotations.isSet(typeContext.container.get())) return false;

        Boolean isContainer = noMethodsThatModifyContent(sortedType, CONTAINER, "container");
        if (isContainer == null) return false;

        sortedType.typeInfo.typeAnalysis.annotations.put(typeContext.container.get(), isContainer);
        log(CONTAINER, "Type " + sortedType.typeInfo.fullyQualifiedName + " marked " + (isContainer ? "" : "not ")
                + "@Container");
        return true;
    }

    private boolean detectE2Final(SortedType sortedType) {
        if (sortedType.typeInfo.typeAnalysis.annotations.getOtherwiseNull(typeContext.e2Immutable.get()) == Boolean.TRUE)
            return false;
        if (sortedType.typeInfo.typeAnalysis.annotations.isSet(typeContext.e2Final.get())) return false;

        // rule 1 of 1. all fields must be effectively final
        boolean isE2Final = true;

        for (FieldInfo fieldInfo : sortedType.typeInfo.typeInspection.get().fields) {
            Boolean effectivelyFinal = fieldInfo.isFinal(typeContext);
            if (effectivelyFinal == null) return false; // cannot decide
            if (!effectivelyFinal) {
                log(VALUE_CLASS, "{} is not a value class, field {} is not effectively final", sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                isE2Final = false;
                break;
            }
        }

        sortedType.typeInfo.typeAnalysis.annotations.put(typeContext.e2Final.get(), isE2Final);
        log(VALUE_CLASS, "Type " + sortedType.typeInfo.fullyQualifiedName + " marked " + (isE2Final ? "" : "not ")
                + "@ValueClass");
        return true;
    }

    private boolean detectE2Immutable(SortedType sortedType) {
        if (sortedType.typeInfo.typeAnalysis.annotations.isSet(typeContext.e2Immutable.get())) return false;
        boolean isEffectivelyImmutable = true;

        Set<FieldInfo> nonPrimitiveNonE2ImmutableFields = new HashSet<>();
        for (FieldInfo fieldInfo : sortedType.typeInfo.typeInspection.get().fields) {

            // RULE 1: ALL FIELDS ARE EFFECTIVELY FINAL

            Boolean effectivelyFinal = fieldInfo.isFinal(typeContext);
            if (effectivelyFinal != null && !effectivelyFinal) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not effectively final",
                        sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                isEffectivelyImmutable = false;
                break;
            }

            // RULE 2: ALL FIELDS HAVE BEEN ANNOTATED @NotModified UNLESS THEY ARE PRIMITIVE OR @E2Immutable

            Boolean fieldIsEffectivelyImmutable = fieldInfo.type.isPrimitive();
            if (!fieldIsEffectivelyImmutable)
                fieldIsEffectivelyImmutable = fieldInfo.type.isEffectivelyImmutable(typeContext);
            // field is of the type of the class being analysed... it will not make the difference.
            if (fieldIsEffectivelyImmutable == null && sortedType.typeInfo == fieldInfo.type.typeInfo)
                fieldIsEffectivelyImmutable = true;

            // part of rule 2: we now need to check that @NotModified is on the field
            if (fieldIsEffectivelyImmutable != null && !fieldIsEffectivelyImmutable) {

                nonPrimitiveNonE2ImmutableFields.add(fieldInfo); // we'll need to do more checks later
                Boolean notModified = fieldInfo.isNotModified(typeContext);
                if (notModified != null && !notModified) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also not @NotModified",
                            sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                    isEffectivelyImmutable = false;
                    break;
                }

                // RULE 4: ALL FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE

                if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also exposed (not private)",
                            sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                    isEffectivelyImmutable = false;
                    break;
                }
                if (notModified == null) {
                    log(E2IMMUTABLE, "Cannot decide yet if {} is an E2Immutable class; not enough on @NotModified of {}",
                            sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                    return false; // cannot decide
                }
            }
            if (effectivelyFinal == null || fieldIsEffectivelyImmutable == null) {
                log(E2IMMUTABLE, "Cannot decide yet if {} is an E2Immutable class; not enough info on {}",
                        sortedType.typeInfo.fullyQualifiedName, fieldInfo.name);
                return false; // cannot decide
            }
        }

        // RULE 3: INDEPENDENCE OF FIELDS VS PARAMETERS

        if (isEffectivelyImmutable) {
            for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().methodsAndConstructors()) {
                Boolean independent = methodInfo.isIndependent(typeContext);

                for (FieldInfo fieldInfo : nonPrimitiveNonE2ImmutableFields) {
                    Boolean modified = methodInfo.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo);
                    if (modified == null) {
                        log(E2IMMUTABLE, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether {} is assigned in {}",
                                sortedType.typeInfo.fullyQualifiedName, fieldInfo.name, methodInfo.name);
                        return false; // not decided
                    }
                    if (modified) {
                        if (independent == null) {
                            log(E2IMMUTABLE, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the assignment to {} is @Independent in {}",
                                    sortedType.typeInfo.fullyQualifiedName, fieldInfo.name, methodInfo.name);
                            return false; //not decided
                        }
                        if (!independent) {
                            log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is assigned in method {}, but it is not @Independent",
                                    sortedType.typeInfo.fullyQualifiedName, fieldInfo.name, methodInfo.name);
                            isEffectivelyImmutable = false;
                            break;
                        }
                    } // else safe
                }
                if (!isEffectivelyImmutable) break;
            }
        }

        // RULE 5: RETURN TYPES

        if (isEffectivelyImmutable) {
            for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().methods) {
                ParameterizedType returnType = methodInfo.returnType();
                Boolean returnTypeIsEffectivelyImmutable = returnType.isPrimitive();
                if (!returnTypeIsEffectivelyImmutable)
                    returnTypeIsEffectivelyImmutable = returnType.isEffectivelyImmutable(typeContext);
                // field is of the type of the class being analysed... it will not make the difference.
                if (returnTypeIsEffectivelyImmutable == null && sortedType.typeInfo == returnType.typeInfo)
                    returnTypeIsEffectivelyImmutable = true;

                if (returnTypeIsEffectivelyImmutable != null && !returnTypeIsEffectivelyImmutable) {
                    // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent
                    Boolean independent = methodInfo.isIndependent(typeContext);
                    if (independent == null) {
                        log(E2IMMUTABLE, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                                sortedType.typeInfo.fullyQualifiedName, methodInfo.name);
                        return false; //not decided
                    }
                    if (!independent) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                                sortedType.typeInfo.fullyQualifiedName, methodInfo.name);
                        isEffectivelyImmutable = false;
                        break;
                    }
                }
            }
        }
        sortedType.typeInfo.typeAnalysis.annotations.put(typeContext.e2Immutable.get(), isEffectivelyImmutable);
        log(E2IMMUTABLE, "Type " + sortedType.typeInfo.fullyQualifiedName + " marked " + (isEffectivelyImmutable ? "" : "not ")
                + "@E2Immutable");
        return true;
    }

    private Boolean noMethodsThatModifyContent(SortedType sortedType, Logger.LogTarget logTarget, String message) {
        for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().constructors) {
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                Boolean isNotModified = parameterInfo.isNotModified(typeContext);
                if (isNotModified == null) return null; // cannot yet decide
                if (!isNotModified) {
                    log(logTarget, "{} is not a {}: {} does not have a @NotModified annotation", sortedType.typeInfo.fullyQualifiedName,
                            parameterInfo.detailedString());
                    return false;
                }
            }
        }
        // rule 3. all non-private methods must not modify their parameters
        for (MethodInfo methodInfo : sortedType.typeInfo.typeInspection.get().methods) {
            if (!methodInfo.methodInspection.get().modifiers.contains(MethodModifier.PRIVATE)) {
                for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                    Boolean isNotModified = parameterInfo.isNotModified(typeContext);
                    if (isNotModified == null) return null; // cannot yet decide
                    if (!isNotModified) {
                        log(logTarget, "{} is not a {}: {} does not have a @NotModified annotation", sortedType.typeInfo.fullyQualifiedName,
                                parameterInfo.detailedString());
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
