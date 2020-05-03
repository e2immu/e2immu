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
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.e2immu.analyser.model.value.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.model.value.UnknownValue.UNKNOWN_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final TypeContext typeContext;
    private static final Set<AnnotationExpression> INITIAL = new HashSet<>();

    public FieldAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        boolean changes = false;
        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();
        SetOnceMap<AnnotationExpression, Boolean> annotations = fieldInfo.fieldAnalysis.annotations;
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
        boolean fieldCanBeAccessedFromOutsideThisType = fieldInfo.owner.isRecord();

        // STEP 1: THE INITIALISER

        Value value;
        boolean haveInitialiser;
        if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
            if (fieldInitialiser.initialiser != EmptyExpression.EMPTY_EXPRESSION) {
                VariableProperties localVariableProperties;
                if (fieldInitialiser.implementationOfSingleAbstractMethod == null) {
                    localVariableProperties = fieldProperties;
                } else {
                    localVariableProperties = fieldProperties.copyWithCurrentMethod(fieldInitialiser.implementationOfSingleAbstractMethod);
                }
                value = fieldInitialiser.initialiser.evaluate(localVariableProperties, EvaluationVisitor.NO_VISITOR);
                log(FINAL, "Set initialiser of field {} to {}", fieldInfo.fullyQualifiedName(), value);
                fieldProperties.setValue(fieldReference, value);
                haveInitialiser = true;
            } else {
                value = NO_VALUE; // initialiser set, but to empty expression
                haveInitialiser = false;
            }
        } else {
            value = NO_VALUE;
            haveInitialiser = true;
        }

        // STEP 2: EFFECTIVELY FINAL: @E1Immutable

        if (!annotations.isSet(typeContext.effectivelyFinal.get())) {
            boolean isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            if (isExplicitlyFinal) {
                annotations.put(typeContext.effectivelyFinal.get(), true);
                log(FINAL, "Mark field {} as effectively final, because explicitly so, value {}",
                        fieldInfo.fullyQualifiedName(), value);
                changes = true;
            } else {
                Boolean isModifiedOutsideConstructors = typeInspection.methods.stream()
                        .filter(m -> !m.isPrivate() || m.isCalledFromNonPrivateMethod())
                        .map(m -> m.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo))
                        .reduce(false, TypeAnalyser.TERNARY_OR);

                if (isModifiedOutsideConstructors == null) {
                    log(DELAYED, "Cannot yet conclude if {} is effectively final", fieldInfo.fullyQualifiedName());
                } else {
                    boolean isFinal;
                    if (fieldCanBeAccessedFromOutsideThisType) {
                        // this means other types can write to the field... not final by definition
                        isFinal = false;
                    } else {
                        isFinal = !isModifiedOutsideConstructors;
                    }
                    annotations.put(typeContext.effectivelyFinal.get(), isFinal);
                    log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                            "effectively final, not modified outside constructors", fieldInfo.fullyQualifiedName());
                    changes = true;
                }
            }
        }

        // STEP 3: EFFECTIVELY FINAL VALUE, and @Constant

        if (fieldInfo.isEffectivelyFinal(typeContext) == Boolean.TRUE && !fieldInfo.fieldAnalysis.effectivelyFinalValue.isSet()) {
            // find the constructors where the value is set; if they're all set to the same value,
            // we can set the initial value; also take into account the value of the initialiser, if it is there
            Value consistentValue = value;
            if (!fieldInfo.isExplicitlyFinal()) {
                for (MethodInfo method : typeInspection.methodsAndConstructors()) {
                    if (method.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo) == Boolean.TRUE) {
                        if (method.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                            Value assignment = method.methodAnalysis.fieldAssignmentValues.get(fieldInfo);
                            if (consistentValue == NO_VALUE) consistentValue = assignment;
                            else if (!consistentValue.equals(assignment)) {
                                log(CONSTANT, "Cannot set consistent value for field {}, have {} and {}",
                                        fieldInfo.fullyQualifiedName(), consistentValue, assignment);
                                fieldInfo.fieldAnalysis.effectivelyFinalValue.set(UNKNOWN_VALUE);
                                annotations.put(typeContext.constant.get(), false);
                                return true;
                            }
                        } else {
                            log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                            consistentValue = NO_VALUE;
                            break;
                        }
                    }
                }
            }
            if (consistentValue != NO_VALUE) {
                Value valueToSet;
                if (consistentValue instanceof org.e2immu.analyser.model.Constant) {
                    valueToSet = consistentValue;
                    AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
                    annotations.put(constantAnnotation, true);
                    log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
                } else {
                    valueToSet = new VariableValue(new FieldReference(fieldInfo, thisVariable), consistentValue.dynamicTypeAnnotations(fieldProperties), false, null, null);
                    annotations.put(typeContext.constant.get(), false);
                    log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
                }
                fieldInfo.fieldAnalysis.effectivelyFinalValue.set(valueToSet);
                log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                        fieldInfo.fullyQualifiedName(), consistentValue);
                changes = true;
            }
        }

        // STEP 4: @NotNull

        if (!annotations.isSet(typeContext.notNull.get())) {
            Boolean isNotNullValue;
            Boolean isFinal = fieldInfo.isEffectivelyFinal(typeContext);
            if (isFinal == null) {
                log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
                isNotNullValue = null;
            } else {
                Boolean finalCriteria;
                if (isFinal == Boolean.FALSE) {
                    if (!fieldInfo.isPrivate() || !haveInitialiser || fieldCanBeAccessedFromOutsideThisType) {
                        log(NOT_NULL, "Field {} cannot be @NotNull: it is not @Final, and either not private, or has no initialiser,"
                                + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName());
                        finalCriteria = false;
                    } else {
                        finalCriteria = value.isNotNull(fieldProperties);
                    }
                } else {
                    finalCriteria = true;
                }
                if (finalCriteria == null) {
                    log(DELAYED, "Delaying @NotNull on {} until we know @NotNull of initialiser", fieldInfo.fullyQualifiedName());
                    isNotNullValue = null;
                } else if (finalCriteria) {
                    // to avoid chicken and egg problems we do not look at effectivelyFinalValue, because that one replaces
                    // the real value with a generic VariableValue, relying on @NotNull
                    boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                            m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                                    (!m.methodAnalysis.fieldAssignments.get(fieldInfo) || m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)));
                    if (allAssignmentValuesDefined) {
                        Boolean allAssignmentValuesNotNull = typeInspection.constructorAndMethodStream()
                                .filter(m -> m.methodAnalysis.fieldAssignments.get(fieldInfo) && m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo))
                                .map(m -> m.methodAnalysis.fieldAssignmentValues.get(fieldInfo).isNotNull(fieldProperties))
                                .reduce(true, TypeAnalyser.TERNARY_AND);
                        if (allAssignmentValuesNotNull == null) {
                            isNotNullValue = null; // delay
                        } else {
                            if (!haveInitialiser) {
                                isNotNullValue = allAssignmentValuesNotNull;
                            } else {
                                if (value == NO_VALUE) {
                                    isNotNullValue = null; // delay
                                } else {
                                    Boolean initialiserIsNotNull = value.isNotNull(fieldProperties);
                                    if (initialiserIsNotNull == null) {
                                        isNotNullValue = null; // delay
                                    } else {
                                        // this is the real one!
                                        isNotNullValue = initialiserIsNotNull && allAssignmentValuesNotNull;
                                    }
                                }
                            }
                        }
                    } else {
                        isNotNullValue = null; // delay
                    }
                } else {
                    isNotNullValue = false;
                }
            }
            if (isNotNullValue == null) {
                log(DELAYED, "Delaying @NotNull on field {}", fieldInfo.fullyQualifiedName());
            } else {
                annotations.put(typeContext.notNull.get(), isNotNullValue);
                log(NOT_NULL, "Mark field {} as " + (isNotNullValue ? "" : "NOT ") + "@NotNull",
                        fieldInfo.fullyQualifiedName(), isNotNullValue);
                changes = true;
            }
        }

        // STEP 5: Dynamic type annotations

        // dynamic type annotations come before @NotModified, because any E2Immutable type cannot be modified anyway.
        if (!fieldInfo.fieldAnalysis.dynamicTypeAnnotationsAdded.isSet()) {

            Set<AnnotationExpression> dynamicTypeAnnotations;
            if (fieldCanBeAccessedFromOutsideThisType) {
                dynamicTypeAnnotations = Set.of(); // we don't control
            } else {
                boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                        m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                                (!m.methodAnalysis.fieldAssignments.get(fieldInfo) || m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)));
                if (allAssignmentValuesDefined) {
                    Set<AnnotationExpression> intersection = typeInspection.constructorAndMethodStream()
                            .filter(m -> m.methodAnalysis.fieldAssignments.get(fieldInfo) && m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo))
                            .map(m -> m.methodAnalysis.fieldAssignmentValues.get(fieldInfo).dynamicTypeAnnotations(fieldProperties))
                            .reduce(INITIAL, (prev, curr) -> {
                                if (prev == null || curr == null) return null;
                                if (prev == INITIAL) return new HashSet<>(curr);
                                prev.retainAll(curr);
                                return prev;
                            });
                    if (intersection == null) {
                        dynamicTypeAnnotations = null; // delay
                    } else {
                        if (!haveInitialiser) {
                            dynamicTypeAnnotations = intersection;
                        } else {
                            if (value == NO_VALUE) {
                                dynamicTypeAnnotations = null; // delay
                            } else {
                                Set<AnnotationExpression> dynamicsOfInitialiser = value.dynamicTypeAnnotations(fieldProperties);
                                if (dynamicsOfInitialiser == null) {
                                    dynamicTypeAnnotations = null; // delay
                                } else {
                                    // this is the real one!
                                    intersection.retainAll(dynamicsOfInitialiser);
                                    dynamicTypeAnnotations = intersection;
                                }
                            }
                        }
                    }
                } else {
                    dynamicTypeAnnotations = null; // delay
                }
            }
            if (dynamicTypeAnnotations == null) {
                log(DELAYED, "Delaying @NotNull on field {}", fieldInfo.fullyQualifiedName());
            } else {
                boolean wroteOne = false;
                for (AnnotationExpression ae : new AnnotationExpression[]{typeContext.e2Container.get(), typeContext.e2Immutable.get(),
                        typeContext.e1Container.get(), typeContext.e1Immutable.get(), typeContext.container.get()}) {
                    boolean dynamic = dynamicTypeAnnotations.contains(ae);
                    boolean positive = !wroteOne && dynamic;
                    if (!fieldInfo.fieldAnalysis.annotations.isSet(ae)) {
                        log(E2IMMUTABLE, "Mark field " + fieldInfo.fullyQualifiedName() + " as " + (positive ? "" : "NOT ") + "@" + ae.typeInfo.simpleName);
                        fieldInfo.fieldAnalysis.annotations.put(ae, positive);
                        if (!wroteOne) wroteOne = true;
                    }
                }
                fieldInfo.fieldAnalysis.dynamicTypeAnnotationsAdded.set(true);
            }
        }

        // STEP 6: @NotModified

        if (!annotations.isSet(typeContext.notModified.get())) {
            Boolean isE2Immutable = fieldInfo.isE2Immutable(typeContext);
            if (isE2Immutable == null) {
                log(DELAYED, "Delaying @NotModified, no idea about dynamic or static type");
            } else if (isE2Immutable) {
                annotations.put(typeContext.notModified.get(), false);
                log(NOT_MODIFIED, "Field {} does not need @NotModified, as it is @E2Immutable", fieldInfo.fullyQualifiedName());
                changes = true;
            } else {
                boolean allContentModificationsDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                        m.methodAnalysis.fieldRead.isSet(fieldInfo) &&
                                (!m.methodAnalysis.fieldRead.get(fieldInfo) || m.methodAnalysis.contentModifications.isSet(fieldReference)));
                if (allContentModificationsDefined) {
                    boolean notModified =
                            !fieldCanBeAccessedFromOutsideThisType &&
                                    typeInspection.constructorAndMethodStream()
                                            .filter(m -> m.methodAnalysis.fieldRead.get(fieldInfo))
                                            .noneMatch(m -> m.methodAnalysis.contentModifications.get(fieldReference));
                    annotations.put(typeContext.notModified.get(), notModified);
                    log(NOT_MODIFIED, "Mark field {} as " + (notModified ? "" : "not ") +
                            "@NotModified", fieldInfo.fullyQualifiedName());
                    changes = true;
                } else {
                    log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                            fieldInfo.fullyQualifiedName());
                }
            }
        }

        // STEP 7: @Linked, variablesLinkedToMe

        if (!fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
            boolean allDefined = typeInspection.constructorAndMethodStream()
                    .allMatch(m -> m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                            (!m.methodAnalysis.fieldAssignments.get(fieldInfo) ||
                                    m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference)));
            if (allDefined) {
                Set<Variable> links = new HashSet<>();
                typeInspection.constructorAndMethodStream().forEach(m -> {
                    if (m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference))
                        links.addAll(m.methodAnalysis.fieldsLinkedToFieldsAndVariables.get(fieldReference));
                });
                fieldInfo.fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
                log(LINKED_VARIABLES, "Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));

                AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(typeContext, links);
                annotations.put(linkAnnotation, !links.isEmpty());
                changes = true;
            }
        }
        return changes;
    }

    public void check(FieldInfo fieldInfo) {
        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        // TODO check the correct field name in @Linked(to="xxxx")
        check(fieldInfo, Linked.class, typeContext.linked.get());
        check(fieldInfo, NotModified.class, typeContext.notModified.get());
        check(fieldInfo, NotNull.class, typeContext.notNull.get());
        check(fieldInfo, Final.class, typeContext.effectivelyFinal.get());

        // dynamic type annotations
        check(fieldInfo, E1Immutable.class, typeContext.e1Immutable.get());
        check(fieldInfo, E2Immutable.class, typeContext.e2Immutable.get());
        check(fieldInfo, Container.class, typeContext.container.get());
        check(fieldInfo, E1Container.class, typeContext.e1Container.get());
        check(fieldInfo, E2Container.class, typeContext.e2Container.get());

        if (fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                List<TypeInfo> allTypes = fieldInfo.owner.allTypesInPrimaryType();
                boolean readInMethods = allTypes.stream().flatMap(ti -> ti.typeInspection.get().constructorAndMethodStream())
                        .filter(m -> !(m.isConstructor && m.typeInfo == fieldInfo.owner)) // not my own constructors
                        .anyMatch(m -> m.methodAnalysis.fieldRead.getOtherwiseNull(fieldInfo) == Boolean.TRUE);
                if (!readInMethods) {
                    typeContext.addMessage(Message.Severity.ERROR, "Private field " + fieldInfo.fullyQualifiedName() +
                            " is not read outside constructors");
                }
            }
        } else if (fieldInfo.fieldAnalysis.annotations.getOtherwiseNull(typeContext.effectivelyFinal.get()) != Boolean.TRUE) {
            // error, unless we're in a record
            if (!fieldInfo.owner.isRecord()) {
                typeContext.addMessage(Message.Severity.ERROR, "Non-private field " + fieldInfo.fullyQualifiedName() +
                        " is not effectively final (@Final)");
            } // else: nested private types can have fields the way they like it
        }
        CheckConstant.checkConstantForFields(typeContext, fieldInfo);
    }

    private void check(FieldInfo fieldInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @" + annotation.getSimpleName()));
    }
}
