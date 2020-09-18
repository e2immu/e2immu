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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.config.*;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.SortedType;
import org.e2immu.analyser.pattern.ConditionalAssignment;
import org.e2immu.analyser.pattern.Pattern;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.Either;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
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

    private final MethodAnalyser methodAnalyser;
    private final FieldAnalyser fieldAnalyser;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();
    private final PatternMatcher patternMatcher;
    private final Configuration configuration;

    public TypeAnalyser(Configuration configuration, @NotNull E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        fieldAnalyser = new FieldAnalyser(e2ImmuAnnotationExpressions);
        methodAnalyser = new MethodAnalyser(e2ImmuAnnotationExpressions);
        this.configuration = configuration;

        // TODO move to some other place
        Pattern pattern1 = ConditionalAssignment.pattern1();
        Pattern pattern2 = ConditionalAssignment.pattern2();
        Pattern pattern3 = ConditionalAssignment.pattern3();
        patternMatcher = new PatternMatcher(Map.of(pattern1, ConditionalAssignment.replacement1ToPattern1(pattern1),
                pattern2, ConditionalAssignment.replacement1ToPattern2(pattern2),
                pattern3, ConditionalAssignment.replacement1ToPattern3(pattern3)));

        this.e2ImmuAnnotationExpressions = Objects.requireNonNull(e2ImmuAnnotationExpressions);
    }

    public Stream<Message> getMessageStream() {
        return Stream.concat(Stream.concat(fieldAnalyser.getMessageStream(),
                methodAnalyser.getMessageStream()), messages.getMessageStream());
    }

    public void check(SortedType sortedType) {
        for (WithInspectionAndAnalysis m : sortedType.methodsFieldsSubTypes) {
            if (m instanceof TypeInfo) check((TypeInfo) m);
            else if (m instanceof MethodInfo) methodAnalyser.check((MethodInfo) m);
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
    }

    private void check(TypeInfo typeInfo) {
        // before we check, we copy the properties into annotations
        log(ANALYSER, "\n******\nAnnotation validation on type {}\n******", typeInfo.fullyQualifiedName);
        typeInfo.typeAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

        check(typeInfo, UtilityClass.class, e2ImmuAnnotationExpressions.utilityClass.get());
        check(typeInfo, E1Immutable.class, e2ImmuAnnotationExpressions.e1Immutable.get());
        check(typeInfo, E1Container.class, e2ImmuAnnotationExpressions.e1Container.get());
        check(typeInfo, ExtensionClass.class, e2ImmuAnnotationExpressions.extensionClass.get());
        check(typeInfo, Container.class, e2ImmuAnnotationExpressions.container.get());
        check(typeInfo, E2Immutable.class, e2ImmuAnnotationExpressions.e2Immutable.get());
        check(typeInfo, E2Container.class, e2ImmuAnnotationExpressions.e2Container.get());
        check(typeInfo, Independent.class, e2ImmuAnnotationExpressions.independent.get());

        // opposites
        check(typeInfo, MutableModifiesArguments.class, e2ImmuAnnotationExpressions.mutableModifiesArguments.get());
    }

    private void check(TypeInfo typeInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        typeInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(typeInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    public void analyse(SortedType sortedType) {
        DebugConfiguration debugConfiguration = configuration.debugConfiguration;
        TypeInfo typeInfo = sortedType.primaryType;
        assert typeInfo.isPrimaryType();

        boolean changes = true;
        int iteration = 0;

        while (changes) {
            log(ANALYSER, "\n******\nStarting iteration {} of the type analyser on {}\n******", iteration, typeInfo.fullyQualifiedName);
            changes = false;

            patternMatcher.startNewIteration();

            for (WithInspectionAndAnalysis member : sortedType.methodsFieldsSubTypes) {
                if (member instanceof MethodInfo) {
                    VariableProperties methodProperties = new VariableProperties(e2ImmuAnnotationExpressions,
                            iteration, configuration, patternMatcher, (MethodInfo) member);

                    if (methodAnalyser.analyse((MethodInfo) member, methodProperties))
                        changes = true;
                    for (MethodAnalyserVisitor methodAnalyserVisitor : debugConfiguration.afterMethodAnalyserVisitors) {
                        methodAnalyserVisitor.visit(iteration, (MethodInfo) member);
                    }
                    messages.addAll(methodProperties.messages);
                } else if (member instanceof FieldInfo) {
                    FieldInfo fieldInfo = (FieldInfo) member;
                    // these are the "hidden" methods: fields of functional interfaces
                    if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
                        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
                        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
                            VariableProperties methodProperties = new VariableProperties(e2ImmuAnnotationExpressions,
                                    iteration, configuration, patternMatcher, fieldInitialiser.implementationOfSingleAbstractMethod);

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

                    VariableProperties fieldProperties = new VariableProperties(e2ImmuAnnotationExpressions,
                            iteration, configuration, patternMatcher, fieldInfo);
                    if (fieldAnalyser.analyse(fieldInfo, fieldProperties))
                        changes = true;
                    for (FieldAnalyserVisitor fieldAnalyserVisitor : debugConfiguration.afterFieldAnalyserVisitors) {
                        fieldAnalyserVisitor.visit(iteration, fieldInfo);
                    }
                    messages.addAll(fieldProperties.messages);
                } else {
                    TypeInfo subType = (TypeInfo) member;
                    if (analyseType(subType, iteration, debugConfiguration)) changes = true;
                }
            }

            if (analyseType(typeInfo, iteration, debugConfiguration)) changes = true;

            iteration++;
            if (iteration > 10) {
                throw new UnsupportedOperationException("More than 10 iterations needed for type " + typeInfo.simpleName + "?");
            }
        }
    }

    private boolean analyseType(TypeInfo typeInfo, int iteration, DebugConfiguration debugConfiguration) {
        boolean changes = analyseImplicitlyImmutableTypes(typeInfo);

        log(ANALYSER, "Analysing type {}", typeInfo.fullyQualifiedName);

        if (typeInfo.hasBeenDefined() && !typeInfo.isInterface()) {
            if (analyseOnlyMarkEventuallyE1Immutable(typeInfo)) changes = true;
            if (analyseEffectivelyE1Immutable(typeInfo)) changes = true;
            if (analyseIndependent(typeInfo)) changes = true;
            if (analyseEffectivelyEventuallyE2Immutable(typeInfo)) changes = true;
            if (analyseContainer(typeInfo)) changes = true;
            if (analyseUtilityClass(typeInfo)) changes = true;
            if (analyseExtensionClass(typeInfo)) changes = true;
        }

        for (TypeAnalyserVisitor typeAnalyserVisitor : debugConfiguration.afterTypePropertyComputations) {
            typeAnalyserVisitor.visit(iteration, typeInfo);
        }

        return changes;
    }

    private boolean analyseImplicitlyImmutableTypes(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        if (typeAnalysis.implicitlyImmutableDataTypes.isSet()) return false;

        log(E2IMMUTABLE, "Computing implicitly immutable types for {}", typeInfo.fullyQualifiedName);
        Set<ParameterizedType> typesOfFields = typeInfo.typeInspection.getPotentiallyRun().fields.stream()
                .map(fieldInfo -> fieldInfo.type).collect(Collectors.toCollection(HashSet::new));
        typesOfFields.addAll(typeInfo.typesOfMethodsAndConstructors());
        typesOfFields.addAll(typesOfFields.stream().flatMap(pt -> pt.components(false).stream()).collect(Collectors.toList()));
        log(E2IMMUTABLE, "Types of fields, methods and constructors: {}", typesOfFields);

        Set<ParameterizedType> explicitTypes = typeInfo.explicitTypes();
        log(E2IMMUTABLE, "Explicit types: {}", explicitTypes);

        typesOfFields.removeIf(type -> {
            if (type.arrays > 0) return true;
            if (type.isUnboundParameterType()) return false;

            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            boolean self = type.typeInfo == typeInfo;
            if (self || typeInfo.isPrimitiveOrBoxed()) return true;
            return explicitTypes.contains(type) || explicitTypes.stream().anyMatch(type::isAssignableFrom);
        });

        // e2immu is more work, we need to check delays
        boolean e2immuDelay = typesOfFields.stream().anyMatch(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            return immutable == MultiLevel.DELAY && bestType.hasBeenDefined();
        });
        if (e2immuDelay) {
            log(DELAYED, "Delaying implicitly immutable data types on {} because of immutable", typeInfo.fullyQualifiedName);
            return false;
        }
        typesOfFields.removeIf(type -> {
            TypeInfo bestType = type.bestTypeInfo();
            if (bestType == null) return false;
            int immutable = bestType.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            return MultiLevel.isAtLeastEventuallyE2Immutable(immutable);
        });

        typeAnalysis.implicitlyImmutableDataTypes.set(ImmutableSet.copyOf(typesOfFields));
        log(E2IMMUTABLE, "Implicitly immutable data types for {} are: [{}]", typeInfo.fullyQualifiedName, typesOfFields);
        return true;
    }

    /*
      writes: typeAnalysis.approvedPreconditions, the official marker for eventuality in the type

      when? all modifying methods must have methodAnalysis.preconditionForOnlyData set with value != NO_VALUE

      TODO: parents, enclosing types
     */
    private boolean analyseOnlyMarkEventuallyE1Immutable(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        if (!typeAnalysis.approvedPreconditions.isEmpty()) {
            return false; // already done
        }
        final TypeInspection.Methods methodsMode = TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM;
        boolean someModifiedNotSet = typeInfo.typeInspection.getPotentiallyRun()
                .methodStream(methodsMode)
                .anyMatch(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someModifiedNotSet) return false;

        boolean allPreconditionsOnModifyingMethodsSet = typeInfo.typeInspection.getPotentiallyRun()
                .methodStream(methodsMode)
                .filter(methodInfo -> methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE)
                .allMatch(methodInfo -> methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.isSet());
        if (!allPreconditionsOnModifyingMethodsSet) {
            log(DELAYED, "Not all precondition preps on modifying methods have been set in {}, delaying", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someInvalidPreconditionsOnModifyingMethods = typeInfo.typeInspection.getPotentiallyRun()
                .methodStream(methodsMode).anyMatch(methodInfo ->
                        methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE &&
                                methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get().isEmpty());
        if (someInvalidPreconditionsOnModifyingMethods) {
            log(MARK, "Not all modifying methods have a valid precondition in {}", typeInfo.fullyQualifiedName);
            return false;
        }

        Map<String, Value> tempApproved = new HashMap<>();
        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods(methodsMode)) {
            int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            if (modified == Level.TRUE) {
                List<Value> preconditions = methodInfo.methodAnalysis.get().preconditionForMarkAndOnly.get();
                for (Value precondition : preconditions) {
                    handlePrecondition(methodInfo, precondition, tempApproved);
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

    private void handlePrecondition(MethodInfo methodInfo, Value precondition, Map<String, Value> tempApproved) {
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

    public static boolean assignmentIncompatibleWithPrecondition(Value precondition, MethodInfo methodInfo) {
        Set<Variable> variables = precondition.variables();
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        for (Variable variable : variables) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
            // fieldSummaries are set after the first iteration
            if (methodInfo.methodAnalysis.get().fieldSummaries.isSet(fieldInfo)) {
                TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);
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
            }
        }
        return false;
    }

    private static boolean isCompatible(Value v1, Value v2) {
        Value and = new AndValue().append(v1, v2);
        return v1.equals(and) || v2.equals(and);
    }

    public static String labelOfPreconditionForMarkAndOnly(List<Value> values) {
        return values.stream().map(TypeAnalyser::labelOfPreconditionForMarkAndOnly).sorted().collect(Collectors.joining(","));
    }

    public static String labelOfPreconditionForMarkAndOnly(Value value) {
        return value.variables().stream().map(Variable::name).distinct().sorted().collect(Collectors.joining("+"));
    }

    private boolean analyseContainer(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        int container = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (container != Level.UNDEFINED) return false;

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(typeInfo, VariableProperty.CONTAINER, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean fieldsReady = typeInfo.typeInspection.getPotentiallyRun().fields.stream().allMatch(
                fieldInfo -> fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.FINAL) == Level.FALSE ||
                        fieldInfo.fieldAnalysis.get().effectivelyFinalValue.isSet());
        if (!fieldsReady) {
            log(DELAYED, "Delaying container, need assignedToField to be set");
            return false;
        }
        boolean allReady = typeInfo.typeInspection.getPotentiallyRun().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY).allMatch(
                m -> m.methodInspection.get().parameters.stream().allMatch(parameterInfo ->
                        !parameterInfo.parameterAnalysis.get().assignedToField.isSet() ||
                                parameterInfo.parameterAnalysis.get().copiedFromFieldToParameters.isSet()));
        if (!allReady) {
            log(DELAYED, "Delaying container, variables linked to fields and params not yet set");
            return false;
        }
        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methodsAndConstructors()) {
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

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(typeInfo, VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEffectiveToDelayTrue(MultiLevel.value(i, MultiLevel.E1IMMUTABLE)), MultiLevel.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        for (FieldInfo fieldInfo : typeInfo.typeInspection.getPotentiallyRun().fields) {
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

    private static int convertMultiLevelEffectiveToDelayTrue(int i) {
        if (i <= MultiLevel.DELAY) return Level.DELAY;
        if (i == MultiLevel.EFFECTIVE) return Level.TRUE;
        return Level.FALSE;
    }

    /**
     * 4 different rules to enforce:
     * <p>
     * RULE 1: All constructor parameters linked to fields/fields linked to constructor parameters must be @NotModified
     * <p>
     * RULE 2: All fields linking to constructor parameters must be either private or E2Immutable
     * <p>
     * RULE 3: All return values of methods must be independent of the fields linking to constructor parameters
     * <p>
     * We obviously start by collecting exactly these fields.
     *
     * @param typeInfo the type to analyse
     * @return true if a decision was made
     */
    private boolean analyseIndependent(TypeInfo typeInfo) {
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        int typeIndependent = typeAnalysis.getProperty(VariableProperty.INDEPENDENT);
        if (typeIndependent != Level.DELAY) return false;

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(typeInfo, VariableProperty.INDEPENDENT, Function.identity(), Level.FALSE);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean variablesLinkedNotSet = typeInfo.typeInspection.getPotentiallyRun().fields.stream()
                .anyMatch(fieldInfo -> !fieldInfo.fieldAnalysis.get().variablesLinkedToMe.isSet());
        if (variablesLinkedNotSet) {
            log(DELAYED, "Delay independence of type {}, not all variables linked to fields set", typeInfo.fullyQualifiedName);
            return false;
        }
        List<FieldInfo> fieldsLinkedToParameters =
                typeInfo.typeInspection.getPotentiallyRun().fields.stream().filter(fieldInfo -> fieldInfo.fieldAnalysis.get().
                        variablesLinkedToMe.get().stream().filter(v -> v instanceof ParameterInfo)
                        .map(v -> (ParameterInfo) v).anyMatch(pi -> pi.owner.isConstructor)).collect(Collectors.toList());

        // RULE 1

        boolean modificationStatusUnknown = fieldsLinkedToParameters.stream().anyMatch(fieldInfo -> fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (modificationStatusUnknown) {
            log(DELAYED, "Delay independence of type {}, modification status of linked fields not yet set", typeInfo.fullyQualifiedName);
            return false;
        }
        boolean someModified = fieldsLinkedToParameters.stream().anyMatch(fieldInfo -> fieldInfo.fieldAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.TRUE);
        if (someModified) {
            log(INDEPENDENT, "Type {} cannot be @Independent, some fields linked to parameters are modified", typeInfo.fullyQualifiedName);
            typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
            return true;
        }

        // RULE 2

        List<FieldInfo> nonPrivateFields = fieldsLinkedToParameters.stream().filter(fieldInfo -> !fieldInfo.isPrivate()).collect(Collectors.toList());
        for (FieldInfo nonPrivateField : nonPrivateFields) {
            int immutable = nonPrivateField.fieldAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
            if (immutable == Level.DELAY) {
                log(DELAYED, "Delay independence of type {}, field {} is not known to be immutable", typeInfo.fullyQualifiedName,
                        nonPrivateField.name);
                return false;
            }
            if (!MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                log(INDEPENDENT, "Type {} cannot be @Independent, field {} is non-private and not level 2 immutable",
                        typeInfo.fullyQualifiedName, nonPrivateField.name);
                typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                return true;
            }
        }

        // RULE 3

        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods(TypeInspection.Methods.THIS_TYPE_ONLY)) {
            if (!typeAnalysis.implicitlyImmutableDataTypes.get().contains(methodInfo.returnType())) {
                boolean notAllSet = methodInfo.methodAnalysis.get().returnStatementSummaries.stream().map(Map.Entry::getValue)
                        .anyMatch(tv -> !tv.linkedVariables.isSet());
                if (notAllSet) {
                    log(DELAYED, "Delay independence of type {}, method {}'s return statement summaries linking not known",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    return false;
                }
                boolean safeMethod = methodInfo.methodAnalysis.get().returnStatementSummaries.stream().map(Map.Entry::getValue)
                        .allMatch(tv -> {
                            Set<FieldInfo> linkedFields = tv.linkedVariables.get().stream()
                                    .filter(v -> v instanceof FieldReference)
                                    .map(v -> ((FieldReference) v).fieldInfo)
                                    .collect(Collectors.toSet());
                            return Collections.disjoint(linkedFields, fieldsLinkedToParameters);
                        });
                if (!safeMethod) {
                    log(INDEPENDENT, "Type {} cannot be @Independent, method {}'s return values link to some of the fields linked to constructors",
                            typeInfo.fullyQualifiedName, methodInfo.name);
                    typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.FALSE);
                    return true;
                }
            }
        }

        log(INDEPENDENT, "Improve type {} to @Independent", typeInfo.fullyQualifiedName);
        typeAnalysis.improveProperty(VariableProperty.INDEPENDENT, MultiLevel.EFFECTIVE);
        return true;
    }

    private static Boolean parentOrEnclosingMustHaveTheSameProperty(TypeInfo typeInfo, VariableProperty variableProperty,
                                                                    Function<Integer, Integer> mapProperty,
                                                                    int falseValue) {
        List<TypeInfo> parentAndOrEnclosing = parentAndOrEnclosing(typeInfo);
        List<Integer> propertyValues = parentAndOrEnclosing.stream()
                .map(t -> mapProperty.apply(t.typeAnalysis.get().getProperty(variableProperty)))
                .collect(Collectors.toList());
        if (propertyValues.stream().anyMatch(level -> level == Level.DELAY)) {
            log(DELAYED, "Waiting with {} on {}, parent or enclosing class's status not yet known",
                    variableProperty, typeInfo.fullyQualifiedName);
            return null;
        }
        if (propertyValues.stream().anyMatch(level -> level != Level.TRUE)) {
            log(DELAYED, "{} cannot be {}, parent or enclosing class is not", typeInfo.fullyQualifiedName, variableProperty);
            typeInfo.typeAnalysis.get().improveProperty(variableProperty, falseValue);
            return true;
        }
        return false;
    }

    private static List<TypeInfo> parentAndOrEnclosing(TypeInfo typeInfo) {
        Either<String, TypeInfo> pe = typeInfo.typeInspection.getPotentiallyRun().packageNameOrEnclosingType;
        return ListUtil.immutableConcat(
                pe.isRight() && !typeInfo.isStatic() ? List.of(pe.getRight()) : List.of(),
                typeInfo.typeInspection.getPotentiallyRun().parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT ?
                        List.of(typeInfo.typeInspection.getPotentiallyRun().parentClass.typeInfo) : List.of()
        );
    }
    /*
       List<TypeInfo> mustBeE1Too = parentAndOrEnclosing(typeInfo);
        List<Integer> mustBeE1TooLevels = mustBeE1Too.stream()
                .map(t -> MultiLevel.value(t.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE), MultiLevel.E1IMMUTABLE))
                .collect(Collectors.toList());
        if (mustBeE1TooLevels.stream().anyMatch(level -> level == MultiLevel.DELAY)) {
            log(DELAYED, "Waiting with E1Immutable on {}, parent or enclosing class's E1Immutable status not yet known",
                    typeInfo.fullyQualifiedName);
            return false;
        }
        if (mustBeE1TooLevels.stream().anyMatch(level -> level != MultiLevel.EFFECTIVE)) {
            log(DELAYED, "{} cannot be level 1 immutable, parent or enclosing class is not", typeInfo.fullyQualifiedName);
            typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
            return true;
        }

     */

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

        Boolean parentOrEnclosing = parentOrEnclosingMustHaveTheSameProperty(typeInfo, VariableProperty.IMMUTABLE,
                i -> convertMultiLevelEventualToDelayTrue(MultiLevel.value(i, MultiLevel.E2IMMUTABLE)), no);
        if (parentOrEnclosing == null) return false;
        if (parentOrEnclosing) return true;

        boolean eventual = typeAnalysis.isEventual();
        boolean haveToEnforcePrivateAndIndependenceRules = false;

        for (FieldInfo fieldInfo : typeInfo.typeInspection.getPotentiallyRun().fields) {
            FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
            if (!fieldAnalysis.isOfImplicitlyImmutableDataType.isSet()) {
                log(DELAYED, "Field {} not yet known if @SupportData, delaying @E2Immutable on type", fieldInfo.fullyQualifiedName());
                return false;
            }
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

                boolean fieldRequiresRules = !fieldAnalysis.isOfImplicitlyImmutableDataType.get();
                haveToEnforcePrivateAndIndependenceRules |= fieldRequiresRules;

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
                    if (!fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE) && fieldRequiresRules) {
                        log(E2IMMUTABLE, "{} is not an E2Immutable class, because field {} is not primitive, " +
                                        "not @E2Immutable, not implicitly immutable, and also exposed (not private)",
                                typeInfo.fullyQualifiedName, fieldInfo.name);
                        typeAnalysis.improveProperty(VariableProperty.IMMUTABLE, no);
                        return true;
                    }
                } else {
                    log(E2IMMUTABLE, "Ignoring private modifier check of {}, self-referencing", fieldInfo.fullyQualifiedName());
                }
            }
        }

        if (haveToEnforcePrivateAndIndependenceRules) {

            for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().constructors) {
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

            for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods) {
                if (methodInfo.isVoid()) continue; // we're looking at return types
                int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
                // in the eventual case, we only need to look at the non-modifying methods
                // calling a modifying method will result in an error
                if (modified == Level.FALSE || !typeAnalysis.isEventual()) {
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
                        if (independent == MultiLevel.FALSE) {
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

    private static int convertMultiLevelEventualToDelayTrue(int i) {
        if (i <= MultiLevel.DELAY) return Level.DELAY;
        if (i >= MultiLevel.EVENTUAL) return Level.TRUE;
        return Level.FALSE;
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
        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)) {
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

        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)) {
            if (!methodInfo.isStatic) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " is not a @UtilityClass, method {} is not static", methodInfo.name);
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }
        // this is technically enough, but we'll verify the constructors (should be private)
        for (MethodInfo constructor : typeInfo.typeInspection.getPotentiallyRun().constructors) {
            if (!constructor.isPrivate()) {
                log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                        " looks like a @UtilityClass, but its constructors are not all private");
                typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
                return true;
            }
        }

        if (typeInfo.typeInspection.getPotentiallyRun().constructors.isEmpty()) {
            log(UTILITY_CLASS, "Type " + typeInfo.fullyQualifiedName +
                    " is not a @UtilityClass: it has no private constructors");
            typeAnalysis.setProperty(VariableProperty.UTILITY_CLASS, Level.FALSE);
            return true;
        }

        // and there should be no means of generating an object
        for (MethodInfo methodInfo : typeInfo.typeInspection.getPotentiallyRun().methods(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)) {
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
