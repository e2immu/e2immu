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
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final TypeContext typeContext;

    public FieldAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        boolean changes = false;
        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
        boolean fieldCanBeWrittenFromOutsideThisType = fieldInfo.owner.isRecord();

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
                value = fieldInitialiser.initialiser.evaluate(localVariableProperties, EvaluationVisitor.NO_VISITOR, ForwardEvaluationInfo.DEFAULT);
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
        boolean fieldSummariesNotYetSet = fieldProperties.iteration == 0;

        // STEP 2: EFFECTIVELY FINAL: @E1Immutable
        if (analyseFinal(fieldInfo, fieldAnalysis, value, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 3: EFFECTIVELY FINAL VALUE, and @Constant
        if (analyseFinalValue(fieldInfo, fieldAnalysis, fieldReference, fieldProperties, value, haveInitialiser, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 4: MULTIPLE ANNOTATIONS ON ASSIGNMENT: SIZE, NOT_NULL, IMMUTABLE, CONTAINER (min over assignments)
        for (VariableProperty property : VariableProperty.FIELD_ANALYSER_MIN_OVER_ASSIGNMENTS) {
            if (analyseDynamicTypeAnnotation(property, fieldInfo, fieldAnalysis, value, haveInitialiser, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
                changes = true;
        }

        // STEP 5: @NotModified
        if (analyseNotModified(fieldInfo, fieldAnalysis, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 6: @Linked, variablesLinkedToMe
        if (analyseLinked(fieldInfo, fieldAnalysis, typeInspection)) changes = true;

        if (fieldErrors(fieldInfo, fieldAnalysis, fieldSummariesNotYetSet)) changes = true;
        return changes;
    }

    private boolean fieldErrors(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis, boolean fieldSummariesNotYetSet) {
        if (fieldAnalysis.fieldError.isSet()) return false;

        if (fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                if (fieldSummariesNotYetSet) return false;
                List<TypeInfo> allTypes = fieldInfo.owner.allTypesInPrimaryType();
                int readInMethods = allTypes.stream().flatMap(ti -> ti.typeInspection.get().constructorAndMethodStream())
                        .filter(m -> !(m.isConstructor && m.typeInfo == fieldInfo.owner)) // not my own constructors
                        .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo)) // field seen
                        .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.READ, Level.DELAY))
                        .max().orElse(Level.DELAY);
                if (readInMethods == Level.DELAY) {
                    log(DELAYED, "Not yet ready to decide on read outside constructors");
                    return false;
                }
                boolean notRead = readInMethods == Level.FALSE;
                fieldAnalysis.fieldError.set(notRead);
                if (notRead) {
                    typeContext.addMessage(Message.newMessage(new Location(fieldInfo), Message.PRIVATE_FIELD_NOT_READ));
                }
                return true;
            }
        } else if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE) {
            // only react once we're certain the variable is not effectively final
            // error, unless we're in a record
            boolean record = fieldInfo.owner.isRecord();
            fieldAnalysis.fieldError.set(!record);
            if (!record) {
                typeContext.addMessage(Message.newMessage(new Location(fieldInfo), Message.NON_PRIVATE_FIELD_NOT_FINAL));
            } // else: nested private types can have fields the way they like it
            return true;
        }
        return false;
    }

    private boolean analyseDynamicTypeAnnotation(VariableProperty property,
                                                 FieldInfo fieldInfo,
                                                 FieldAnalysis fieldAnalysis,
                                                 Value value,
                                                 boolean haveInitialiser,
                                                 boolean fieldCanBeWrittenFromOutsideThisType,
                                                 TypeInspection typeInspection,
                                                 boolean fieldSummariesNotYetSet) {
        int currentValue = fieldAnalysis.getProperty(property);
        if (currentValue != Level.DELAY) return false; // already decided
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying {} on {} until we know about @Final", property, fieldInfo.fullyQualifiedName());
            return false;
        }
        if (isFinal == Level.FALSE && (!fieldInfo.isPrivate()
                || !haveInitialiser && property == VariableProperty.NOT_NULL
                || fieldCanBeWrittenFromOutsideThisType)) {
            log(NOT_NULL, "Field {} cannot be {}: it is not @Final, and either not private, or has no initialiser in the case of @NotNull,"
                    + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName(), property);
            fieldAnalysis.setProperty(property, Level.FALSE); // in the case of size, FALSE means >= 0
            return true;
        }
        if (fieldSummariesNotYetSet) return false;

        boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                // field is not present in the method
                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                        // field is not assigned to in the method
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.ASSIGNED, Level.DELAY) < Level.TRUE ||
                        // if it is present, assigned to, it needs to have a value
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.isSet());

        if (!allAssignmentValuesDefined) {
            log(DELAYED, "Delaying property {} on field {}, not all assignment values defined",
                    property, fieldInfo.fullyQualifiedName());
            return false;
        }

        boolean allDelaysResolved = delaysOnFieldSummariesResolved(typeInspection, fieldInfo);

        // compute the value of the assignments
        int valueFromAssignment = computeValueFromAssignment(typeInspection, fieldInfo, haveInitialiser, value, property, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property {} on field {}, initialiser delayed", property, fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        // for NOT_NULL and SIZE we also need to look at fieldSummaries properties if there is NO assignment
        int valueFromContext;
        if (VariableProperty.CONTEXT_PROPERTIES_FROM_STMT_TO_METHOD.contains(property)) {
            valueFromContext = computeValueFromContext(typeInspection, fieldInfo, property, allDelaysResolved);
            if (valueFromContext == Level.DELAY) {
                log(DELAYED, "Delaying property {} on {}, context property delay", property, fieldInfo.fullyQualifiedName());
                return false; // delay
            }
        } else valueFromContext = Level.DELAY;

        int finalValue = Math.max(valueFromAssignment, valueFromContext);
        log(DYNAMIC, "Set property {} on field {} to value {}", property, fieldInfo.fullyQualifiedName(), finalValue);
        fieldAnalysis.setProperty(property, finalValue);
        return true;
    }

    private boolean delaysOnFieldSummariesResolved(TypeInspection typeInspection, FieldInfo fieldInfo) {
        return typeInspection.constructorAndMethodStream().filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .noneMatch(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.METHOD_DELAY_RESOLVED, Level.DELAY)
                        == Level.TRUE);// TRUE indicates that there are delays
    }

    private int computeValueFromContext(TypeInspection typeInspection, FieldInfo fieldInfo, VariableProperty property, boolean allDelaysResolved) {
        IntStream contextRestrictions = typeInspection.constructorAndMethodStream()
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(property, Level.DELAY));
        int result = contextRestrictions.max().orElse(Level.DELAY);
        if (result == Level.DELAY && allDelaysResolved) return Level.FALSE;
        return result;
    }

    private int computeValueFromAssignment(TypeInspection typeInspection, FieldInfo fieldInfo, boolean haveInitialiser, Value value,
                                           VariableProperty property, boolean allDelaysResolved) {
        IntStream assignments = typeInspection.constructorAndMethodStream()
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.isSet())
                .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.get().getPropertyOutsideContext(property));
        IntStream initialiser = haveInitialiser ? IntStream.of(value.getPropertyOutsideContext(property)) : IntStream.empty();
        IntStream combined = IntStream.concat(assignments, initialiser);
        int result = property == VariableProperty.SIZE ? MethodAnalyser.safeMinimum(typeContext, new Location(fieldInfo), combined) : combined.min().orElse(Level.FALSE);
        if (result == Level.DELAY && allDelaysResolved) return Level.FALSE;
        return result;
    }

    private boolean analyseFinalValue(FieldInfo fieldInfo,
                                      FieldAnalysis fieldAnalysis,
                                      FieldReference fieldReference,
                                      VariableProperties fieldProperties,
                                      Value value,
                                      boolean haveInitialiser,
                                      TypeInspection typeInspection,
                                      boolean fieldSummariesNotYetSet) {
        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE || fieldAnalysis.effectivelyFinalValue.isSet())
            return false;

        List<Value> values = new LinkedList<>();
        if (haveInitialiser) {
            if (value == NO_VALUE) {
                log(DELAYED, "Delaying consistent value for field " + fieldInfo.fullyQualifiedName());
                return false;
            }
            values.add(value);
        }
        if (!(fieldInfo.isExplicitlyFinal() && haveInitialiser)) {
            if (fieldSummariesNotYetSet) return false;
            for (MethodInfo method : typeInspection.methodsAndConstructors()) {
                MethodAnalysis methodAnalysis = method.methodAnalysis.get();
                if (methodAnalysis.fieldSummaries.isSet(fieldInfo)) {
                    TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);
                    if (tv.properties.get(VariableProperty.ASSIGNED) >= Level.TRUE) {
                        if (tv.value.isSet()) {
                            values.add(tv.value.get());
                        } else {
                            log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                            return false;
                        }
                    }
                }
            }
        }

        // field linked to parameter

        if (values.size() == 1 && values.get(0) instanceof VariableValue) {
            VariableValue variableValue = (VariableValue) values.get(0);
            if (variableValue.variable instanceof ParameterInfo) {
                ParameterInfo parameterInfo = (ParameterInfo) variableValue.variable;
                parameterInfo.parameterAnalysis.get().assignedToField.set(fieldInfo);
                log(CONSTANT, "Field {} has been assigned to parameter {}", fieldInfo.name, parameterInfo.detailedString());
            } else {
                log(CONSTANT, "Field {} is assignment linked to another field? what would be the purpose?", fieldInfo.fullyQualifiedName());
            }
        }

        // compute and set the combined value

        Value effectivelyFinalValue = determineEffectivelyFinalValue(fieldReference, values);
        fieldAnalysis.effectivelyFinalValue.set(effectivelyFinalValue);
        fieldAnalysis.setProperty(VariableProperty.CONSTANT, effectivelyFinalValue.isConstant());

        // check constant

        if (effectivelyFinalValue.isConstant()) {
            // directly adding the annotation; it will not be used for inspection
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(typeContext, value);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
        } else {
            log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
            fieldAnalysis.annotations.put(typeContext.constant.get(), false);
        }

        log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                fieldInfo.fullyQualifiedName(), effectivelyFinalValue);
        return true;
    }

    private Value determineEffectivelyFinalValue(FieldReference fieldReference, List<Value> values) {
        // List<Value> transformed = values.stream()
        //        .map(v -> v instanceof VariableValue && ((VariableValue) v).variable instanceof ParameterInfo ?
        //                 new ParameterValue((ParameterInfo) ((VariableValue) v).variable) : v)
        //         .collect(Collectors.toList());
        if (values.size() == 1) {
            Value value = values.get(0);
            if (value.isConstant()) return value;
        }
        Value combinedValue = CombinedValue.create(values);
        return new FinalFieldValue(fieldReference, combinedValue);
    }

    private boolean analyseLinked(FieldInfo fieldInfo,
                                  FieldAnalysis fieldAnalysis,
                                  TypeInspection typeInspection) {
        if (fieldAnalysis.variablesLinkedToMe.isSet()) return false;

        boolean allDefined = typeInspection.constructorAndMethodStream()
                .allMatch(m ->
                        m.methodAnalysis.get().variablesLinkedToFieldsAndParameters.isSet() && (
                                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.isSet()));
        if (!allDefined) return false;

        Set<Variable> links = new HashSet<>();
        typeInspection.constructorAndMethodStream()
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.isSet())
                .forEach(m -> links.addAll(m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.get()));
        fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
        log(LINKED_VARIABLES, "FA: Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));

        // explicitly adding the annotation here; it will not be inspected.
        AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(typeContext, links);
        fieldAnalysis.annotations.put(linkAnnotation, !links.isEmpty());
        return true;
    }

    private boolean analyseFinal(FieldInfo fieldInfo,
                                 FieldAnalysis fieldAnalysis,
                                 Value value,
                                 boolean fieldCanBeWrittenFromOutsideThisType,
                                 TypeInspection typeInspection,
                                 boolean fieldSummariesNotYetPresent) {
        if (Level.UNDEFINED != fieldAnalysis.getProperty(VariableProperty.FINAL)) return false;
        boolean isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
        if (isExplicitlyFinal) {
            fieldAnalysis.setProperty(VariableProperty.FINAL, Level.TRUE);
            log(FINAL, "Mark field {} as effectively final, because explicitly so, value {}",
                    fieldInfo.fullyQualifiedName(), value);
            return true;
        }
        if (fieldSummariesNotYetPresent) return false;
        int isAssignedOutsideConstructors = typeInspection.methods.stream()
                .filter(m -> !m.isPrivate() || m.isCalledFromNonPrivateMethod())
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.ASSIGNED, Level.DELAY))
                .max().orElse(Level.DELAY);
        boolean isFinal;
        if (fieldCanBeWrittenFromOutsideThisType) {
            // this means other types can write to the field... not final by definition
            isFinal = false;
        } else {
            isFinal = isAssignedOutsideConstructors < Level.TRUE;
        }
        fieldAnalysis.setProperty(VariableProperty.FINAL, isFinal);
        if (isFinal && fieldInfo.type.isRecordType()) {
            typeContext.addMessage(Message.newMessage(new Location(fieldInfo), Message.EFFECTIVELY_FINAL_FIELD_NOT_RECORD));
        }
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final", fieldInfo.fullyQualifiedName());
        return true;
    }

    private boolean analyseNotModified(FieldInfo fieldInfo,
                                       FieldAnalysis fieldAnalysis,
                                       boolean fieldCanBeWrittenFromOutsideThisType,
                                       TypeInspection typeInspection,
                                       boolean fieldSummariesNotYetSet) {
        if (fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED) != Level.UNDEFINED) return false;
        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int e2immutable = Level.value(immutable, Level.E2IMMUTABLE);
        if (e2immutable == Level.DELAY) {
            log(DELAYED, "Delaying @NotModified, no idea about dynamic type @E2Immutable");
            return false;
        }
        if (fieldSummariesNotYetSet) return false;
        // no need to check e2immutable == TRUE, because that happened in the first statement (getProperty)
        boolean allContentModificationsDefined = typeInspection.constructorAndMethodStream().allMatch(m ->
                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.READ, Level.DELAY) < Level.TRUE ||
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.NOT_MODIFIED, Level.DELAY) != Level.DELAY);

        if (allContentModificationsDefined) {
            boolean notModified =
                    !fieldCanBeWrittenFromOutsideThisType &&
                            typeInspection.constructorAndMethodStream()
                                    .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                                    .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.READ, Level.DELAY) >= Level.TRUE)
                                    .noneMatch(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.NOT_MODIFIED, Level.DELAY) == Level.FALSE);
            fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED, notModified);
            log(NOT_MODIFIED, "Mark field {} as " + (notModified ? "" : "not ") +
                    "@NotModified", fieldInfo.fullyQualifiedName());
            return true;
        }
        log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                fieldInfo.fullyQualifiedName());
        return false;
    }

    public void check(FieldInfo fieldInfo) {
        // before we check, we copy the properties into annotations
        fieldInfo.fieldAnalysis.get().transferPropertiesToAnnotations(typeContext);

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

        CheckConstant.checkConstantForFields(typeContext, fieldInfo);
        CheckSize.checkSizeForFields(typeContext, fieldInfo);
    }

    private void check(FieldInfo fieldInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(fieldInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            typeContext.addMessage(error);
        });
    }
}
