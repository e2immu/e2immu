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
import org.e2immu.annotation.Constant;
import org.e2immu.annotation.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.TypeContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;


public class FieldAnalyser {
    private final TypeContext typeContext;

    public FieldAnalyser(TypeContext typeContext) {
        this.typeContext = typeContext;
    }

    public boolean analyse(FieldInfo fieldInfo, Variable thisVariable, VariableProperties fieldProperties) {
        boolean changes = false;

        Value value = null;
        if (fieldInfo.fieldInspection.get().initialiser.isSet()) {
            log(ANALYSER, "Evaluating field {}", fieldInfo.fullyQualifiedName());
            FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
            value = fieldInfo.fieldInspection.get().initialiser.get().evaluate(fieldProperties);
            fieldProperties.setValue(fieldReference, value);
            log(ANALYSER, "Evaluation of field {}: {}", fieldInfo.fullyQualifiedName(), value);
        }

        TypeInspection typeInspection = fieldInfo.owner.typeInspection.get();

        if (!fieldInfo.fieldAnalysis.annotations.isSet(typeContext.effectivelyFinal.get())) {
            log(ANALYSER, "Analysing field {}, value {}", fieldInfo.fullyQualifiedName(), value);

            boolean isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            if (isExplicitlyFinal) {
                fieldInfo.fieldAnalysis.annotations.put(typeContext.effectivelyFinal.get(), true);
                log(ANALYSER, "Mark field {} as effectively final, because explicitly so", fieldInfo.fullyQualifiedName());
                changes = true;
            } else {
                Boolean isModifiedOutsideConstructors = typeInspection.methods.stream()
                        .map(m -> m.methodAnalysis.fieldModifications.getOtherwiseNull(fieldInfo))
                        .reduce(false, TypeAnalyser.TERNARY_OR);

                if (isModifiedOutsideConstructors == null) {
                    log(ANALYSER, "Cannot yet conclude if {} is effectively final", fieldInfo.fullyQualifiedName());
                } else {
                    fieldInfo.fieldAnalysis.annotations.put(typeContext.effectivelyFinal.get(), !isModifiedOutsideConstructors);
                    log(ANALYSER, "Mark field {} as " + (isModifiedOutsideConstructors ? "not " : "") +
                            "effectively final, not modified outside constructors", fieldInfo.fullyQualifiedName());
                    changes = true;
                    if (!isModifiedOutsideConstructors && !fieldInfo.fieldAnalysis.initialValue.isSet()) {
                        // find the constructors where the value is set; if they're all set to the same value,
                        // we can set the initial value
                        Value consistentValue = null;
                        for (MethodInfo constructor : typeInspection.constructors) {
                            if (constructor.methodAnalysis.fieldAssignments.isSet(fieldInfo)) {
                                Value assignment = constructor.methodAnalysis.fieldAssignments.get(fieldInfo);
                                if (consistentValue == null) consistentValue = assignment;
                                else if (!consistentValue.equals(assignment)) {
                                    log(ANALYSER, "Cannot set consistent value for {}, have {} and {}", fieldInfo.fullyQualifiedName(),
                                            consistentValue, assignment);
                                    consistentValue = null;
                                    break;
                                }
                            }
                        }
                        if (consistentValue != null) {
                            if (consistentValue instanceof org.e2immu.analyser.model.Constant) {
                                fieldInfo.fieldAnalysis.initialValue.set(consistentValue);
                                log(ANALYSER, "Set initial value of effectively final {} to {}", fieldInfo.fullyQualifiedName(),
                                        consistentValue);
                                changes = true;
                            } else {
                                log(ANALYSER, "Not setting initial value of effectively final {}, not constant but {}",
                                        fieldInfo.fullyQualifiedName(), consistentValue);
                            }
                            if (!fieldInfo.fieldAnalysis.annotations.isSet(typeContext.notNull.get())) {
                                Boolean nonNull = consistentValue.isNotNull(fieldProperties);
                                if (nonNull != null) {
                                    log(ANALYSER, "Set non-null of effectively final {} to {}", fieldInfo.fullyQualifiedName(), nonNull);
                                    fieldInfo.fieldAnalysis.annotations.put(typeContext.notNull.get(), nonNull);
                                    changes = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (fieldInfo.isExplicitlyFinal()) {
            if (value != UnknownValue.UNKNOWN_VALUE && value != null) {
                if (!fieldInfo.fieldAnalysis.initialValue.isSet()) {
                    log(ANALYSER, "Setting initial value of {} to {}", fieldInfo.fullyQualifiedName(), value);
                    fieldInfo.fieldAnalysis.initialValue.set(value);
                    changes = true;
                }
                if (value.isNotNull(fieldProperties) == Boolean.TRUE &&
                        !fieldInfo.fieldAnalysis.annotations.isSet(typeContext.notNull.get())) {
                    log(ANALYSER, "Mark field {} as not null, given that it is final and the value initializes to not null",
                            fieldInfo.fullyQualifiedName());
                    fieldInfo.fieldAnalysis.annotations.put(typeContext.notNull.get(), true);
                    changes = true;
                }
            }
        }
        if (!fieldInfo.fieldAnalysis.annotations.isSet(typeContext.notNull.get()) &&
                fieldInfo.fieldAnalysis.annotations.getOtherwiseNull(typeContext.effectivelyFinal.get()) == Boolean.TRUE &&
                fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
            log(ANALYSER, "Checking for link on {}", fieldInfo.fullyQualifiedName());

            Set<Variable> variables = fieldInfo.fieldAnalysis.variablesLinkedToMe.get();
            Boolean allNotNull = true;
            for (Variable variable : variables) {
                if (variable instanceof FieldReference) {
                    Boolean notNull = ((FieldReference) variable).fieldInfo.fieldAnalysis.annotations.getOtherwiseNull(typeContext.notNull.get());
                    if (notNull == null) allNotNull = null;
                    else if (!notNull) allNotNull = false;
                } else if (variable instanceof ParameterInfo) {
                    Boolean nullNotAllowed = ((ParameterInfo) variable).parameterAnalysis.annotations.getOtherwiseNull(typeContext.nullNotAllowed.get());
                    if (nullNotAllowed == null) {
                        allNotNull = null;
                    } else if (!nullNotAllowed) allNotNull = false;
                }
                if (allNotNull == null) break;
            }
            if (allNotNull != null) {
                log(ANALYSER, "Mark that {} is @NotNull? {}", fieldInfo.fullyQualifiedName(), allNotNull);
                fieldInfo.fieldAnalysis.annotations.put(typeContext.notNull.get(), allNotNull);
            } else {
                log(ANALYSER, "Cannot yet conclude @NotNull on {}", fieldInfo.fullyQualifiedName());
            }
        }

        // read == null -> not read, does not count ==> FALSE
        // read & cm == null -> don't know ==> NULL
        // read & cm == false -> not modified ==> FALSE
        // read & cm == true -> modified ==> TRUE
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldProperties.thisVariable);

        if (!fieldInfo.fieldAnalysis.annotations.isSet(typeContext.notModified.get())) {
            // first check if we're dealing with fields of ENUM's; they're not modifiable at all
            if(fieldInfo.owner.typeInspection.get().typeNature == TypeNature.ENUM) {
                fieldInfo.fieldAnalysis.annotations.put(typeContext.notModified.get(), true);
                log(MODIFY_CONTENT, "FA: Mark field {} of enum as @NotModified", fieldInfo.fullyQualifiedName());
                changes = true;
            } else {
                boolean allReadNotNull = typeInspection.constructorAndMethodStream().allMatch(m ->
                        m.methodAnalysis.fieldRead.isSet(fieldInfo));
                if (allReadNotNull) {
                    boolean notDecided = typeInspection.constructorAndMethodStream().anyMatch(m ->
                            !m.methodAnalysis.directContentModifications.isSet(fieldReference));
                    if (notDecided) {
                        log(MODIFY_CONTENT, "Cannot yet conclude if {}'s contents have been modified, not all modifications known",
                                fieldInfo.fullyQualifiedName());
                    } else {
                        boolean notModified = typeInspection.constructorAndMethodStream().allMatch(m ->
                                m.methodAnalysis.directContentModifications.getOtherwiseNull(fieldReference) != Boolean.TRUE);
                        fieldInfo.fieldAnalysis.annotations.put(typeContext.notModified.get(), notModified);
                        log(MODIFY_CONTENT, "FA: Mark field {} as " + (notModified ? "" : "not ") +
                                "@NotModified", fieldInfo.fullyQualifiedName());
                        changes = true;
                    }
                } else {
                    log(MODIFY_CONTENT, "Cannot yet conclude if {}'s contents have been modified, not all read",
                            fieldInfo.fullyQualifiedName());
                }
            }
        }

        if (!fieldInfo.fieldAnalysis.variablesLinkedToMe.isSet()) {
            boolean allDefined = typeInspection.constructorAndMethodStream()
                    .allMatch(m ->
                            m.methodAnalysis.fieldModifications.getOtherwiseNull(fieldInfo) == Boolean.FALSE ||
                                    m.methodAnalysis.fieldModifications.getOtherwiseNull(fieldInfo) == Boolean.TRUE &&
                                            m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference));
            if (allDefined) {
                Set<Variable> links = new HashSet<>();
                typeInspection.constructorAndMethodStream().forEach(m -> {
                    if (m.methodAnalysis.fieldsLinkedToFieldsAndVariables.isSet(fieldReference))
                        links.addAll(m.methodAnalysis.fieldsLinkedToFieldsAndVariables.get(fieldReference));
                });
                fieldInfo.fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
                log(LINKED_VARIABLES, "Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.detailedString(links));
                changes = true;

                // TODO set correct names in the annotation
                fieldInfo.fieldAnalysis.annotations.put(typeContext.linked.get(), !links.isEmpty());
            }
        }
        return changes;
    }

    public void check(FieldInfo fieldInfo) {
        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        // TODO check the correct field name
        fieldInfo.error(Linked.class, typeContext.linked.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @Linked"));

        fieldInfo.error(NotModified.class, typeContext.notModified.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @NotModified"));

        fieldInfo.error(NotNull.class, typeContext.notNull.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @NotNull"));

        fieldInfo.error(Final.class, typeContext.effectivelyFinal.get()).ifPresent(mustBeAbsent ->
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                        " should " + (mustBeAbsent ? "not " : "") + "be marked @Final"));

        if (fieldInfo.fieldInspection.get().modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = fieldInfo.owner.typeInspection.get().methods.stream()
                        .anyMatch(m -> m.methodAnalysis.fieldRead.getOtherwiseNull(fieldInfo) == Boolean.TRUE);
                if (!readInMethods) {
                    typeContext.addMessage(Message.Severity.ERROR, "Private field " + fieldInfo.fullyQualifiedName() +
                            " is not read outside constructors");
                }
            }
        } else if (fieldInfo.fieldAnalysis.annotations.getOtherwiseNull(typeContext.effectivelyFinal.get()) != Boolean.TRUE) {
            typeContext.addMessage(Message.Severity.ERROR, "Non-private field " + fieldInfo.fullyQualifiedName() +
                    " is not effectively final (@Final)");
        }

        Optional<AnnotationExpression> oConstant = fieldInfo.getInspection().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        oConstant.ifPresent(constant -> {
            boolean testValue = constant.test();
            Value valueToTest = null;
            String typeMsg = "";
            if (isIntType(fieldInfo.type)) {
                int value = constant.intValue();
                if (value != 0) testValue = true;
                valueToTest = new IntValue(value);
                typeMsg = "int";
            } else if (isBoolType(fieldInfo.type)) {
                boolean value = constant.boolValue();
                if (value) testValue = true;
                valueToTest = BoolValue.of(value);
                typeMsg = "boolean";
            } else if (isStringType(fieldInfo.type)) {
                String value = constant.stringValue();
                if (!"".equals(value)) testValue = true;
                valueToTest = value == null ? NullValue.NULL_VALUE : new StringValue(value);
                typeMsg = "string";
            }
            if (fieldInfo.fieldAnalysis.initialValue.isSet()) {
                if (testValue) {
                    log(ANALYSER, "Checking value {}, type {}", valueToTest, typeMsg);
                    Value value = fieldInfo.fieldAnalysis.initialValue.get();
                    if (!value.equals(valueToTest)) {
                        typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() +
                                ": expected constant value " + valueToTest + " of type " + typeMsg + ", got " + value);
                    }
                }
            } else {
                typeContext.addMessage(Message.Severity.ERROR, "Field " + fieldInfo.fullyQualifiedName() + " has no initial value set");
            }
        });
    }

    private boolean isType(ParameterizedType type, Set<String> typeNames) {
        return type.typeInfo != null && typeNames.contains(type.typeInfo.fullyQualifiedName);
    }

    private boolean isIntType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.Integer", "int"));
    }

    private boolean isBoolType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.Boolean", "boolean"));
    }

    private boolean isStringType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.String"));
    }

}
