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
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;

import java.util.*;
import java.util.function.BinaryOperator;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

/**
 * In the type analysis record we state whether this type has "free fields" or not.
 * Nested types will be allowed in two forms:
 * (1) non-private nested types, where (a) all non-private fields must be @E1Immutable,
 * and (b) access to private methods and fields from enclosing to nested and nested to enclosing is restricted
 * to reading fields and calling @NotModified methods in a direct hierarchical line
 * (2) private subtypes, which do not need to satisfy (1a), and which have the one additional freedom compared to (1b) that
 * the enclosing type can access private fields and methods at will as long as the types are in hierarchical line
 * <p>
 * The analyse and check methods are called independently for types and nested types, in an order of dependence determined
 * by the resolver, but guaranteed such that a nested type will always come before its enclosing type.
 * <p>
 * Therefore, at the end of an enclosing type's analysis, we should have decisions on @NotModified of the methods of the
 * enclosing type, and it should be possible to establish whether a nested type only reads fields (does NOT assign) and
 * calls @NotModified private methods.
 * <p>
 * Errors related to those constraints are added to the type making the violation.
 */

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
        TypeInfo typeInfo = sortedType.typeInfo;

        // before we check, we copy the properties into annotations
        typeInfo.typeAnalysis.transferPropertiesToAnnotations(typeContext);

        log(ANALYSER, "\n******\nAnnotation validation on type {}\n******", typeInfo.fullyQualifiedName);

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
        check(typeInfo, E1Container.class, typeContext.e1Container.get());
        check(typeInfo, ExtensionClass.class, typeContext.extensionClass.get());
        check(typeInfo, Container.class, typeContext.container.get());
        check(typeInfo, E2Immutable.class, typeContext.e2Immutable.get());
        check(typeInfo, E2Container.class, typeContext.e2Container.get());
        check(typeInfo, NotNull.class, typeContext.notNull.get());
        check(typeInfo, NotNull1.class, typeContext.notNull1.get());
        check(typeInfo, NotNull2.class, typeContext.notNull2.get());
        // TODO there's a "where" which complicates things!! check(typeInfo, NotModified.class, typeContext.e2Immutable.get());
        // already implemented for "reading", but not yet for checking
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

            List<This> thisVariables = typeInfo.thisVariables();
            VariableProperties fieldProperties = new VariableProperties(typeContext, typeInfo);

            for (WithInspectionAndAnalysis member : sortedType.methodsAndFields) {
                if (member instanceof MethodInfo) {
                    VariableProperties methodProperties = new VariableProperties(typeContext, (MethodInfo) member);
                    if (methodAnalyser.analyse((MethodInfo) member, methodProperties))
                        changes = true;
                } else {
                    FieldInfo fieldInfo = (FieldInfo) member;

                    // these are the "hidden" methods: fields of functional interfaces
                    if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                            VariableProperties methodProperties = new VariableProperties(typeContext,
                                    fieldInitialiser.implementationOfSingleAbstractMethod);
                            if (methodAnalyser.analyse(fieldInitialiser.implementationOfSingleAbstractMethod, methodProperties)) {
                                changes = true;
                            }
                        }
                    }

                    if (fieldAnalyser.analyse(fieldInfo, thisVariables.get(0), fieldProperties))
                        changes = true;
                }
            }
            // TODO at some point we will have overlapping qualities
            // this is caused by the fact that some knowledge may come later.
            // at the moment I'd rather not delay too much, and live with @Container @E1Immutable @E2Immutable @E2Container on a type
            if (typeInfo.hasBeenDefined()) {
                if (analyseE1Immutable(typeInfo)) changes = true;
                if (analyseE2Immutable(typeInfo)) changes = true;
                if (analyseContainer(typeInfo)) changes = true;
                if (analyseUtilityClass(typeInfo)) changes = true;
                if (analyseExtensionClass(typeInfo)) changes = true;
                if (analyseNotNull(typeInfo)) changes = true;
            }
            if (cnt > 10) {
                throw new UnsupportedOperationException("?10 iterations needed?");
            }
        }

        // from now on, even if we come back here, all @NotModifieds have been set, no delays allowed anymore
        if (!typeInfo.typeAnalysis.doNotAllowDelaysOnNotModified.isSet()) {
            typeInfo.typeAnalysis.doNotAllowDelaysOnNotModified.set(true);
        }

        if (!typeInfo.typeInspection.get().subTypes.isEmpty() && !typeInfo.typeAnalysis.startedPostAnalysisIntoNestedTypes.isSet()) {
            postAnalysisIntoNestedTypes(typeInfo);
            typeInfo.typeAnalysis.startedPostAnalysisIntoNestedTypes.set(true);
        }
    }

    /**
     * Lets analyse the sub-types again, so that we're guaranteed that @NotModified on the enclosing type's methods have been computed.
     *
     * @param typeInfo the enclosing type
     */
    private void postAnalysisIntoNestedTypes(TypeInfo typeInfo) {
        log(ANALYSER, "\n--------\nStarting post-analysis into method calls from nested types to {}\n--------",
                typeInfo.fullyQualifiedName);
        for (TypeInfo nestedType : typeInfo.typeInspection.get().subTypes) {
            SortedType sortedType = new SortedType(nestedType);
            // the order of analysis is not important anymore, we just have to go over the method calls to the enclosing type

            analyse(sortedType);
            check(sortedType); // we're not checking at top level!
        }
        log(ANALYSER, "\n--------\nEnded post-analysis into method calls from nested types to {}\n--------",
                typeInfo.fullyQualifiedName);
    }

    private boolean analyseContainer(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return false;

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isPrivate()) {
                for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                    int notModified = parameterInfo.parameterAnalysis.getProperty(VariableProperty.NOT_MODIFIED);
                    if (notModified == Level.DELAY) return false; // cannot yet decide
                    if (notModified == Level.FALSE) {
                        log(CONTAINER, "{} is not a @Container: {} in {} does not have a @NotModified annotation",
                                typeInfo.fullyQualifiedName,
                                parameterInfo.detailedString(),
                                methodInfo.distinguishingName());
                        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.FALSE);
                        return true;
                    }
                }
            }
        }
        typeAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
        log(CONTAINER, "Mark {} as @Container", typeInfo.fullyQualifiedName);
        return true;
    }

    private boolean analyseE1Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE1Immutable = Level.value(typeImmutable, Level.E1IMMUTABLE);
        if (typeE1Immutable != Level.DELAY) return false; // we have a decision already
        int no = Level.compose(Level.FALSE, Level.E2IMMUTABLE);

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            int effectivelyFinal = fieldInfo.fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) return false; // cannot decide
            if (effectivelyFinal == Level.FALSE) {
                log(E1IMMUTABLE, "Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                return true;
            }
        }
        log(E1IMMUTABLE, "Improve @Immutable of type {} to @E1Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, Level.compose(Level.TRUE, Level.E1IMMUTABLE));
        return true;
    }

    private boolean analyseE2Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE2Immutable = Level.value(typeImmutable, Level.E2IMMUTABLE);
        if (typeE2Immutable != Level.DELAY) return false; // we have a decision already
        int no = Level.compose(Level.FALSE, Level.E2IMMUTABLE);

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis;

            // RULE 1: ALL FIELDS ARE EFFECTIVELY FINAL

            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) {
                log(DELAYED, "Field {} non known yet if effectively final, delay E2Immu", fieldInfo.fullyQualifiedName());
                return false;
            }
            if (effectivelyFinal == Level.FALSE) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                return true;
            }

            // RULE 2: ALL FIELDS HAVE BEEN ANNOTATED @NotModified UNLESS THEY ARE PRIMITIVE OR @E2Immutable

            int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            int e2immutable = Level.value(immutable, Level.E2IMMUTABLE);
            // field is of the type of the class being analysed... it will not make the difference.
            if (e2immutable == Level.DELAY && typeInfo == fieldInfo.type.typeInfo) {
                e2immutable = Level.TRUE;
            }
            // part of rule 2: we now need to check that @NotModified is on the field
            if (e2immutable == Level.DELAY) {
                log(DELAYED, "Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldInfo.fullyQualifiedName());
                return false;
            }
            if (e2immutable == Level.FALSE) {
                int notModified = fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED);

                if (notModified == Level.DELAY) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldInfo.fullyQualifiedName());
                    return false;
                }
                if (notModified == Level.FALSE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also not @NotModified",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return true;
                }

                // RULE 4: ALL FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE

                if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also exposed (not private)",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return false;
                }
            }
        }

        // RULE 3: INDEPENDENCE OF METHODS

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().constructors) {
            int independent = methodInfo.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
            if (independent == Level.DELAY) {
                log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}", methodInfo.distinguishingName());
                return false; //not decided
            }
            if (independent == Level.FALSE) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                        typeInfo.fullyQualifiedName, methodInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                return false;
            }
        }

        // RULE 5: RETURN TYPES

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis;
            int returnTypeImmutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
            int returnTypeE2Immutable = Level.value(returnTypeImmutable, Level.E2IMMUTABLE);
            if (returnTypeE2Immutable == Level.DELAY) {
                log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodInfo.distinguishingName());
                return false;
            }
            if (returnTypeE2Immutable == Level.FALSE) {
                // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent
                int independent = methodInfo.methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
                if (independent == Level.DELAY) {
                    log(DELAYED, "Cannot decide yet if {} is an E2Immutable class; not enough info on whether the method {} is @Independent",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    return false; //not decided
                }
                if (independent == Level.FALSE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because method {}'s return type is not primitive, not E2Immutable, not independent",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return true;
                }
            }
        }
        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, Level.compose(Level.TRUE, Level.E2IMMUTABLE));
        return true;
    }

    // this one analyses NotNull at level 0, for the whole type
    // TODO we should split this up for methods, fields, parameters, with a "where" variable

    private boolean analyseNotNull(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int typeNotNull = typeAnalysis.getProperty(VariableProperty.NOT_NULL);
        if (typeNotNull != Level.DELAY) return false;

        // all fields should be @NN, all methods, and all parameters...
        boolean isNotNull = true;
        methodLoop:
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                int notNull = methodInfo.methodAnalysis.getProperty(VariableProperty.NOT_NULL);
                if (notNull == Level.DELAY) return false;
                if (notNull == Level.FALSE) {
                    isNotNull = false;
                    break;
                }
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                int notNull = parameterInfo.parameterAnalysis.getProperty(VariableProperty.NOT_NULL);
                if (notNull == Level.DELAY) return false;
                if (notNull == Level.FALSE) {
                    isNotNull = false;
                    break methodLoop;
                }
            }
        }
        if (isNotNull) {
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
                int notNull = fieldInfo.fieldAnalysis.getProperty(VariableProperty.NOT_NULL);
                if (notNull == Level.DELAY) return false;
                if (notNull == Level.FALSE) {
                    isNotNull = false;
                    break;
                }
            }
        }
        typeAnalysis.setProperty(VariableProperty.NOT_NULL, isNotNull);
        log(NOT_NULL, "Marked type {} as " + (isNotNull ? "" : "NOT ") + " @NotNull", typeInfo.fullyQualifiedName);
        return true;
    }

    private boolean analyseExtensionClass(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return false;

        int e2Immutable = Level.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
        if (e2Immutable == Level.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable == Level.FALSE) {
            log(UTILITY_CLASS, "Type {} is not an @ExtensionClass, not @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.FALSE);
            return true;
        }

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
        boolean isExtensionClass = commonTypeOfFirstParameter != null;
        typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, isExtensionClass);
        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return true;
    }

    private boolean analyseUtilityClass(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis;
        int utilityClass = typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS);
        if (utilityClass != Level.DELAY) return false;

        int e2Immutable = Level.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
        if (e2Immutable == Level.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable == Level.FALSE) {
            log(UTILITY_CLASS, "Type {} is not a @UtilityClass, not @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            if (!methodInfo.isStatic) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be static)
        for (MethodInfo constructor : typeInfo.typeInspection.get().constructors) {
            if (!constructor.methodInspection.get().modifiers.contains(MethodModifier.PRIVATE)) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }

        if (typeInfo.typeInspection.get().constructors.isEmpty()) {
            log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

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
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }

        typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.TRUE);
        log(UTILITY_CLASS, "Type {} marked @UtilityClass", typeInfo.fullyQualifiedName);
        return true;
    }

}
