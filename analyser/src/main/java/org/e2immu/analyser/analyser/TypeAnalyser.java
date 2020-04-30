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
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class TypeAnalyser {
    private final MethodAnalyser methodAnalyser;
    private final FieldAnalyser fieldAnalyser;
    private final TypeContext typeContext;
    public static final BinaryOperator<Boolean> TERNARY_OR = (val, acc) -> val == null || acc == null ? null : val || acc;
    public static final BinaryOperator<Boolean> TERNARY_AND = (val, acc) -> val == null || acc == null ? null : val && acc;

    public TypeAnalyser(@NotNull TypeContext typeContext) {
        fieldAnalyser = new FieldAnalyser(typeContext);
        methodAnalyser = new MethodAnalyser(typeContext);
        this.typeContext = Objects.requireNonNull(typeContext);
    }

    public void check(SortedType sortedType) {
        log(ANALYSER, "\n******\nAnnotation validation on type {}\n******", sortedType.typeInfo.fullyQualifiedName);
        TypeInfo typeInfo = sortedType.typeInfo;

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
        check(typeInfo, UtilityClass.class, typeContext.utilityClass.get());
        check(typeInfo, E1Immutable.class, typeContext.e1Immutable.get());
        check(typeInfo, ExtensionClass.class, typeContext.extensionClass.get());
        check(typeInfo, Container.class, typeContext.container.get());
        check(typeInfo, E2Immutable.class, typeContext.e2Immutable.get());
        check(typeInfo, NotNull.class, typeContext.notNull.get());
        // TODO there's a "where" which complicates things!! check(typeInfo, NotModified.class, typeContext.e2Immutable.get());

    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Type " + typeInfo.fullyQualifiedName +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getSimpleName()));
    }

    public void analyse(SortedType sortedType) {
        TypeInfo typeInfo = sortedType.typeInfo;
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);
        boolean changes = true;
        int cnt = 0;
        while (changes) {
            cnt++;
            log(ANALYSER, "\n******\nStarting iteration {} of the type analyser on {}\n******", cnt, typeInfo.fullyQualifiedName);
            changes = false;

            This thisVariable = new This(typeInfo);
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
            // TODO at some point we will have overlapping qualities
            // this is caused by the fact that some knowledge may come later.
            // at the moment I'd rather not delay too much, and live with @Container @E1Immutable @E2Immutable @E2Container on a type
            if (typeInfo.hasBeenDefined()) {
                Boolean isE2Immutable = isE2Immutable(typeInfo);
                if (isE2Immutable != null && assignE2Immutable(typeInfo, isE2Immutable)) changes = true;
                Boolean isE1Immutable = isE1Immutable(typeInfo);
                if (isE1Immutable != null && assignE1Immutable(typeInfo, isE1Immutable)) changes = true;

                Boolean isContainer = noMethodsWhoseParametersContentIsModified(typeInfo);
                if (isContainer != null) {
                    if (assignContainer(typeInfo, isContainer)) changes = true;
                    if (isE1Immutable != null && assignE1Container(typeInfo, isContainer, isE1Immutable))
                        changes = true;
                    if (isE2Immutable != null && assignE2Container(typeInfo, isContainer, isE2Immutable))
                        changes = true;
                }
                if (detectUtilityClass(typeInfo)) changes = true;
                if (detectExtensionClass(typeInfo)) changes = true;
                if (detectNotNull(typeInfo)) changes = true;
            }
        }
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

        Boolean isFinal = fieldInfo.isEffectivelyFinal(typeContext);
        if (Boolean.TRUE == isFinal) {
            if (fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
                value = fieldInfo.fieldAnalysis.effectivelyFinalValue.get();
            } else {
                Boolean isE2Immutable = fieldInfo.isE2Immutable(typeContext);
                Set<AnnotationExpression> dynamicAnnotationExpressions;
                if (isE2Immutable == null) {
                    dynamicAnnotationExpressions = null;
                } else {
                    dynamicAnnotationExpressions = fieldInfo.fieldAnalysis.annotations.stream().filter(Map.Entry::getValue)
                            .map(Map.Entry::getKey).collect(Collectors.toSet());
                }
                value = new VariableValue(fieldReference, dynamicAnnotationExpressions, true, null);
            }
        } else if (Boolean.FALSE == isFinal) {
            value = new VariableValue(fieldReference);
        } else {
            // no idea about @Final, @E2Immutable
            value = new VariableValue(fieldReference, null, true, null);
        }

        VariableProperty[] properties;
        if (fieldInfo.isNotNull(typeContext) == Boolean.TRUE) {
            properties = new VariableProperty[]{VariableProperty.PERMANENTLY_NOT_NULL};
        } else {
            properties = new VariableProperty[0];
        }

        fieldProperties.create(fieldReference, value, properties);
    }

    private boolean assignContainer(TypeInfo typeInfo, boolean isContainer) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.container.get())) return false;

        typeInfo.typeAnalysis.annotations.put(typeContext.container.get(), isContainer);
        log(CONTAINER, "Type " + typeInfo.fullyQualifiedName + " marked " + (isContainer ? "" : "not ")
                + "@Container");
        return true;
    }

    private boolean assignE1Container(TypeInfo typeInfo, boolean isContainer, boolean isE1Immutable) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.e1Container.get())) return false;

        boolean isE1Container = isContainer && isE1Immutable;

        typeInfo.typeAnalysis.annotations.put(typeContext.e1Container.get(), isE1Container);
        log(CONTAINER, "Type " + typeInfo.fullyQualifiedName + " marked " + (isE1Container ? "" : "not ")
                + "@E1Container");
        return true;
    }

    private boolean assignE2Container(TypeInfo typeInfo, boolean isContainer, boolean isE2Immutable) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.e2Container.get())) return false;

        boolean isE2Container = isContainer && isE2Immutable;

        typeInfo.typeAnalysis.annotations.put(typeContext.e2Container.get(), isE2Container);
        log(CONTAINER, "Type " + typeInfo.fullyQualifiedName + " marked " + (isE2Container ? "" : "not ")
                + "@E2Container");
        return true;
    }

    private Boolean noMethodsWhoseParametersContentIsModified(TypeInfo typeInfo) {
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                Boolean isNotModified = parameterInfo.isNotModified(typeContext);
                if (isNotModified == null) return null; // cannot yet decide
                if (!isNotModified) {
                    log(CONTAINER, "{} is not a @Container: {} in {} does not have a @NotModified annotation",
                            typeInfo.fullyQualifiedName,
                            parameterInfo.detailedString(),
                            methodInfo.distinguishingName());
                    return false;
                }
            }
        }
        return true;
    }

    private boolean assignE1Immutable(TypeInfo typeInfo, boolean isE1Immutable) {
        if (typeInfo.typeAnalysis.annotations.getOtherwiseNull(typeContext.e2Immutable.get()) == Boolean.TRUE)
            return false;
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.e1Immutable.get())) return false;

        typeInfo.typeAnalysis.annotations.put(typeContext.e1Immutable.get(), isE1Immutable);
        log(E1IMMUTABLE, "Type " + typeInfo.fullyQualifiedName + " marked " + (isE1Immutable ? "" : "not ")
                + "@E1Immutable");
        return true;
    }

    private Boolean isE1Immutable(TypeInfo typeInfo) {
        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            Boolean effectivelyFinal = fieldInfo.isEffectivelyFinal(typeContext);
            if (effectivelyFinal == null) return null; // cannot decide
            if (!effectivelyFinal) {
                return false;
            }
        }
        return true;
    }

    private boolean assignE2Immutable(TypeInfo typeInfo, boolean isEffectivelyImmutable) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.e2Immutable.get())) return false;

        typeInfo.typeAnalysis.annotations.put(typeContext.e2Immutable.get(), isEffectivelyImmutable);
        log(E2IMMUTABLE, "Type " + typeInfo.fullyQualifiedName + " marked " + (isEffectivelyImmutable ? "" : "not ")
                + "@E2Immutable");
        return true;
    }

    private Boolean isE2Immutable(TypeInfo typeInfo) {
        Set<FieldInfo> nonPrimitiveNonE2ImmutableFields = new HashSet<>();
        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {

            // RULE 1: ALL FIELDS ARE EFFECTIVELY FINAL

            Boolean effectivelyFinal = fieldInfo.isEffectivelyFinal(typeContext);
            if (effectivelyFinal == null) {
                log(DELAYED, "Field {} non known yet if effectively final, delay E2Immu", fieldInfo.fullyQualifiedName());
                return null;
            }
            if (!effectivelyFinal) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldInfo.name);
                return false;
            }

            // RULE 2: ALL FIELDS HAVE BEEN ANNOTATED @NotModified UNLESS THEY ARE PRIMITIVE OR @E2Immutable

            Boolean fieldIsEffectivelyImmutable = fieldInfo.isE2Immutable(typeContext);
            // field is of the type of the class being analysed... it will not make the difference.
            if (fieldIsEffectivelyImmutable == null && typeInfo == fieldInfo.type.typeInfo)
                fieldIsEffectivelyImmutable = true;

            // part of rule 2: we now need to check that @NotModified is on the field
            if (fieldIsEffectivelyImmutable == null) {
                log(DELAYED, "Field {} not known yet if E2Immutable, delaying E2Immutable on type", fieldInfo.fullyQualifiedName());
                return null;
            }
            if (!fieldIsEffectivelyImmutable) {
                nonPrimitiveNonE2ImmutableFields.add(fieldInfo); // we'll need to do more checks later
                Boolean notModified = fieldInfo.isNotModified(typeContext);

                if (notModified == null) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldInfo.fullyQualifiedName());
                    return null;
                }
                if (!notModified) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also not @NotModified",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    return false;
                }

                // RULE 4: ALL FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE

                if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also exposed (not private)",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    return false;
                }
            }
        }

        // RULE 3: INDEPENDENCE OF FIELDS VS PARAMETERS

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().constructors) {
            Boolean independent = methodInfo.isIndependent(typeContext);
            if (independent == null) {
                log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}", methodInfo.distinguishingName());
                return null; //not decided
            }
            if (!independent) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                        typeInfo.fullyQualifiedName, methodInfo.name);
                return false;
            }
        }

        // RULE 5: RETURN TYPES

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            Boolean returnTypeIsEffectivelyImmutable = methodInfo.isE2Immutable(typeContext);
            if (returnTypeIsEffectivelyImmutable == null) {
                log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodInfo.distinguishingName());
                return null;
            }
            if (!returnTypeIsEffectivelyImmutable) {
                // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent
                Boolean independent = methodInfo.isIndependent(typeContext);
                if (independent == null) {
                    log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    return null; //not decided
                }
                if (!independent) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    return false;
                }
            }
        }
        return true;
    }

    private boolean detectNotNull(TypeInfo typeInfo) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.notNull.get())) return false;

        // all fields should be @NN, all methods, and all parameters...
        boolean isNotNull = true;
        methodLoop:
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                Boolean notNull = methodInfo.isNotNull(typeContext);
                if (notNull == null) return false;
                if (notNull == Boolean.FALSE) {
                    isNotNull = false;
                    break;
                }
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                Boolean notNull = parameterInfo.isNotNull(typeContext);
                if (notNull == null) return false;
                if (notNull == Boolean.FALSE) {
                    isNotNull = false;
                    break methodLoop;
                }
            }
        }
        if (isNotNull) {
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
                Boolean notNull = fieldInfo.isNotNull(typeContext);
                if (notNull == null) return false;
                if (notNull == Boolean.FALSE) {
                    isNotNull = false;
                    break;
                }
            }
        }
        typeInfo.typeAnalysis.annotations.put(typeContext.notNull.get(), isNotNull);
        log(NOT_NULL, "Marked type {} as " + (isNotNull ? "" : "NOT ") + " @NotNull", typeInfo.fullyQualifiedName);
        return true;
    }

    private boolean detectExtensionClass(TypeInfo typeInfo) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.extensionClass.get())) return false;

        Boolean isE2Immutable = typeInfo.isE2Immutable(typeContext);
        if (isE2Immutable == null) {
            log(DELAYED, "Don't know yet about @E2Immutable for @ExtensionClass on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean isExtensionClass = isE2Immutable;
        if (isE2Immutable) {
            ParameterizedType commonTypeOfFirstParameter = null;
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
                if (methodInfo.isStatic && !methodInfo.isPrivate()) {
                    List<ParameterInfo> parameters = methodInfo.methodInspection.get().parameters;
                    ParameterizedType typeOfFirstParameter;
                    if (parameters.isEmpty()) {
                        typeOfFirstParameter = methodInfo.returnType();
                    } else {
                        typeOfFirstParameter = parameters.get(0).parameterizedType;
                    }
                    if (commonTypeOfFirstParameter == null) {
                        commonTypeOfFirstParameter = typeOfFirstParameter;
                    } else if (!ParameterizedType.equalsTypeParametersOnlyIndex(commonTypeOfFirstParameter,
                            typeOfFirstParameter)) {
                        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName +
                                " is not an @ExtensionClass, it has no common type for the first " +
                                "parameter (or return type, if no parameters) of static methods, seeing " +
                                commonTypeOfFirstParameter.detailedString() + " vs " + typeOfFirstParameter.detailedString());
                        commonTypeOfFirstParameter = null;
                        break;
                    }
                }
            }
            isExtensionClass = commonTypeOfFirstParameter != null;
        } else {
            log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " is not an @ExtensionClass as it is not @E2Immutable");
        }
        typeInfo.typeAnalysis.annotations.put(typeContext.extensionClass.get(), isExtensionClass);
        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return true;
    }

    private boolean detectUtilityClass(TypeInfo typeInfo) {
        if (typeInfo.typeAnalysis.annotations.isSet(typeContext.utilityClass.get())) return false;
        Boolean isE2Immutable = typeInfo.isE2Immutable(typeContext);
        if (isE2Immutable == null) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean isUtilityClass = isE2Immutable;
        if (isUtilityClass) {
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
                if (!methodInfo.isStatic) {
                    log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                            " is not a @UtilityClass, method {} is not static", methodInfo.name);
                    isUtilityClass = false;
                    break;
                }
            }
        }
        if (isUtilityClass) {
            // this is technically enough, but we'll verify the constructors (should be static)
            for (MethodInfo constructor : typeInfo.typeInspection.get().constructors) {
                if (!constructor.methodInspection.get().modifiers.contains(MethodModifier.PRIVATE)) {
                    log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                            " looks like a @UtilityClass, but its constructors are not all private");
                    isUtilityClass = false;
                    break;
                }
            }
        }
        if (isUtilityClass && typeInfo.typeInspection.get().constructors.isEmpty()) {
            log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            isUtilityClass = false;
        }
        if (isUtilityClass) {
            // and there should be no means of generating an object
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
                if (!methodInfo.methodAnalysis.createObjectOfSelf.isSet()) {
                    log(UTILITY_CLASS, "Not yet deciding on @Utility class for {}, createObjectOfSelf not yet set on method {}",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    return false;
                }
                if (methodInfo.methodAnalysis.createObjectOfSelf.get()) {
                    log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                            " looks like a @UtilityClass, but an object of the class is created in method "
                            + methodInfo.fullyQualifiedName());
                    isUtilityClass = false;
                    break;
                }
            }
        }
        typeInfo.typeAnalysis.annotations.put(typeContext.utilityClass.get(), isUtilityClass);
        log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isUtilityClass ? "" : "not ")
                + "@UtilityClass");
        return true;
    }

}
