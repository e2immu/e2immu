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
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.*;

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

        // STEP 4: MULTIPLE ANNOTATIONS ON ASSIGNMENT: IMMUTABLE, CONTAINER (min over assignments)
        for (VariableProperty property : VariableProperty.FIELD_ANALYSER_MIN_OVER_ASSIGNMENTS) {
            if (analyseDynamicTypeAnnotation(property, fieldInfo, fieldAnalysis, value, haveInitialiser,
                    fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
                changes = true;
        }

        // STEP 5: NOT NULL
        if (analyseNotNull(fieldInfo, fieldAnalysis, value, haveInitialiser, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 6: @NotModified
        if (analyseNotModified(fieldInfo, fieldAnalysis, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 7: @Size
        if (analyseSize(fieldInfo, fieldAnalysis, value, haveInitialiser, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.FALSE &&
                analyseDynamicTypeAnnotation(VariableProperty.SIZE, fieldInfo, fieldAnalysis, value, haveInitialiser,
                        fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 8: @Linked, variablesLinkedToMe
        if (analyseLinked(fieldInfo, fieldAnalysis, typeInspection)) changes = true;

        // STEP 9: some ERRORS
        if (fieldErrors(fieldInfo, fieldAnalysis, fieldSummariesNotYetSet)) changes = true;
        return changes;
    }

    // TODO SIZE = min over assignments IF the field is not modified + not exposed or e2immu + max over restrictions + max of these two

    private boolean analyseSize(FieldInfo fieldInfo,
                                FieldAnalysis fieldAnalysis,
                                Value value,
                                boolean haveInitialiser,
                                boolean fieldCanBeWrittenFromOutsideThisType,
                                TypeInspection typeInspection,
                                boolean fieldSummariesNotYetSet) {
        int currentValue = fieldAnalysis.getProperty(VariableProperty.SIZE);
        if (currentValue != Level.DELAY) return false; // already decided
        if (!fieldInfo.type.hasSize()) {
            log(SIZE, "No @Size annotation on {}, because the type has no size!", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.FALSE); // in the case of size, FALSE there cannot be size
            return true;
        }
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return false;
        }
        if (isFinal == Level.FALSE && (!fieldInfo.isPrivate() || fieldCanBeWrittenFromOutsideThisType)) {
            log(SIZE, "Field @Size cannot be {}: it is not @Final, and either not private, "
                    + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.FALSE); // in the case of size, FALSE there cannot be size
            return true;
        }
        if (fieldSummariesNotYetSet) return false;

        // now for the more serious restrictions... if the type is @E2Immu, we can have a @Size restriction (actually, size is constant!)
        // if the field is @NotModified, and not exposed, then @Size is governed by the assignments and restrictions of the method.
        // but if the field is exposed somehow, or modified in the type, we must stick to @Size(min >= 0) (we have a size)
        int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @NotModified", fieldInfo.fullyQualifiedName());
            return false;
        }
        if (modified == Level.TRUE) {
            fieldAnalysis.setProperty(VariableProperty.SIZE, Analysis.IS_A_SIZE);
            log(SIZE, "Setting @Size on {} to @Size(min = 0), meaning 'we have a @Size, but nothing else'", fieldInfo.fullyQualifiedName());
            return true;
        }
        int immutable = Level.value(fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), Level.E2IMMUTABLE);
        if (immutable == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @E2Immutable", fieldInfo.fullyQualifiedName());
            return true;
        }
        if (immutable == Level.FALSE) {
            // TODO
        }
        if (someAssignmentValuesUndefined(VariableProperty.SIZE, fieldInfo, typeInspection)) return false;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved(typeInspection, fieldInfo);

        int valueFromAssignment = computeValueFromAssignment(typeInspection, fieldInfo, haveInitialiser, value, VariableProperty.SIZE, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        int valueFromContext = computeValueFromContext(typeInspection, fieldInfo, VariableProperty.SIZE, allDelaysResolved);
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        if (valueFromContext > valueFromAssignment) {
            log(SIZE, "Problematic: assignments have lower value than requirements for @Size");
            typeContext.addMessage(Message.newMessage(new Location(fieldInfo), Message.POTENTIAL_SIZE_PROBLEM));
        }
        int finalValue = Math.max(valueFromAssignment, valueFromContext);
        log(SIZE, "Set property @Size on field {} to value {}", fieldInfo.fullyQualifiedName(), finalValue);
        fieldAnalysis.setProperty(VariableProperty.SIZE, finalValue);
        return true;
    }


    private boolean analyseNotNull(FieldInfo fieldInfo,
                                   FieldAnalysis fieldAnalysis,
                                   Value value,
                                   boolean haveInitialiser,
                                   boolean fieldCanBeWrittenFromOutsideThisType,
                                   TypeInspection typeInspection,
                                   boolean fieldSummariesNotYetSet) {
        int currentValue = fieldAnalysis.getProperty(VariableProperty.NOT_NULL);
        if (currentValue != Level.DELAY) return false; // already decided
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return false;
        }
        if (isFinal == Level.FALSE && (!fieldInfo.isPrivate() || !haveInitialiser || fieldCanBeWrittenFromOutsideThisType)) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it is not @Final, and either not private, or has no initialiser, "
                    + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.NOT_NULL, Level.FALSE);
            return true;
        }
        if (fieldSummariesNotYetSet) return false;

        if (someAssignmentValuesUndefined(VariableProperty.NOT_NULL, fieldInfo, typeInspection)) return false;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved(typeInspection, fieldInfo);

        int valueFromAssignment = computeValueFromAssignment(typeInspection, fieldInfo, haveInitialiser, value, VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        int valueFromContext = computeValueFromContext(typeInspection, fieldInfo, VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        int finalValue = Math.max(valueFromAssignment, valueFromContext);
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fieldInfo.fullyQualifiedName(), finalValue);

        if (isFinal == Level.TRUE && finalValue >= Level.TRUE) {
            List<MethodInfo> methodsWhereFieldIsAssigned = methodsWhereFieldIsAssigned(fieldInfo);
            if (methodsWhereFieldIsAssigned.size() > 0 && !haveInitialiser) {
                // check that all methods have a precondition, and that the variable is linked to at least one of the parameters occurring in the precondition
                boolean linkedToVarsInPrecondition = methodsWhereFieldIsAssigned.stream().allMatch(mi ->
                        mi.methodAnalysis.isSet() && mi.methodAnalysis.get().precondition.isSet() &&
                                !Collections.disjoint(mi.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.get(),
                                        mi.methodAnalysis.get().precondition.get().variables()));
                if (linkedToVarsInPrecondition) {
                    // we now check if a not-null is compatible with the pre-condition
                    boolean allCompatible = methodsWhereFieldIsAssigned.stream().allMatch(methodInfo -> {
                        Value assignment = methodInfo.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.get();
                        Value fieldIsNotNull = NegatedValue.negate(EqualsValue.equals(NullValue.NULL_VALUE, assignment));
                        Value andValue = new AndValue().append(methodInfo.methodAnalysis.get().precondition.get(), fieldIsNotNull);
                        return andValue != BoolValue.FALSE;
                    });
                    if (allCompatible) {
                        log(NOT_NULL, "Not setting @NotNull on {}, already in precondition", fieldInfo.fullyQualifiedName());
                        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, Level.FALSE);
                        return true;
                    }
                } else {
                    log(NOT_NULL_DEBUG, "Not checking preconditions because not linked to parameters for {}", fieldInfo.fullyQualifiedName());
                }
            } else {
                log(NOT_NULL_DEBUG, "Only checking preconditions if my value is assigned in methods, not in initialiser; {}", fieldInfo.fullyQualifiedName());
            }
        } else {
            log(NOT_NULL_DEBUG, "Non-final, therefore not checking preconditions on methods for {}", fieldInfo.fullyQualifiedName());
        }
        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, finalValue);
        return true;
    }

    private static List<MethodInfo> methodsWhereFieldIsAssigned(FieldInfo fieldInfo) {
        return fieldInfo.owner.typeInspection.get().constructorAndMethodStream().filter(mi -> mi.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .filter(mi -> mi.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.ASSIGNED, Level.DELAY) >= Level.TRUE)
                .collect(Collectors.toList());
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
                        .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.READ, Level.FALSE))
                        .max().orElse(Level.FALSE);
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
                || fieldCanBeWrittenFromOutsideThisType)) {
            log(NOT_NULL, "Field {} cannot be {}: it is not @Final, and either not private, "
                    + " or it can be accessed from outside this class", fieldInfo.fullyQualifiedName(), property);
            fieldAnalysis.setProperty(property, Level.FALSE); // in the case of size, FALSE means >= 0
            return true;
        }
        if (fieldSummariesNotYetSet) return false;
        if (someAssignmentValuesUndefined(property, fieldInfo, typeInspection)) return false;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved(typeInspection, fieldInfo);

        // compute the value of the assignments
        int valueFromAssignment = computeValueFromAssignment(typeInspection, fieldInfo, haveInitialiser, value, property, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property {} on field {}, initialiser delayed", property, fieldInfo.fullyQualifiedName());
            return false; // delay
        }
        log(DYNAMIC, "Set property {} on field {} to value {}", property, fieldInfo.fullyQualifiedName(), valueFromAssignment);
        fieldAnalysis.setProperty(property, valueFromAssignment);
        return true;
    }

    private static boolean someAssignmentValuesUndefined(VariableProperty property, FieldInfo fieldInfo, TypeInspection typeInspection) {
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
            return true;
        }
        return false;
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
                if (!parameterInfo.parameterAnalysis.get().assignedToField.isSet()) {
                    parameterInfo.parameterAnalysis.get().assignedToField.set(fieldInfo);
                    log(CONSTANT, "Field {} has been assigned to parameter {}", fieldInfo.name, parameterInfo.detailedString());
                }
            } else {
                log(CONSTANT, "Field {} is assignment linked to another field? what would be the purpose?", fieldInfo.fullyQualifiedName());
            }
        }

        // we could have checked this at the start, but then we'd miss the potential assignment between parameter and field

        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE || fieldAnalysis.effectivelyFinalValue.isSet())
            return false;

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
        Value combinedValue;
        if (values.isEmpty()) {
            combinedValue = NullValue.NULL_VALUE;
        } else if (values.size() == 1) {
            Value value = values.get(0);
            if (value.isConstant()) return value;
            combinedValue = value;
        } else {
            combinedValue = CombinedValue.create(values);
        }
        return new FinalFieldValue(fieldReference);
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
        if (fieldAnalysis.getProperty(VariableProperty.MODIFIED) != Level.UNDEFINED) return false;
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
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) != Level.DELAY);

        if (allContentModificationsDefined) {
            boolean modified = fieldCanBeWrittenFromOutsideThisType ||
                    typeInspection.constructorAndMethodStream()
                            .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                            .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.READ, Level.DELAY) >= Level.TRUE)
                            .anyMatch(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).properties.getOtherwise(VariableProperty.MODIFIED, Level.DELAY) == Level.TRUE);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, modified);
            log(NOT_MODIFIED, "Mark field {} as {}", fieldInfo.fullyQualifiedName(), modified ? "@Modified" : "@NotModified");
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

        // opposites
        check(fieldInfo, org.e2immu.annotation.Variable.class, typeContext.variableField.get());
        check(fieldInfo, Modified.class, typeContext.modified.get());
        check(fieldInfo, Nullable.class, typeContext.nullable.get());

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
