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

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.config.MethodAnalyserVisitor;
import org.e2immu.analyser.config.TypeAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeAnalyser.class);

    public static final int POST_ANALYSIS = 100;
    private final MethodAnalyser methodAnalyser;
    private final FieldAnalyser fieldAnalyser;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();

    public TypeAnalyser(@NotNull E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        fieldAnalyser = new FieldAnalyser(e2ImmuAnnotationExpressions);
        methodAnalyser = new MethodAnalyser(e2ImmuAnnotationExpressions);
        this.e2ImmuAnnotationExpressions = Objects.requireNonNull(e2ImmuAnnotationExpressions);
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(Stream.concat(fieldAnalyser.getMessageStream(),
                methodAnalyser.getMessageStream()), messages.getMessageStream());
    }

    public void check(SortedType sortedType) {
        TypeInfo typeInfo = sortedType.typeInfo;

        // before we check, we copy the properties into annotations
        typeInfo.typeAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

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
        check(typeInfo, UtilityClass.class, e2ImmuAnnotationExpressions.utilityClass.get());
        check(typeInfo, E1Immutable.class, e2ImmuAnnotationExpressions.e1Immutable.get());
        check(typeInfo, E1Container.class, e2ImmuAnnotationExpressions.e1Container.get());
        check(typeInfo, ExtensionClass.class, e2ImmuAnnotationExpressions.extensionClass.get());
        check(typeInfo, Container.class, e2ImmuAnnotationExpressions.container.get());
        check(typeInfo, E2Immutable.class, e2ImmuAnnotationExpressions.e2Immutable.get());
        check(typeInfo, E2Container.class, e2ImmuAnnotationExpressions.e2Container.get());

        // opposites
        check(typeInfo, BeforeMark.class, e2ImmuAnnotationExpressions.beforeMark.get());
        check(typeInfo, MutableModifiesArguments.class, e2ImmuAnnotationExpressions.mutableModifiesArguments.get());
    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    public void analyse(SortedType sortedType, DebugConfiguration debugConfiguration, boolean postAnalysis) {
        TypeInfo typeInfo = sortedType.typeInfo;
        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);

        // this needs to be done as soon as, so that @Independent computations can be based on the support data only
        boolean changes = analyseSupportDataTypes(typeInfo);

        int cnt = 0;
        while (changes) {
            log(ANALYSER, "\n******\nStarting iteration {} of the type analyser on {}\n******", cnt, typeInfo.fullyQualifiedName);
            changes = false;

            int iteration = postAnalysis ? POST_ANALYSIS + cnt : cnt;
            // TODO check that separate field properties work...
            //VariableProperties fieldProperties = new VariableProperties(typeContext, typeInfo, iteration, debugConfiguration);

            for (WithInspectionAndAnalysis member : sortedType.methodsAndFields) {
                if (member instanceof MethodInfo) {
                    VariableProperties methodProperties = new VariableProperties(iteration, debugConfiguration, (MethodInfo) member);

                    for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.beforeMethodAnalyserVisitors) {
                        methodAnalyserVisitor.visit(iteration, (MethodInfo) member);
                    }
                    if (methodAnalyser.analyse((MethodInfo) member, methodProperties))
                        changes = true;
                    for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.afterMethodAnalyserVisitors) {
                        methodAnalyserVisitor.visit(iteration, (MethodInfo) member);
                    }
                    messages.addAll(methodProperties.messages);
                } else {
                    FieldInfo fieldInfo = (FieldInfo) member;
                    // these are the "hidden" methods: fields of functional interfaces
                    if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                            VariableProperties methodProperties = new VariableProperties(iteration, debugConfiguration,
                                    fieldInitialiser.implementationOfSingleAbstractMethod);

                            for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.beforeMethodAnalyserVisitors) {
                                methodAnalyserVisitor.visit(iteration, fieldInitialiser.implementationOfSingleAbstractMethod);
                            }
                            try {
                                if (methodAnalyser.analyse(fieldInitialiser.implementationOfSingleAbstractMethod, methodProperties)) {
                                    changes = true;
                                }
                            } catch (RuntimeException rte) {
                                LOGGER.warn("Caught exception in method analysis of SAM of field " + fieldInfo.fullyQualifiedName());
                                if (fieldInitialiser.artificial) {
                                    LOGGER.warn("Method's code is artificial:\n{}", fieldInitialiser.implementationOfSingleAbstractMethod.stream(0));
                                }
                                throw rte;
                            }
                            for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.afterMethodAnalyserVisitors) {
                                methodAnalyserVisitor.visit(iteration, fieldInitialiser.implementationOfSingleAbstractMethod);
                            }
                            messages.addAll(methodProperties.messages);
                        }
                    }

                    for (FieldAnalyserVisitor fieldAnalyserVisitor : debugConfiguration.beforeFieldAnalyserVisitors) {
                        fieldAnalyserVisitor.visit(iteration, fieldInfo);
                    }
                    VariableProperties fieldProperties = new VariableProperties(iteration, debugConfiguration, fieldInfo);
                    if (fieldAnalyser.analyse(fieldInfo, new This(typeInfo), fieldProperties))
                        changes = true;
                    for (FieldAnalyserVisitor fieldAnalyserVisitor : debugConfiguration.afterFieldAnalyserVisitors) {
                        fieldAnalyserVisitor.visit(iteration, fieldInfo);
                    }
                    messages.addAll(fieldProperties.messages);
                }
            }

            for (TypeAnalyserVisitor typeAnalyserVisitor : debugConfiguration.beforeTypePropertyComputations) {
                typeAnalyserVisitor.visit(iteration, typeInfo);
            }

            if (typeInfo.hasBeenDefined()) {
                if (analyseOnlyMarkEventuallyE1Immutable(typeInfo)) changes = true;
                if (analyseEffectivelyE1Immutable(typeInfo)) changes = true;
                if (analyseEffectivelyEventuallyE2Immutable(typeInfo)) changes = true;
                if (analyseContainer(typeInfo)) changes = true;
                if (analyseUtilityClass(typeInfo)) changes = true;
                if (analyseExtensionClass(typeInfo)) changes = true;
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

    private boolean analyseSupportDataTypes(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        if (typeAnalysis.supportDataTypes.isSet()) return false;

        log(E2IMMUTABLE, "Computing support types for {}", typeInfo.fullyQualifiedName);
        Set<ParameterizedType> typesOfFields = typeInfo.typeInspection.get().fields.stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        Set<ParameterizedType> typesOfMethodsAndConstructors = typeInfo.typesOfMethodsAndConstructors();

        onlyKeepSupportData(typesOfMethodsAndConstructors, typesOfFields);

        typeAnalysis.supportDataTypes.set(ImmutableSet.copyOf(typesOfFields));
        log(E2IMMUTABLE, "Support types for {} are: [{}]", typeInfo.fullyQualifiedName,
                StringUtil.join(typesOfFields, ParameterizedType::detailedString));
        return true;
    }

    private void onlyKeepSupportData(Set<ParameterizedType> typesOfMethodsAndConstructors, Set<ParameterizedType> supportData) {
        supportData.removeIf(ParameterizedType::cannotBeSupportData);
        supportData.removeIf(type -> {
            boolean keep = typesOfMethodsAndConstructors.stream().anyMatch(type::containsComponent);
            if (!keep) {
                log(E2IMMUTABLE, "Removing {} because none of {} are components", type.detailedString(), typesOfMethodsAndConstructors);
            }
            return !keep;
        });
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

    /*
      writes: typeAnalysis.approvedPreconditions, the official marker for eventuality in the type

      when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE
     */
    private boolean analyseOnlyMarkEventuallyE1Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        if (!typeAnalysis.approvedPreconditions.isEmpty()) {
            return false; // already done
        }
        final TypeInspection.Methods methodsMode = TypeInspection.Methods.EXCLUDE_FIELD_SAM;
        boolean someModifiedNotSet = typeInfo.typeInspection.get()
                .methodStream(methodsMode)
                .anyMatch(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someModifiedNotSet) return false;

        boolean allPreconditionsOnModifyingMethodsSet = typeInfo.typeInspection.get()
                .methodStream(methodsMode)
                .filter(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                .allMatch(methodInfo -> methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.isSet());
        if (!allPreconditionsOnModifyingMethodsSet) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someInvalidPreconditionsOnModifyingMethods = typeInfo.typeInspection.get()
                .methodStream(methodsMode).anyMatch(methodInfo ->
                        methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE &&
                                methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get() == UnknownValue.NO_VALUE);
        if (someInvalidPreconditionsOnModifyingMethods) {
            log(MARK, "Not all modifying methods have a valid precondition in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        Map<String, Value> tempApproved = new HashMap<>();
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(methodsMode)) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            if (modified == Level.TRUE) {
                Value precondition = methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get();
                Value negated = NegatedValue.negate(precondition);
                String label = labelOfPreconditionForMarkAndOnly(precondition);
                Value inMap = tempApproved.get(label);

                boolean isMark = assignmentIncompatibleWithPrecondition(precondition, methodInfo);
                if (isMark) {
                    if (inMap == null) {
                        tempApproved.put(label, precondition);
                    } else if (inMap.equals(precondition)) {
                        log(MARK, "OK, precondition for {} turns out to be 'before' already", label);
                    } else if (inMap.equals(negated)) {
                        log(MARK, "Precondition for {} turns out to be 'after', we switch");
                        tempApproved.put(label, precondition);
                    }
                } else if (inMap == null) {
                    tempApproved.put(label, precondition); // no idea yet if before or after
                } else if (!inMap.equals(precondition) && !inMap.equals(negated)) {
                    messages.add(Message.newMessage(new Location(methodInfo), Message.DUPLICATE_MARK_LABEL, "Label: " + label));
                }
            }
        }
        if (tempApproved.isEmpty()) {
            log(MARK, "No modifying methods in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        // copy into approved preconditions
        tempApproved.forEach(typeAnalysis.approvedPreconditions::put);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.EVENTUAL);
        log(MARK, "Approved preconditions {} in {}, type is now @E1Immutable(after=)", tempApproved.values(), typeInfo.fullyQualifiedName);
        return true;
    }

    public static boolean assignmentIncompatibleWithPrecondition(Value precondition, MethodInfo methodInfo) {
        Set<Variable> variables = precondition.variables();
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        return variables.stream().anyMatch(variable -> {
            TransferValue tv = methodAnalysis.fieldSummaries.get(((FieldReference) variable).fieldInfo);
            boolean assigned = tv.properties.get(VariableProperty.ASSIGNED) >= Level.READ_ASSIGN_ONCE;
            log(MARK, "Field {} is assigned in {}? {}", variable.name(), methodInfo.distinguishingName(), assigned);

            if (assigned && tv.stateOnAssignment.isSet()) {
                Value state = tv.stateOnAssignment.get();
                if (isCompatible(state, precondition)) {
                    log(MARK, "We checked, and found the state {} compatible with the precondition {}", state, precondition);
                    return false;
                }
            }

            return assigned;
        });
    }

    private static boolean isCompatible(Value v1, Value v2) {
        Value and = new AndValue().append(v1, v2);
        return v1.equals(and) || v2.equals(and);
    }

    public static String labelOfPreconditionForMarkAndOnly(Value value) {
        return value.variables().stream().map(Variable::name).distinct().sorted().collect(Collectors.joining("+"));
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
        boolean allReady = typeInfo.typeInspection.get().constructorAndMethodStream(TypeInspection.Methods.ALL).allMatch(
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

    private boolean analyseEffectivelyE1Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int typeE1Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable != MultiLevel.DELAY) return false; // we have a decision already

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            int effectivelyFinal = fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.DELAY) return false; // cannot decide
            if (effectivelyFinal == Level.FALSE) {
                log(E1IMMUTABLE, "Type {} cannot be @E1Immutable, field {} is not effectively final",
                        typeInfo.fullyQualifiedName, fieldInfo.name);
                typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
                return true;
            }
        }
        log(E1IMMUTABLE, "Improve IMMUTABLE property of type {} to @E1Immutable", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.EFFECTIVELY_E1IMMUTABLE);
        return true;
    }

    /**
     * Rules as of 30 July 2020: Definition on top of @E1Immutable
     * <p>
     * RULE 1: All fields must be @NotModified.
     * <p>
     * RULE 2: All @SupportData fields must be private, or their types must be level 2 immutable
     * <p>
     * RULE 3: All methods and constructors must be independent of the @SupportData fields
     *
     * @param typeInfo The type to be analysed
     * @return true if a change was made to typeAnalysis
     */
    private boolean analyseEffectivelyEventuallyE2Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        int typeImmutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int typeE2Immutable = MultiLevel.value(typeImmutable, MultiLevel.E2IMMUTABLE);
        if (typeE2Immutable != MultiLevel.DELAY) return false; // we have a decision already
        int typeE1Immutable = MultiLevel.value(typeImmutable, MultiLevel.E1IMMUTABLE);
        if (typeE1Immutable < MultiLevel.EVENTUAL) {
            log(E2IMMUTABLE, "Type {} is not (yet) @E2Immutable, because it is not (yet) (eventually) @E1Immutable", typeInfo.fullyQualifiedName);
            return false;
        }
        int no = MultiLevel.compose(typeE1Immutable, MultiLevel.FALSE);
        boolean eventual = typeAnalysis.isEventual();
        boolean haveSupportData = false;

        for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields) {
            FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
            if (!fieldAnalysis.supportData.isSet()) {
                log(DELAYED, "Field {} not yet known if @SupportData, delaying @E2Immutable on type", fieldInfo.fullyQualifiedName());
                return false;
            }
            boolean supportData = fieldAnalysis.supportData.get();
            haveSupportData |= supportData;

            // RULE 1: ALL FIELDS MUST BE NOT MODIFIED

            // this follows automatically if they are primitive or E2Immutable themselves

            int fieldImmutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
            int fieldE2Immutable = MultiLevel.value(fieldImmutable, MultiLevel.E2IMMUTABLE);
            // field is of the type of the class being analysed... it will not make the difference.
            if (fieldE2Immutable == MultiLevel.DELAY && typeInfo == fieldInfo.type.typeInfo) {
                fieldE2Immutable = MultiLevel.EFFECTIVE;
            }
            // part of rule 2: we now need to check that @NotModified is on the field
            if (fieldE2Immutable == MultiLevel.DELAY) {
                log(DELAYED, "Field {} not known yet if @E2Immutable, delaying @E2Immutable on type", fieldInfo.fullyQualifiedName());
                return false;
            }
            // we're allowing eventualities to cascade!
            if (fieldE2Immutable < MultiLevel.EVENTUAL) {
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

                // RULE 2: ALL @SupportData FIELDS NON-PRIMITIVE NON-E2IMMUTABLE MUST HAVE ACCESS MODIFIER PRIVATE
                if (fieldInfo.type.typeInfo != typeInfo) {
                    if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE) && supportData) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, not @E2Immutable, is support data, and also exposed (not private)",
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

        if (haveSupportData) {
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
        }

        // RULE 3: RETURN TYPES OF METHODS

        if (haveSupportData) {
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods) {
                if (methodInfo.isVoid()) continue; // we're looking at return types
                int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
                // in the eventual case, we only need to look at the non-modifying methods
                if (modified == Level.FALSE || !eventual) {
                    MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
                    int returnTypeImmutable = methodAnalysis.getProperty(VariableProperty.IMMUTABLE);
                    int returnTypeE2Immutable = MultiLevel.value(returnTypeImmutable, MultiLevel.E2IMMUTABLE);
                    if (returnTypeE2Immutable == MultiLevel.DELAY) {
                        log(DELAYED, "Return type of {} not known if @E2Immutable, delaying", methodInfo.distinguishingName());
                        return false;
                    }
                    if (returnTypeE2Immutable < MultiLevel.EVENTUAL) {
                        // rule 5, continued: if not primitive, not E2Immutable, then the result must be Independent of the support types
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
        }

        log(E2IMMUTABLE, "Improve @Immutable of type {} to @E2Immutable", typeInfo.fullyQualifiedName);
        int e2Immutable = eventual ? MultiLevel.EVENTUAL : MultiLevel.EFFECTIVE;
        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.compose(typeE1Immutable, e2Immutable));
        return true;
    }

    private boolean analyseExtensionClass(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int extensionClass = typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS);
        if (extensionClass != Level.DELAY) return false;

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not an @ExtensionClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.EXTENSION_CLASS, Level.FALSE);
            return true;
        }

        boolean haveFirstParameter = false;
        ParameterizedType commonTypeOfFirstParameter = null;
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(TypeInspection.Methods.EXCLUDE_FIELD_SAM)) {
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

        int e2Immutable = MultiLevel.value(typeAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Don't know yet about @E2Immutable on {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        if (e2Immutable < MultiLevel.EVENTUAL) {
            log(UTILITY_CLASS, "Type {} is not a @UtilityClass, not (eventually) @E2Immutable", typeInfo.fullyQualifiedName);
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(TypeInspection.Methods.EXCLUDE_FIELD_SAM)) {
            if (!methodInfo.isStatic) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInfo.typeInspection.get().constructors) {
            if (!constructor.isPrivate()) {
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
        for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(TypeInspection.Methods.EXCLUDE_FIELD_SAM)) {
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
