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
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.Logger;
import org.e2immu.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
    private final Messages messages = new Messages();

    public FieldAnalyser(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        boolean changes = false;
        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
        boolean fieldCanBeWrittenFromOutsideThisType = fieldInfo.owner.isRecord() || !fieldInfo.isPrivate() && !fieldInfo.isExplicitlyFinal();

        // STEP 0: support data: does this field have to satisfy rules 2 and 3 of level 2 immutability?

        if (computeImplicitlyImmutableDataType(fieldInfo, fieldAnalysis)) changes = true;

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
        boolean isFunctionalInterface = fieldInfo.type.isFunctionalInterface();

        if (makeInternalObjectFlowsPermanent(fieldInfo, fieldAnalysis, fieldProperties)) changes = true;

        // STEP 2: EFFECTIVELY FINAL: @E1Immutable
        if (analyseFinal(fieldInfo, fieldAnalysis, value, fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 3: EFFECTIVELY FINAL VALUE, and @Constant
        if (analyseFinalValue(fieldInfo, fieldAnalysis, fieldReference, fieldProperties, value, haveInitialiser, typeInspection, fieldSummariesNotYetSet))
            changes = true;

        // STEP 4: IMMUTABLE (min over assignments)
        if (!isFunctionalInterface &&
                analyseDynamicTypeAnnotation(VariableProperty.IMMUTABLE, fieldInfo, fieldAnalysis, value, haveInitialiser,
                        fieldCanBeWrittenFromOutsideThisType, typeInspection, fieldSummariesNotYetSet))
            changes = true;

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


        // STEP 8: @NotModified1 for functional interfaces
        if (isFunctionalInterface && analyseNotModified1(fieldInfo, fieldAnalysis)) {
            changes = true;
        }

        // STEP 9: @Linked, variablesLinkedToMe
        if (analyseLinked(fieldInfo, fieldAnalysis, typeInspection)) changes = true;

        // STEP 10: some ERRORS
        if (fieldErrors(fieldInfo, fieldAnalysis, fieldSummariesNotYetSet)) changes = true;
        return changes;
    }

    private boolean computeImplicitlyImmutableDataType(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        if (fieldAnalysis.isOfImplicitlyImmutableDataType.isSet()) return false;
        TypeAnalysis typeAnalysis = fieldInfo.owner.typeAnalysis.get();
        if (!typeAnalysis.implicitlyImmutableDataTypes.isSet()) return false;
        boolean implicit = typeAnalysis.implicitlyImmutableDataTypes.get().contains(fieldInfo.type);
        fieldAnalysis.isOfImplicitlyImmutableDataType.set(implicit);
        return true;
    }

    private boolean makeInternalObjectFlowsPermanent(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis, VariableProperties fieldProperties) {
        if (fieldAnalysis.internalObjectFlows.isSet()) return false; // already done
        boolean noDelays = fieldProperties.getInternalObjectFlows().noneMatch(ObjectFlow::isDelayed);
        if (noDelays) {
            Set<ObjectFlow> internalObjectFlows = ImmutableSet.copyOf(fieldProperties.getInternalObjectFlows().collect(Collectors.toSet()));
            internalObjectFlows.forEach(of -> of.finalize(null));
            fieldAnalysis.internalObjectFlows.set(internalObjectFlows);
            log(OBJECT_FLOW, "Set {} internal object flows on {}", internalObjectFlows.size(), fieldInfo.fullyQualifiedName());
            return true;
        }
        log(DELAYED, "Not yet setting internal object flows on {}, delaying", fieldInfo.fullyQualifiedName());
        return false;
    }

    private boolean analyseNotModified1(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        if (fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED_1) != Level.UNDEFINED) return false;
        FieldInspection.FieldInitialiser initialiser = fieldInfo.fieldInspection.get().initialiser.get();
        if (initialiser.implementationOfSingleAbstractMethod == null) return false;
        MethodInfo sam = initialiser.implementationOfSingleAbstractMethod;
        boolean someParameterModificationUnknown = sam.methodInspection.get().parameters.stream().anyMatch(p ->
                p.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someParameterModificationUnknown) {
            log(NOT_MODIFIED, "Delaying @NotModified1 on {}, some parameters have no @Modified status", fieldInfo.fullyQualifiedName());
        }
        boolean allParametersNotModified = sam.methodInspection.get().parameters.stream().allMatch(p ->
                p.parameterAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.FALSE);
        log(NOT_MODIFIED, "Set @NotModified1 on {} to {}", fieldInfo.fullyQualifiedName(), allParametersNotModified);
        fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, allParametersNotModified);
        return true;
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
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisType) {
            log(SIZE, "Field {} cannot have @Size: it is not @Final, and it can be assigned to from outside this class", fieldInfo.fullyQualifiedName());
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
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.IS_A_SIZE);
            log(SIZE, "Setting @Size on {} to @Size(min = 0), meaning 'we have a @Size, but nothing else'", fieldInfo.fullyQualifiedName());
            return true;
        }
        int e2Immutable = MultiLevel.value(fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @E2Immutable", fieldInfo.fullyQualifiedName());
            return true;
        }
        if (e2Immutable == MultiLevel.FALSE) {
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
            messages.add(Message.newMessage(new Location(fieldInfo), Message.POTENTIAL_SIZE_PROBLEM));
        }
        int finalValue = Level.best(valueFromAssignment, valueFromContext);
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
        int nn = fieldAnalysis.getProperty(VariableProperty.NOT_NULL);
        if (nn > MultiLevel.DELAY) return false;
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return false;
        }
        if (isFinal == Level.FALSE && (!haveInitialiser || fieldCanBeWrittenFromOutsideThisType)) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it is not @Final, or has no initialiser, "
                    + " or it can be assigned to from outside this class", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
            return true;
        }
        if (fieldSummariesNotYetSet) return false;

        if (someAssignmentValuesUndefined(VariableProperty.NOT_NULL, fieldInfo, typeInspection)) return false;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved(typeInspection, fieldInfo);

        int valueFromAssignment = computeValueFromAssignment(typeInspection, fieldInfo, haveInitialiser, value,
                VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        int valueFromContext = computeValueFromContext(typeInspection, fieldInfo, VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return false; // delay
        }

        int finalNotNullValue = MultiLevel.bestNotNull(valueFromAssignment, valueFromContext);
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fieldInfo.fullyQualifiedName(), finalNotNullValue);

        if (isFinal == Level.TRUE && MultiLevel.value(finalNotNullValue, MultiLevel.NOT_NULL) == MultiLevel.EFFECTIVE) {
            List<MethodInfo> methodsWhereFieldIsAssigned = methodsWhereFieldIsAssigned(fieldInfo);
            if (methodsWhereFieldIsAssigned.size() > 0 && !haveInitialiser) {

                boolean linkingAndPreconditionsComputed = methodsWhereFieldIsAssigned.stream()
                        .map(m -> m.methodAnalysis.get())
                        .allMatch(m -> m.variablesLinkedToFieldsAndParameters.isSet() && m.precondition.isSet());
                if (!linkingAndPreconditionsComputed) {
                    log(DELAYED, "Delaying property @NotNull on {}, waiting for linking and preconditions", fieldInfo.fullyQualifiedName());
                    return false;
                }

                // check that all methods have a precondition, and that the variable is linked to at least one of the parameters occurring in the precondition
                boolean linkedToVarsInPrecondition = methodsWhereFieldIsAssigned.stream().allMatch(mi ->
                        mi.methodAnalysis.isSet() && mi.methodAnalysis.get().precondition.isSet() &&
                                !Collections.disjoint(safeLinkedVariables(mi.methodAnalysis.get().fieldSummaries.get(fieldInfo)),
                                        mi.methodAnalysis.get().precondition.get().variables()));
                if (linkedToVarsInPrecondition) {
                    // we now check if a not-null is compatible with the pre-condition
                    boolean allCompatible = methodsWhereFieldIsAssigned.stream().allMatch(methodInfo -> {
                        Value assignment = methodInfo.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.get();
                        Value fieldIsNotNull = NegatedValue.negate(EqualsValue.equals(NullValue.NULL_VALUE, assignment, ObjectFlow.NO_FLOW));
                        Value andValue = new AndValue(ObjectFlow.NO_FLOW).append(methodInfo.methodAnalysis.get().precondition.get(), fieldIsNotNull);
                        return andValue != BoolValue.FALSE;
                    });
                    if (allCompatible) {
                        log(NOT_NULL, "Setting @Nullable on {}, already in precondition", fieldInfo.fullyQualifiedName());
                        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
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
        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, finalNotNullValue);
        return true;
    }

    private static Set<Variable> safeLinkedVariables(TransferValue transferValue) {
        return transferValue.linkedVariables.isSet() ? transferValue.linkedVariables.get() : Set.of();
    }

    private static List<MethodInfo> methodsWhereFieldIsAssigned(FieldInfo fieldInfo) {
        return fieldInfo.owner.typeInspection.get().constructorAndMethodStream(TypeInspection.Methods.ALL)
                .filter(mi -> mi.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .filter(mi -> mi.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.ASSIGNED) >= Level.TRUE)
                .collect(Collectors.toList());
    }

    private boolean fieldErrors(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis, boolean fieldSummariesNotYetSet) {
        if (fieldAnalysis.fieldError.isSet()) return false;

        if (fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                if (fieldSummariesNotYetSet) return false;
                List<TypeInfo> allTypes = fieldInfo.owner.allTypesInPrimaryType();
                int readInMethods = allTypes.stream().flatMap(ti -> ti.typeInspection.get().constructorAndMethodStream(TypeInspection.Methods.ALL))
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
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.PRIVATE_FIELD_NOT_READ));
                }
                return true;
            }
        } else if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE) {
            // only react once we're certain the variable is not effectively final
            // error, unless we're in a record
            boolean record = fieldInfo.owner.isRecord();
            fieldAnalysis.fieldError.set(!record);
            if (!record) {
                messages.add(Message.newMessage(new Location(fieldInfo), Message.NON_PRIVATE_FIELD_NOT_FINAL));
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
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisType) {
            log(NOT_NULL, "Field {} cannot be {}: it is not @Final, and it can be assigned to from outside this class",
                    fieldInfo.fullyQualifiedName(), property);
            fieldAnalysis.setProperty(property, property.falseValue); // in the case of size, FALSE means >= 0
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
        boolean allAssignmentValuesDefined = typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL).allMatch(m ->
                // field is not present in the method
                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                        // field is not assigned to in the method
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.ASSIGNED) < Level.TRUE ||
                        // if it is present, assigned to, it needs to have a value
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).value.isSet());

        if (!allAssignmentValuesDefined) {
            log(DELAYED, "Delaying property {} on field {}, not all assignment values defined",
                    property, fieldInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private static boolean delaysOnFieldSummariesResolved(TypeInspection typeInspection, FieldInfo fieldInfo) {
        return typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL).filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .map(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo))
                .noneMatch(fs -> fs.getProperty(VariableProperty.METHOD_DELAY_RESOLVED) == Level.FALSE);
        // FALSE indicates that there are delays, TRUE that they have been resolved, DELAY that we're not aware
    }

    private static int computeValueFromContext(TypeInspection typeInspection, FieldInfo fieldInfo, VariableProperty property, boolean allDelaysResolved) {
        IntStream contextRestrictions = typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL)
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(property));
        int result = contextRestrictions.max().orElse(Level.DELAY);
        if (result == Level.DELAY && allDelaysResolved) return property.falseValue;
        return result;
    }

    private int computeValueFromAssignment(TypeInspection typeInspection, FieldInfo fieldInfo, boolean haveInitialiser, Value value,
                                           VariableProperty property, boolean allDelaysResolved) {
        // we can make this very efficient with streams, but it becomes pretty hard to debug
        List<Integer> values = new ArrayList<>();
        typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL).forEach(methodInfo -> {
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            if (methodAnalysis.fieldSummaries.isSet(fieldInfo)) {
                TransferValue tv = methodAnalysis.fieldSummaries.get(fieldInfo);
                if (tv.value.isSet()) {
                    int v = tv.value.get().getPropertyOutsideContext(property);
                    values.add(v);
                }
            }
        });
        if (haveInitialiser) {
            int v = value.getPropertyOutsideContext(property);
            values.add(v);
        }
        int result = property == VariableProperty.SIZE ? MethodAnalyser.safeMinimumForSize(messages, new Location(fieldInfo), values.stream().mapToInt(Integer::intValue)) :
                values.stream().mapToInt(Integer::intValue).min().orElse(property.falseValue);
        if (result == Level.DELAY && allDelaysResolved) return property.falseValue;
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
                    if (tv.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE) {
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

        if (values.size() == 1) {
            VariableValue variableValue = values.get(0).asInstanceOf(VariableValue.class);
            if (variableValue != null) {
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
        }

        // we could have checked this at the start, but then we'd miss the potential assignment between parameter and field

        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE || fieldAnalysis.effectivelyFinalValue.isSet())
            return false;

        // compute and set the combined value

        if (!fieldAnalysis.internalObjectFlows.isSet()) {
            log(DELAYED, "Delaying effectively final value because internal object flows not yet known, {}", fieldInfo.fullyQualifiedName());
            return false;
        }
        Value effectivelyFinalValue = determineEffectivelyFinalValue(fieldReference, values);

        ObjectFlow objectFlow = effectivelyFinalValue.getObjectFlow();
        if (objectFlow != ObjectFlow.NO_FLOW && !fieldAnalysis.objectFlow.isSet()) {
            log(OBJECT_FLOW, "Set final object flow object for field {}: {}", fieldInfo.fullyQualifiedName(), objectFlow);
            objectFlow.finalize(fieldAnalysis.objectFlow.getFirst());
            fieldAnalysis.objectFlow.set(objectFlow);
        }
        if (!fieldAnalysis.objectFlow.isSet()) {
            fieldAnalysis.objectFlow.set(fieldAnalysis.objectFlow.getFirst());
            log(OBJECT_FLOW, "Confirming the initial object flow for {}", fieldInfo.fullyQualifiedName());
        }

        fieldAnalysis.effectivelyFinalValue.set(effectivelyFinalValue);
        fieldAnalysis.setProperty(VariableProperty.CONSTANT, effectivelyFinalValue.isConstant());

        // check constant

        if (effectivelyFinalValue.isConstant()) {
            // directly adding the annotation; it will not be used for inspection
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(e2ImmuAnnotationExpressions, value);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
        } else {
            log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
            fieldAnalysis.annotations.put(e2ImmuAnnotationExpressions.constant.get(), false);
        }

        log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                fieldInfo.fullyQualifiedName(), effectivelyFinalValue);
        return true;
    }

    private Value determineEffectivelyFinalValue(FieldReference fieldReference, List<Value> values) {
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
        return new FinalFieldValue(fieldReference, combinedValue.getObjectFlow());
    }

    private boolean analyseLinked(FieldInfo fieldInfo,
                                  FieldAnalysis fieldAnalysis,
                                  TypeInspection typeInspection) {
        if (fieldAnalysis.variablesLinkedToMe.isSet()) return false;

        boolean allDefined = typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL)
                .allMatch(m ->
                        m.methodAnalysis.get().variablesLinkedToFieldsAndParameters.isSet() && (
                                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.isSet()));
        if (!allDefined) return false;

        Set<Variable> links = new HashSet<>();
        typeInspection.constructorAndMethodStream(TypeInspection.Methods.ALL)
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.isSet())
                .forEach(m -> links.addAll(m.methodAnalysis.get().fieldSummaries.get(fieldInfo).linkedVariables.get()));
        fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
        log(LINKED_VARIABLES, "FA: Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));

        // explicitly adding the annotation here; it will not be inspected.
        AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(e2ImmuAnnotationExpressions, links);
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
        int isAssignedOutsideConstructors = typeInspection.methodStream(TypeInspection.Methods.ALL)
                .filter(m -> !m.isPrivate() || m.isCalledFromNonPrivateMethod())
                .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                .mapToInt(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.ASSIGNED))
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
            messages.add(Message.newMessage(new Location(fieldInfo), Message.EFFECTIVELY_FINAL_FIELD_NOT_RECORD));
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
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotModified on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return false;
        }
        if (isFinal == Level.FALSE) {
            log(NOT_MODIFIED, "Field {} cannot be @NotModified, as it is not @Final", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            return true;
        }

        if (fieldInfo.type.isFunctionalInterface()) {
            return analyseNotModifiedFunctionalInterface(fieldInfo, fieldAnalysis);
        }

        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        int e2immutable = MultiLevel.value(immutable, MultiLevel.E2IMMUTABLE);
        if (e2immutable == MultiLevel.DELAY) {
            log(DELAYED, "Delaying @NotModified, no idea about dynamic type @E2Immutable");
            return false;
        }

        // NOTE: we do not need code to check if e2immutable is at least eventually level 2, since the getProperty(MODIFIED) in the first line
        // intercepts this!

        if (fieldSummariesNotYetSet) return false;

        // we only consider methods, not constructors!
        boolean allContentModificationsDefined = typeInspection.methodStream(TypeInspection.Methods.ALL).allMatch(m ->
                !m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) ||
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) < Level.TRUE ||
                        m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) != Level.DELAY);

        if (allContentModificationsDefined) {
            boolean modified = fieldCanBeWrittenFromOutsideThisType ||
                    typeInspection.methodStream(TypeInspection.Methods.ALL)
                            .filter(m -> m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo))
                            .filter(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) >= Level.TRUE)
                            .anyMatch(m -> m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) == Level.TRUE);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, modified);
            log(NOT_MODIFIED, "Mark field {} as {}", fieldInfo.fullyQualifiedName(), modified ? "@Modified" : "@NotModified");
            return true;
        }
        if (Logger.isLogEnabled(DELAYED)) {
            log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                    fieldInfo.fullyQualifiedName());
            typeInspection.methodStream(TypeInspection.Methods.ALL).filter(m ->
                    m.methodAnalysis.get().fieldSummaries.isSet(fieldInfo) &&
                            m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) == Level.TRUE &&
                            m.methodAnalysis.get().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) == Level.DELAY)
                    .forEach(m -> log(DELAYED, "Method {} reads the field, but we're still waiting"));
        }
        return false;
    }

    /*
    TODO at some point this should go beyond functional interfaces.

    TODO at some point this should go beyond the initializer; it should look at all assignments
     */
    private boolean analyseNotModifiedFunctionalInterface(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().initialiser.get();
        if (fieldInitialiser.implementationOfSingleAbstractMethod != null) {
            MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod;
            int modified = sam.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
            log(NOT_MODIFIED, "Field {} of functional interface type: copying MODIFIED {} from SAM", fieldInfo.fullyQualifiedName(), modified);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, modified);
            return true;
        }
        log(NOT_MODIFIED, "Field {} of functional interface type: undeclared, so not modified", fieldInfo.fullyQualifiedName());
        fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        return true;
    }

    public void check(FieldInfo fieldInfo) {
        // before we check, we copy the properties into annotations
        fieldInfo.fieldAnalysis.get().transferPropertiesToAnnotations(e2ImmuAnnotationExpressions);

        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        // TODO check the correct field name in @Linked(to="xxxx")
        check(fieldInfo, Linked.class, e2ImmuAnnotationExpressions.linked.get());
        check(fieldInfo, NotModified.class, e2ImmuAnnotationExpressions.notModified.get());
        check(fieldInfo, NotNull.class, e2ImmuAnnotationExpressions.notNull.get());
        check(fieldInfo, Final.class, e2ImmuAnnotationExpressions.effectivelyFinal.get());

        // dynamic type annotations
        check(fieldInfo, E1Immutable.class, e2ImmuAnnotationExpressions.e1Immutable.get());
        check(fieldInfo, E2Immutable.class, e2ImmuAnnotationExpressions.e2Immutable.get());
        check(fieldInfo, Container.class, e2ImmuAnnotationExpressions.container.get());
        check(fieldInfo, E1Container.class, e2ImmuAnnotationExpressions.e1Container.get());
        check(fieldInfo, E2Container.class, e2ImmuAnnotationExpressions.e2Container.get());

        // checks for dynamic properties of functional interface types
        check(fieldInfo, NotModified1.class, e2ImmuAnnotationExpressions.notModified1.get());

        // opposites
        check(fieldInfo, org.e2immu.annotation.Variable.class, e2ImmuAnnotationExpressions.variableField.get());
        check(fieldInfo, Modified.class, e2ImmuAnnotationExpressions.modified.get());
        check(fieldInfo, Nullable.class, e2ImmuAnnotationExpressions.nullable.get());

        CheckConstant.checkConstantForFields(messages, fieldInfo);
        CheckSize.checkSizeForFields(messages, fieldInfo);
    }

    private void check(FieldInfo fieldInfo, Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(fieldInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }
}
