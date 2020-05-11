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
import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

import static org.e2immu.analyser.model.value.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.model.value.UnknownValue.UNKNOWN_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final TypeContext typeContext;
    private static final VariableProperty[] DYNAMIC_PROPERTIES = {VariableProperty.IMMUTABLE, VariableProperty.CONTAINER};

    public FieldAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        boolean changes = false;
        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis;
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
        if (analyseFinal(fieldInfo, fieldAnalysis, value, fieldCanBeAccessedFromOutsideThisType, typeInspection))
            changes = true;

        // STEP 3: EFFECTIVELY FINAL VALUE, and @Constant
        if (analyseFinalValue(fieldInfo, fieldAnalysis, value, haveInitialiser, fieldReference, typeInspection, fieldProperties))
            changes = true;

        // STEP 4: @NotNull
        if (analyseNotNull(fieldInfo, fieldAnalysis, value, haveInitialiser, typeInspection, fieldCanBeAccessedFromOutsideThisType))
            changes = true;

        // STEP 5: Dynamic type annotations
        // dynamic type annotations come before @NotModified, because any E2Immutable type cannot be modified anyway.
        for (VariableProperty property : DYNAMIC_PROPERTIES) {
            if (analyseDynamicTypeAnnotation(property, fieldInfo, fieldAnalysis, value, haveInitialiser, fieldCanBeAccessedFromOutsideThisType, typeInspection))
                changes = true;
        }

        // STEP 6: @NotModified
        if (analyseNotModified(fieldInfo, fieldAnalysis, fieldReference, fieldCanBeAccessedFromOutsideThisType, typeInspection))
            changes = true;

        // STEP 7: @Linked, variablesLinkedToMe
        if (analyseLinked(fieldInfo, fieldAnalysis, fieldReference, typeInspection)) changes = true;

        return changes;
    }

    private boolean analyseDynamicTypeAnnotation(VariableProperty property,
                                                 FieldInfo fieldInfo,
                                                 FieldAnalysis fieldAnalysis,
                                                 Value value,
                                                 boolean haveInitialiser,
                                                 boolean fieldCanBeAccessedFromOutsideThisType,
                                                 TypeInspection typeInspection) {
        int currentValue = fieldAnalysis.getProperty(property);
        if (!property.canImprove && currentValue != Level.DELAY) return false;
        if (fieldCanBeAccessedFromOutsideThisType) {
            log(DYNAMIC, "Field {} cannot have dynamic type property {}, it can be written from outside this type",
                    fieldInfo.fullyQualifiedName(), property);
            fieldAnalysis.setProperty(property, Level.FALSE);
            return true;
        }

        boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                        (!m.methodAnalysis.fieldAssignments.get(fieldInfo) || m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)));
        if (!allAssignmentValuesDefined) {
            log(DELAYED, "Delaying dynamic type property {} on field {}, not all assignment values defined",
                    property, fieldInfo.fullyQualifiedName());
            return false;
        }

        IntStream assignments = typeInspection.constructorAndMethodStream()
                .filter(m -> m.methodAnalysis.fieldAssignments.get(fieldInfo) && m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo))
                .mapToInt(m -> m.methodAnalysis.fieldAssignmentValues.get(fieldInfo).getPropertyOutsideContext(property));
        IntStream initialiser = haveInitialiser ? IntStream.of(value.getPropertyOutsideContext(property)) : IntStream.empty();
        IntStream combined = IntStream.concat(assignments, initialiser);
        OptionalInt least = combined.min();

        int conclusion = least.orElse(Level.FALSE);
        if (conclusion == Level.DELAY) {
            log(DELAYED, "Delaying dynamic type property {} on field {}, initialiser delayed",
                    property, fieldInfo.fullyQualifiedName());
            return false;
        }
        if(conclusion <= currentValue) {
            return false; // not better
        }
        log(DYNAMIC, "Set dynamic type property {} on field {} to value {}", property, fieldInfo.fullyQualifiedName(),
                conclusion);
        fieldAnalysis.setProperty(property, conclusion);
        return true;
    }

    private boolean analyseFinalValue(FieldInfo fieldInfo,
                                      FieldAnalysis fieldAnalysis,
                                      Value value,
                                      boolean haveInitialiser,
                                      FieldReference fieldReference,
                                      TypeInspection typeInspection,
                                      EvaluationContext evaluationContext) {
        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE || fieldAnalysis.effectivelyFinalValue.isSet())
            return false;
        // find the constructors where the value is set; if they're all set to the same value,
        // we can set the initial value; also take into account the value of the initialiser, if it is there
        Value consistentValue = value;
        if (!(fieldInfo.isExplicitlyFinal() && haveInitialiser)) {
            for (MethodInfo method : typeInspection.methodsAndConstructors()) {
                if (method.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo) == Boolean.TRUE) {
                    if (method.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)) {
                        Value assignment = method.methodAnalysis.fieldAssignmentValues.get(fieldInfo);
                        if (consistentValue == NO_VALUE) consistentValue = assignment;
                        else if (!consistentValue.equals(assignment)) {
                            log(CONSTANT, "Cannot set consistent value for field {}, have {} and {}",
                                    fieldInfo.fullyQualifiedName(), consistentValue, assignment);
                            fieldInfo.fieldAnalysis.effectivelyFinalValue.set(UNKNOWN_VALUE);
                            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.FALSE);
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
        if (consistentValue == NO_VALUE) return false; // delay

        int isNotNull = consistentValue.isNotNull0OutsideContext();
        if (isNotNull == Level.DELAY) return false; // delay

        Value valueToSet;
        boolean isConstant = consistentValue instanceof Constant;
        fieldAnalysis.setProperty(VariableProperty.CONSTANT, isConstant);

        if (isConstant) {
            valueToSet = consistentValue;
            // directly adding the annotation; it will not be used for inspection
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
        } else {
            valueToSet = evaluationContext.newVariableValue(fieldReference);
            log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
            fieldAnalysis.annotations.put(typeContext.constant.get(), false);
        }
        fieldInfo.fieldAnalysis.effectivelyFinalValue.set(valueToSet);
        log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                fieldInfo.fullyQualifiedName(), consistentValue);
        return true;
    }

    private boolean analyseLinked(FieldInfo fieldInfo,
                                  FieldAnalysis fieldAnalysis,
                                  FieldReference fieldReference,
                                  TypeInspection typeInspection) {
        if (fieldAnalysis.variablesLinkedToMe.isSet()) return false;

        boolean allDefined = typeInspection.constructorAndMethodStream()
                .allMatch(m -> m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                        (!m.methodAnalysis.fieldAssignments.get(fieldInfo) ||
                                m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference)));
        if (!allDefined) return false;

        Set<Variable> links = new HashSet<>();
        typeInspection.constructorAndMethodStream().forEach(m -> {
            if (m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference))
                links.addAll(m.methodAnalysis.fieldsLinkedToFieldsAndVariables.get(fieldReference));
        });
        fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
        log(LINKED_VARIABLES, "Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));

        // explicitly adding the annotation here; it will not be inspected.
        AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(typeContext, links);
        fieldAnalysis.annotations.put(linkAnnotation, !links.isEmpty());
        return true;
    }

    private boolean analyseFinal(FieldInfo fieldInfo,
                                 FieldAnalysis fieldAnalysis,
                                 Value value,
                                 boolean fieldCanBeAccessedFromOutsideThisType,
                                 TypeInspection typeInspection) {
        if (Level.UNDEFINED != fieldAnalysis.getProperty(VariableProperty.FINAL)) return false;
        boolean isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
        if (isExplicitlyFinal) {
            fieldAnalysis.setProperty(VariableProperty.FINAL, Level.TRUE);
            log(FINAL, "Mark field {} as effectively final, because explicitly so, value {}",
                    fieldInfo.fullyQualifiedName(), value);
            return true;
        }
        Boolean isModifiedOutsideConstructors = typeInspection.methods.stream()
                .filter(m -> !m.isPrivate() || m.isCalledFromNonPrivateMethod())
                .map(m -> m.methodAnalysis.fieldAssignments.getOtherwiseNull(fieldInfo))
                .reduce(false, TypeAnalyser.TERNARY_OR);

        if (isModifiedOutsideConstructors == null) {
            log(DELAYED, "Cannot yet conclude if {} is effectively final", fieldInfo.fullyQualifiedName());
            return false;
        }
        boolean isFinal;
        if (fieldCanBeAccessedFromOutsideThisType) {
            // this means other types can write to the field... not final by definition
            isFinal = false;
        } else {
            isFinal = !isModifiedOutsideConstructors;
        }
        fieldAnalysis.setProperty(VariableProperty.FINAL, isFinal);
        if (isFinal && fieldInfo.type.isRecordType()) {
            typeContext.addMessage(Message.Severity.ERROR,
                    "Effectively final field " + fieldInfo.fullyQualifiedName() +
                            " is not allowed to be of a record type " + fieldInfo.type.detailedString());
        }
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final, not modified outside constructors", fieldInfo.fullyQualifiedName());
        return true;
    }

    private boolean analyseNotModified(FieldInfo fieldInfo,
                                       FieldAnalysis fieldAnalysis,
                                       FieldReference fieldReference,
                                       boolean fieldCanBeAccessedFromOutsideThisType,
                                       TypeInspection typeInspection) {
        if (fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED) != Level.UNDEFINED) return false;
        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int e2immutable = Level.value(immutable, Level.E2IMMUTABLE);
        if (e2immutable == Level.DELAY) {
            log(DELAYED, "Delaying @NotModified, no idea about @E2Immutable");
            return false;
        }
        if (e2immutable == Level.TRUE) {
            fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED, Level.FALSE);
            log(NOT_MODIFIED, "Field {} does not need @NotModified, as it is @E2Immutable", fieldInfo.fullyQualifiedName());
            return true;
        }
        boolean allContentModificationsDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                m.methodAnalysis.fieldRead.isSet(fieldInfo) &&
                        (!m.methodAnalysis.fieldRead.get(fieldInfo) || m.methodAnalysis.contentModifications.isSet(fieldReference)));
        if (allContentModificationsDefined) {
            boolean notModified =
                    !fieldCanBeAccessedFromOutsideThisType &&
                            typeInspection.constructorAndMethodStream()
                                    .filter(m -> m.methodAnalysis.fieldRead.get(fieldInfo))
                                    .noneMatch(m -> m.methodAnalysis.contentModifications.get(fieldReference));
            fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED, notModified);
            log(NOT_MODIFIED, "Mark field {} as " + (notModified ? "" : "not ") +
                    "@NotModified", fieldInfo.fullyQualifiedName());
            return true;
        }
        log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                fieldInfo.fullyQualifiedName());
        return false;

    }

    private boolean analyseNotNull(FieldInfo fieldInfo,
                                   FieldAnalysis fieldAnalysis,
                                   Value value,
                                   boolean haveInitialiser,
                                   TypeInspection typeInspection,
                                   boolean fieldCanBeAccessedFromOutsideThisType) {
        if (fieldAnalysis.getProperty(VariableProperty.NOT_NULL) != Level.UNDEFINED) return false;
        int isNotNullValue;
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            isNotNullValue = Level.DELAY;
        } else {
            int finalCriteria;
            if (isFinal == Level.FALSE) {
                if (!fieldInfo.isPrivate() || !haveInitialiser || fieldCanBeAccessedFromOutsideThisType) {
                    log(NOT_NULL, "Field {} cannot be @NotNull: it is not @Final, and either not private, or has no initialiser,"
                            + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName());
                    finalCriteria = Level.FALSE;
                } else {
                    finalCriteria = value.isNotNull0OutsideContext();
                }
            } else {
                finalCriteria = Level.TRUE;
            }
            if (finalCriteria == Level.DELAY) {
                log(DELAYED, "Delaying @NotNull on {} until we know @NotNull of initialiser", fieldInfo.fullyQualifiedName());
                isNotNullValue = Level.DELAY;
            } else if (finalCriteria == Level.TRUE) {
                // to avoid chicken and egg problems we do not look at effectivelyFinalValue, because that one replaces
                // the real value with a generic VariableValue, relying on @NotNull
                boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                        m.methodAnalysis.fieldAssignments.isSet(fieldInfo) &&
                                (!m.methodAnalysis.fieldAssignments.get(fieldInfo) || m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo)));
                if (allAssignmentValuesDefined) {
                    int allAssignmentValuesNotNull = typeInspection.constructorAndMethodStream()
                            .filter(m -> m.methodAnalysis.fieldAssignments.get(fieldInfo) && m.methodAnalysis.fieldAssignmentValues.isSet(fieldInfo))
                            .mapToInt(m -> m.methodAnalysis.fieldAssignmentValues.get(fieldInfo).isNotNull0OutsideContext())
                            .reduce(0, Level.AND);
                    if (allAssignmentValuesNotNull == Level.DELAY) {
                        isNotNullValue = Level.DELAY; // delay
                    } else {
                        if (!haveInitialiser) {
                            isNotNullValue = allAssignmentValuesNotNull;
                        } else {
                            if (value == NO_VALUE) {
                                isNotNullValue = Level.DELAY; // delay
                            } else {
                                int initialiserIsNotNull = value.isNotNull0OutsideContext();
                                if (initialiserIsNotNull == Level.DELAY) {
                                    isNotNullValue = Level.DELAY; // delay
                                } else {
                                    // this is the real one!
                                    isNotNullValue = Level.AND.applyAsInt(initialiserIsNotNull, allAssignmentValuesNotNull);
                                }
                            }
                        }
                    }
                } else {
                    isNotNullValue = Level.DELAY; // delay
                }
            } else {
                isNotNullValue = Level.DELAY;
            }
        }
        if (isNotNullValue == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on field {}", fieldInfo.fullyQualifiedName());
        } else {
            fieldAnalysis.setProperty(VariableProperty.NOT_NULL, isNotNullValue);
            log(NOT_NULL, "Mark field {} as " + (isNotNullValue == Level.TRUE ? "" : "NOT ") + "@NotNull",
                    fieldInfo.fullyQualifiedName(), isNotNullValue);
            return true;
        }
        return false;
    }


    public void check(FieldInfo fieldInfo) {
        // before we check, we copy the properties into annotations
        fieldInfo.fieldAnalysis.transferPropertiesToAnnotations(typeContext);

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
