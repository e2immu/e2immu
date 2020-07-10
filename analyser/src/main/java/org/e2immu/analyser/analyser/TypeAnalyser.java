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

import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
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
    public static final int POST_ANALYSIS = 100;
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
        typeInfo.typeAnalysis.get().transferPropertiesToAnnotations(typeContext);

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

        // opposites
        check(typeInfo, Mutable.class, typeContext.mutable.get());
        check(typeInfo, ExternallyMutable.class, typeContext.externallyMutable.get());
        check(typeInfo, ModifiesArguments.class, typeContext.modifiesArguments.get());
    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            typeContext.addMessage(error);
        });
    }

    public void analyse(SortedType sortedType, DebugConfiguration debugConfiguration, boolean postAnalysis) {
        TypeInfo typeInfo = sortedType.typeInfo;
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);

        boolean changes = true;
        int cnt = 0;
        while (changes) {
            log(ANALYSER, "\n******\nStarting iteration {} of the type analyser on {}\n******", cnt, typeInfo.fullyQualifiedName);
            changes = false;

            int iteration = postAnalysis ? POST_ANALYSIS + cnt : cnt;
            // TODO check that separate field properties work...
            //VariableProperties fieldProperties = new VariableProperties(typeContext, typeInfo, iteration, debugConfiguration);

            for (WithInspectionAndAnalysis member : sortedType.methodsAndFields) {
                if (member instanceof MethodInfo) {
                    VariableProperties methodProperties = new VariableProperties(typeContext, iteration, debugConfiguration, (MethodInfo) member);

                    for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.beforeMethodAnalyserVisitors) {
                        methodAnalyserVisitor.visit(iteration, (MethodInfo) member);
                    }
                    if (methodAnalyser.analyse((MethodInfo) member, methodProperties))
                        changes = true;
                    for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.afterMethodAnalyserVisitors) {
                        methodAnalyserVisitor.visit(iteration, (MethodInfo) member);
                    }

                } else {
                    FieldInfo fieldInfo = (FieldInfo) member;
                    // these are the "hidden" methods: fields of functional interfaces
                    if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                            VariableProperties methodProperties = new VariableProperties(typeContext, iteration, debugConfiguration,
                                    fieldInitialiser.implementationOfSingleAbstractMethod);

                            for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.beforeMethodAnalyserVisitors) {
                                methodAnalyserVisitor.visit(iteration, fieldInitialiser.implementationOfSingleAbstractMethod);
                            }
                            if (methodAnalyser.analyse(fieldInitialiser.implementationOfSingleAbstractMethod, methodProperties)) {
                                changes = true;
                            }
                            for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.afterMethodAnalyserVisitors) {
                                methodAnalyserVisitor.visit(iteration, fieldInitialiser.implementationOfSingleAbstractMethod);
                            }
                        }
                    }

                    for (FieldAnalyserVisitor fieldAnalyserVisitor : debugConfiguration.beforeFieldAnalyserVisitors) {
                        fieldAnalyserVisitor.visit(iteration, fieldInfo);
                    }
                    VariableProperties fieldProperties = new VariableProperties(typeContext, iteration, debugConfiguration, fieldInfo);
                    if (fieldAnalyser.analyse(fieldInfo, new This(typeInfo), fieldProperties))
                        changes = true;
                    for (FieldAnalyserVisitor fieldAnalyserVisitor : debugConfiguration.afterFieldAnalyserVisitors) {
                        fieldAnalyserVisitor.visit(iteration, fieldInfo);
                    }
                }
            }

            for (TypeAnalyserVisitor typeAnalyserVisitor : debugConfiguration.beforeTypePropertyComputations) {
                typeAnalyserVisitor.visit(iteration, typeInfo);
            }
            // TODO at some point we will have overlapping qualities
            // this is caused by the fact that some knowledge may come later.
            // at the moment I'd rather not delay too much, and live with @Container @E1Immutable @E2Immutable @E2Container on a type
            if (typeInfo.hasBeenDefined()) {
                if (analyseOnlyAndMark(typeInfo)) changes = true;
                if (analyseE1Immutable(typeInfo)) changes = true;
                if (analyseE2Immutable(typeInfo)) changes = true;
                if (analyseContainer(typeInfo)) changes = true;
                if (analyseUtilityClass(typeInfo)) changes = true;
                if (analyseExtensionClass(typeInfo)) changes = true;
                if (analyseNotNull(typeInfo)) changes = true;
            }

            for (TypeAnalyserVisitor typeAnalyserVisitor : debugConfiguration.afterTypePropertyComputations) {
                typeAnalyserVisitor.visit(iteration, typeInfo);
            }

            cnt++;
            if (cnt > 10) {
                throw new UnsupportedOperationException("More than 10 iterations needed for type " + typeInfo.simpleName + "?");
            }
        }

        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        // from now on, even if we come back here, all @NotModifieds have been set, no delays allowed anymore
        if (!typeAnalysis.doNotAllowDelaysOnNotModified.isSet()) {
            typeAnalysis.doNotAllowDelaysOnNotModified.set(true);
        }

        if (!typeInfo.typeInspection.get().subTypes.isEmpty() && !typeAnalysis.startedPostAnalysisIntoNestedTypes.isSet()) {
            postAnalysisIntoNestedTypes(typeInfo, debugConfiguration);
            typeAnalysis.startedPostAnalysisIntoNestedTypes.set(true);
        }
    }

    /**
     * Lets analyse the sub-types again, so that we're guaranteed that @NotModified on the enclosing type's methods have been computed.
     *
     * @param typeInfo the enclosing type
     */
    private void postAnalysisIntoNestedTypes(TypeInfo typeInfo, DebugConfiguration debugConfiguration) {
        log(ANALYSER, "\n--------\nStarting post-analysis into method calls from nested types to {}\n--------",
                typeInfo.fullyQualifiedName);
        for (TypeInfo nestedType : typeInfo.typeInspection.get().subTypes) {
            SortedType sortedType = new SortedType(nestedType);
            // the order of analysis is not important anymore, we just have to go over the method calls to the enclosing type

            analyse(sortedType, debugConfiguration, true);
            check(sortedType); // we're not checking at top level!
        }
        log(ANALYSER, "\n--------\nEnded post-analysis into method calls from nested types to {}\n--------",
                typeInfo.fullyQualifiedName);
    }

    private boolean analyseOnlyAndMark(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        if (!typeAnalysis.approvedPreconditions.isEmpty()) {
            return false; // already done
        }
        boolean someModifiedNotSet = typeInfo.typeInspection.get().methods.stream()
                .anyMatch(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someModifiedNotSet) return false;

        boolean allPreconditionsOnModifyingMethodsSet = typeInfo.typeInspection.get().methods.stream()
                .filter(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                .allMatch(methodInfo -> methodInfo.methodAnalysis.get().preconditionForOnlyData.isSet());
        if (!allPreconditionsOnModifyingMethodsSet) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someInvalidPreconditionsOnModifyingMethods = typeInfo.typeInspection.get().methods.stream().anyMatch(methodInfo ->
                methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE &&
                        methodInfo.methodAnalysis.get().preconditionForOnlyData.get() == UnknownValue.NO_VALUE);
        if (someInvalidPreconditionsOnModifyingMethods) {
            log(MARK, "Not all modifying methods have a valid precondition in {}", typeInfo.fullyQualifiedName);
            return false;
        }
        int count = 0;
        Map<Value, String> tempApproved = new HashMap<>();
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            if (modified == Level.TRUE) {
                Value precondition = methodInfo.methodAnalysis.get().preconditionForOnlyData.get();
                if (!tempApproved.containsKey(precondition)) {
                    String markLabel = "mark" + (count > 0 ? ("" + count) : "");
                    tempApproved.put(precondition, markLabel);
                    count++;
                }
            }
        }
        if (tempApproved.isEmpty()) {
            log(MARK, "No modifying methods in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis.approvedPreconditions::put);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, Level.TRUE);
        log(MARK, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return true;
    }

    private boolean analyseContainer(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return false;

        boolean fieldsReady = typeInfo.typeInspection.get().fields.stream().allMatch(
                fieldInfo -> fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL) == Level.FALSE ||
                        fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
        if (!fieldsReady) {
            log(DELAYED, "Delaying container, need assignedToField to be set");
            return false;
        }
        boolean allReady = typeInfo.typeInspection.get().constructorAndMethodStream().allMatch(
                m -> m.methodInspection.get().parameters.stream().allMatch(parameterInfo ->
                        !parameterInfo.parameterAnalysis.get().assignedToField.isSet() ||
                                parameterInfo.parameterAnalysis.get().copiedFromFieldToParameters.isSet()));
        if (!allReady) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            return false;
        }
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isPrivate()) {
                for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                    int modified = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED);
                    if (modified == Level.DELAY) return false; // cannot yet decide
                    if (modified == Level.TRUE) {
                        log(CONTAINER, "{} is not a @Container: the content of {} is modified in {}",
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
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE1Immutable = Level.value(typeImmutable, Level.E1IMMUTABLE);
        if (typeE1Immutable != Level.DELAY) return false; // we have a decision already
        int no = Level.compose(Level.FALSE, Level.E1IMMUTABLE);

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            int effectivelyFinal = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL);
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
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE2Immutable = Level.value(typeImmutable, Level.E2IMMUTABLE);
        if (typeE2Immutable != Level.DELAY) return false; // we have a decision already
        int typeE1Immutable = Level.value(typeImmutable, Level.E1IMMUTABLE);
        if (typeE1Immutable != Level.TRUE && typeAnalysis.approvedPreconditions.isEmpty()) {
            log(E2IMMUTABLE, "Type {} is not @E2Immutable, because it is not (eventually) @E1Immutable", typeInfo.fullyQualifiedName);
            return false;
        }
        int no = Level.compose(Level.FALSE, Level.E2IMMUTABLE);
        boolean eventual = typeAnalysis.isEventual();

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();

            // RULE 1: ALL FIELDS ARE EFFECTIVELY FINAL
            // checked with @E1Immutable, or eventual

            // RULE 2: ALL FIELDS HAVE BEEN ANNOTATED @NotModified UNLESS THEY ARE PRIMITIVE OR @E2Immutable

            int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            int e2immutable = Level.value(immutable, Level.E2IMMUTABLE);
            // TODO for now, we'll not be cascading eventually immutable
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
                int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);

                // we check on !eventual, because in the eventual case, there are no modifying methods callable anymore
                if (!eventual && modified == Level.DELAY) {
                    log(DELAYED, "Field {} not known yet if @NotModified, delaying E2Immutable on type", fieldInfo.fullyQualifiedName());
                    return false;
                }
                if (!eventual && modified == Level.TRUE) {
                    log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and its content is modified",
                            typeInfo.fullyQualifiedName, fieldInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                    return true;
                }

                // RULE 4: ALL FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                        return true;
                    }
                } else {
                    log(E2IMMUTABLE, "Ignoring private modifier check of {}, self-referencing", fieldInfo.fullyQualifiedName());
                }
            }
        }

        // RULE 3: INDEPENDENCE OF CONSTRUCTORS

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().constructors) {
            int independent = methodInfo.methodAnalysis.get().getProperty(VariableProperty.INDEPENDENT);
            if (independent == Level.DELAY) {
                log(DELAYED, "Cannot decide yet about E2Immutable class, no info on @Independent in constructor {}", methodInfo.distinguishingName());
                return false; //not decided
            }
            if (independent == Level.FALSE) {
                log(E2IMMUTABLE, "{} is not an E2Immutable class, because constructor is not @Independent",
                        typeInfo.fullyQualifiedName, methodInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                return true;
            }
        }

        // RULE 5: RETURN TYPES

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            // in the eventual case, we only need to look at the non-modifying methods
            if(modified == Level.FALSE || !eventual) {
                MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
                int returnTypeImmutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                int returnTypeE2Immutable = Level.value(returnTypeImmutable, Level.E2IMMUTABLE);
                if (returnTypeE2Immutable == Level.DELAY) {
                    log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodInfo.distinguishingName());
                    return false;
                }
                if (returnTypeE2Immutable == Level.FALSE) {
                    // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent
                    int independent = methodAnalysis.getProperty(VariableProperty.INDEPENDENT);
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
        }
        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, Level.compose(Level.TRUE, Level.E2IMMUTABLE));
        return true;
    }

    // this one analyses NotNull at level 0, for the whole type
    // TODO we should split this up for methods, fields, parameters, with a "where" variable

    private boolean analyseNotNull(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int typeNotNull = typeAnalysis.getProperty(VariableProperty.NOT_NULL);
        if (typeNotNull != Level.DELAY) return false;

        // all fields should be @NN, all methods, and all parameters...
        boolean isNotNull = true;
        methodLoop:
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                int notNull = methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL);
                if (notNull == Level.DELAY) return false;
                if (notNull == Level.FALSE) {
                    isNotNull = false;
                    break;
                }
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                int notNull = parameterInfo.parameterAnalysis.get().getProperty(VariableProperty.NOT_NULL);
                if (notNull == Level.DELAY) return false;
                if (notNull == Level.FALSE) {
                    isNotNull = false;
                    break methodLoop;
                }
            }
        }
        if (isNotNull) {
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
                int notNull = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.NOT_NULL);
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
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
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

        boolean haveFirstParameter = false;
        ParameterizedType commonTypeOfFirstParameter = null;
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
            if (methodInfo.isStatic && !methodInfo.isPrivate()) {
                List<ParameterInfo> parameters = methodInfo.methodInspection.get().parameters;
                ParameterizedType typeOfFirstParameter;
                if (parameters.isEmpty()) {
                    typeOfFirstParameter = methodInfo.returnType();
                } else {
                    typeOfFirstParameter = parameters.get(0).parameterizedType;
                    haveFirstParameter = true;
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
        boolean isExtensionClass = commonTypeOfFirstParameter != null && haveFirstParameter;
        typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, isExtensionClass);
        log(EXTENSION_CLASS, "Type " + typeInfo.fullyQualifiedName + " marked " + (isExtensionClass ? "" : "not ")
                + "@ExtensionClass");
        return true;
    }

    private boolean analyseUtilityClass(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
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
            if (!methodInfo.methodAnalysis.get().createObjectOfSelf.isSet()) {
                log(UTILITY_CLASS, "Not yet deciding on @Utility class for {}, createObjectOfSelf not yet set on method {}",
                        typeInfo.fullyQualifiedName, methodInfo.name);
                return false;
            }
            if (methodInfo.methodAnalysis.get().createObjectOfSelf.get()) {
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
