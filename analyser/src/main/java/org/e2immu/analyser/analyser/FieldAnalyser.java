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
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.analyser.check.CheckSize;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.Logger;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.model.abstractvalue.UnknownValue.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class FieldAnalyser extends AbstractAnalyser {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FieldAnalyser.class);

    // analyser components, constants are used in tests
    public static final String COMPUTE_IMPLICITLY_IMMUTABLE_DATA_TYPE = "computeImplicitlyImmutableDataType";
    public static final String EVALUATE_INITIALISER = "evaluateInitialiser";
    public static final String ANALYSE_FINAL = "analyseFinal";
    public static final String ANALYSE_FINAL_VALUE = "analyseFinalValue";
    public static final String ANALYSE_DYNAMIC_TYPE_ANNOTATION_IMMUTABLE = "analyseDynamicTypeAnnotation:IMMUTABLE";
    public static final String ANALYSE_NOT_NULL = "analyseNotNull";
    public static final String ANALYSE_MODIFIED = "analyseModified";
    public static final String ANALYSE_SIZE = "analyseSize";
    public static final String ANALYSE_SIZE_AS_DYNAMIC_TYPE_ANNOTATION = "analyseSizeAsDynamicTypeAnnotation";
    public static final String ANALYSE_NOT_MODIFIED_1 = "analyseNotModified1";
    public static final String ANALYSE_LINKED = "analyseLinked";
    public static final String FIELD_ERRORS = "fieldErrors";

    public final TypeInfo primaryType;
    public final FieldInfo fieldInfo;
    public final FieldInspection fieldInspection;
    public final FieldAnalysisImpl.Builder fieldAnalysis;
    public final MethodAnalyser sam;
    private final boolean fieldCanBeWrittenFromOutsideThisType;
    private final AnalyserComponents<String, Integer> analyserComponents;

    private List<MethodAnalyser> allMethodsAndConstructors;
    private List<MethodAnalyser> myMethodsAndConstructors;
    private TypeAnalyser myTypeAnalyser;
    private boolean fieldSummariesNotYetSet;
    private final boolean haveInitialiser;
    private Value initialiserValue;

    public FieldAnalyser(FieldInfo fieldInfo,
                         TypeInfo primaryType,
                         TypeAnalysis ownerTypeAnalysis,
                         MethodAnalyser sam,
                         AnalyserContext analyserContext) {
        super("Field " + fieldInfo.name, analyserContext);
        this.fieldInfo = fieldInfo;
        fieldInspection = fieldInfo.fieldInspection.get();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext, fieldInfo, ownerTypeAnalysis);
        this.primaryType = primaryType;
        this.sam = sam;
        fieldCanBeWrittenFromOutsideThisType = fieldInfo.owner.isRecord() || !fieldInfo.isPrivate() && !fieldInfo.isExplicitlyFinal();
        haveInitialiser = fieldInspection.initialiser.isSet() && fieldInspection.initialiser.get().initialiser != EmptyExpression.EMPTY_EXPRESSION;

        analyserComponents = new AnalyserComponents.Builder<String, Integer>()
                .add(COMPUTE_IMPLICITLY_IMMUTABLE_DATA_TYPE, (iteration) -> computeImplicitlyImmutableDataType())
                .add(EVALUATE_INITIALISER, this::evaluateInitialiser)
                .add(ANALYSE_FINAL, (iteration) -> analyseFinal())
                .add(ANALYSE_FINAL_VALUE, (iteration) -> analyseFinalValue())
                .add(ANALYSE_DYNAMIC_TYPE_ANNOTATION_IMMUTABLE, (iteration) -> analyseDynamicTypeAnnotation(iteration, VariableProperty.IMMUTABLE))
                .add(ANALYSE_NOT_NULL, this::analyseNotNull)
                .add(ANALYSE_MODIFIED, (iteration) -> analyseModified())
                .add(ANALYSE_SIZE, this::analyseSize)
                .add(ANALYSE_SIZE_AS_DYNAMIC_TYPE_ANNOTATION, this::analyseSizeAsDynamicTypeAnnotation)
                .add(ANALYSE_NOT_MODIFIED_1, (iteration) -> analyseNotModified1())
                .add(ANALYSE_LINKED, (iteration) -> analyseLinked())
                .add(FIELD_ERRORS, (iteration) -> fieldErrors())
                .build();
    }

    @Override
    public AnalyserComponents<String, Integer> getAnalyserComponents() {
        return analyserComponents;
    }

    @Override
    public Analysis getAnalysis() {
        return fieldAnalysis;
    }

    @Override
    public WithInspectionAndAnalysis getMember() {
        return fieldInfo;
    }

    @Override
    public void initialize() {
        ImmutableList.Builder<MethodAnalyser> allMethodsAndConstructors = new ImmutableList.Builder<>();
        ImmutableList.Builder<MethodAnalyser> myMethodsAndConstructors = new ImmutableList.Builder<>();

        analyserContext.getMethodAnalysers().values().forEach(analyser -> {
            allMethodsAndConstructors.add(analyser);
            if (analyser.methodInfo.typeInfo == fieldInfo.owner) {
                myMethodsAndConstructors.add(analyser);
            }
        });
        myTypeAnalyser = analyserContext.getTypeAnalysers().get(fieldInfo.owner);
        this.allMethodsAndConstructors = allMethodsAndConstructors.build();
        this.myMethodsAndConstructors = myMethodsAndConstructors.build();
    }

    @Override
    public AnalysisStatus analyse(int iteration) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());
        fieldSummariesNotYetSet = iteration == 0;

        // analyser visitors
        try {
            AnalysisStatus analysisStatus = analyserComponents.run(iteration);

            for (FieldAnalyserVisitor fieldAnalyserVisitor : analyserContext.getConfiguration().debugConfiguration.afterFieldAnalyserVisitors) {
                fieldAnalyserVisitor.visit(new FieldAnalyserVisitor.Data(iteration, fieldInfo, fieldAnalysis, analyserComponents.getStatusesAsMap()));
            }

            return analysisStatus;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", fieldInfo.fullyQualifiedName());
            throw rte;
        }
    }

    private AnalysisStatus evaluateInitialiser(int iteration) {

        // STEP 1: THE INITIALISER
        AnalysisStatus resultOfObjectFlow;

        Value value;
        if (fieldInspection.initialiser.isSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.initialiser.get();
            if (fieldInitialiser.initialiser != EmptyExpression.EMPTY_EXPRESSION) {
                EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, ConditionManager.INITIAL);
                EvaluationResult evaluationResult = fieldInitialiser.initialiser.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                initialiserValue = evaluationResult.value;
                resultOfObjectFlow = makeInternalObjectFlowsPermanent(evaluationResult);
                value = evaluationResult.value;
                log(FINAL, "Set initialiser of field {} to {}", fieldInfo.fullyQualifiedName(), value);
                return resultOfObjectFlow.combine(initialiserValue == NO_VALUE ? DELAYS : DONE);
            }
        }
        initialiserValue = NO_VALUE; // initialiser set, but to empty expression
        return DONE;
    }

    private AnalysisStatus makeInternalObjectFlowsPermanent(EvaluationResult evaluationResult) {
        assert !fieldAnalysis.internalObjectFlows.isSet();
        Set<ObjectFlow> internalObjectFlows = evaluationResult.getObjectFlowStream().collect(Collectors.toUnmodifiableSet());
        boolean noDelays = internalObjectFlows.stream().noneMatch(ObjectFlow::isDelayed);
        if (noDelays) {
            internalObjectFlows.forEach(of -> of.finalize(null));
            fieldAnalysis.internalObjectFlows.set(internalObjectFlows);
            log(OBJECT_FLOW, "Set {} internal object flows on {}", internalObjectFlows.size(), fieldInfo.fullyQualifiedName());
            return DONE;
        }
        log(DELAYED, "Not yet setting internal object flows on {}, delaying", fieldInfo.fullyQualifiedName());
        return DELAYS;
    }


    private AnalysisStatus analyseSizeAsDynamicTypeAnnotation(int iteration) {
        int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) return DELAYS;
        if (modified == Level.FALSE)
            return analyseDynamicTypeAnnotation(iteration, VariableProperty.SIZE);
        return DONE; // not for me
    }

    private AnalysisStatus computeImplicitlyImmutableDataType() {
        assert !fieldAnalysis.isOfImplicitlyImmutableDataType.isSet();
        if (myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes() == null) return DELAYS;
        boolean implicit = myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes().contains(fieldInfo.type);
        fieldAnalysis.isOfImplicitlyImmutableDataType.set(implicit);
        return DONE;
    }

    private AnalysisStatus analyseNotModified1() {
        if (!fieldInfo.type.isFunctionalInterface() || sam == null) return DONE; // not for me
        assert fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED_1) == Level.DELAY;

        boolean someParameterModificationUnknown = sam.getParameterAnalysers().stream().anyMatch(p ->
                p.parameterAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY);
        if (someParameterModificationUnknown) {
            log(NOT_MODIFIED, "Delaying @NotModified1 on {}, some parameters have no @Modified status", fieldInfo.fullyQualifiedName());
        }
        boolean allParametersNotModified = sam.getParameterAnalysers().stream().allMatch(p ->
                p.parameterAnalysis.getProperty(VariableProperty.MODIFIED) == Level.FALSE);

        log(NOT_MODIFIED, "Set @NotModified1 on {} to {}", fieldInfo.fullyQualifiedName(), allParametersNotModified);
        fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, allParametersNotModified);
        return DONE;
    }

    // TODO SIZE = min over assignments IF the field is not modified + not exposed or e2immu + max over restrictions + max of these two

    private AnalysisStatus analyseSize(int iteration) {
        assert fieldAnalysis.getProperty(VariableProperty.SIZE) == Level.DELAY;

        if (!fieldInfo.type.hasSize()) {
            log(SIZE, "No @Size annotation on {}, because the type has no size!", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.FALSE); // in the case of size, FALSE there cannot be size
            return DONE;
        }
        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisType) {
            log(SIZE, "Field {} cannot have @Size: it is not @Final, and it can be assigned to from outside this class", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.FALSE); // in the case of size, FALSE there cannot be size
            return DONE;
        }
        if (fieldSummariesNotYetSet) return DELAYS;

        // now for the more serious restrictions... if the type is @E2Immu, we can have a @Size restriction (actually, size is constant!)
        // if the field is @NotModified, and not exposed, then @Size is governed by the assignments and restrictions of the method.
        // but if the field is exposed somehow, or modified in the type, we must stick to @Size(min >= 0) (we have a size)
        int modified = fieldAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @NotModified", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (modified == Level.TRUE) {
            fieldAnalysis.setProperty(VariableProperty.SIZE, Level.IS_A_SIZE);
            log(SIZE, "Setting @Size on {} to @Size(min = 0), meaning 'we have a @Size, but nothing else'", fieldInfo.fullyQualifiedName());
            return DONE;
        }
        int e2Immutable = MultiLevel.value(fieldAnalysis.getProperty(VariableProperty.IMMUTABLE), MultiLevel.E2IMMUTABLE);
        if (e2Immutable == MultiLevel.DELAY) {
            log(DELAYED, "Delaying @Size on {} until we know about @E2Immutable", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (someAssignmentValuesUndefined(VariableProperty.SIZE)) return DELAYS;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved();

        int valueFromAssignment = computeValueFromAssignment(iteration, VariableProperty.SIZE, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int valueFromContext = computeValueFromContext(VariableProperty.SIZE, allDelaysResolved);
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        if (valueFromContext > valueFromAssignment) {
            log(SIZE, "Problematic: assignments have lower value than requirements for @Size");
            messages.add(Message.newMessage(new Location(fieldInfo), Message.POTENTIAL_SIZE_PROBLEM));
        }
        int finalValue = Level.best(valueFromAssignment, valueFromContext);
        log(SIZE, "Set property @Size on field {} to value {}", fieldInfo.fullyQualifiedName(), finalValue);
        fieldAnalysis.setProperty(VariableProperty.SIZE, finalValue);
        return DONE;
    }

    private AnalysisStatus analyseNotNull(int iteration) {
        assert fieldAnalysis.getProperty(VariableProperty.NOT_NULL) <= MultiLevel.DELAY;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (isFinal == Level.FALSE && (!haveInitialiser || fieldCanBeWrittenFromOutsideThisType)) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it is not @Final, or has no initialiser, "
                    + " or it can be assigned to from outside this class", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
            return DONE;
        }
        if (fieldSummariesNotYetSet) return DELAYS;

        if (someAssignmentValuesUndefined(VariableProperty.NOT_NULL)) return DELAYS;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved();

        int valueFromAssignment = computeValueFromAssignment(iteration, VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int valueFromContext = computeValueFromContext(VariableProperty.NOT_NULL, allDelaysResolved);
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int finalNotNullValue = MultiLevel.bestNotNull(valueFromAssignment, valueFromContext);
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fieldInfo.fullyQualifiedName(), finalNotNullValue);

        if (isFinal == Level.TRUE && MultiLevel.value(finalNotNullValue, MultiLevel.NOT_NULL) == MultiLevel.EFFECTIVE) {
            List<MethodAnalyser> methodsWhereFieldIsAssigned = methodsWhereFieldIsAssigned();
            if (methodsWhereFieldIsAssigned.size() > 0 && !haveInitialiser) {

                boolean linkingAndPreconditionsComputed = methodsWhereFieldIsAssigned.stream()
                        .allMatch(m -> m.methodLevelData().variablesLinkedToFieldsAndParameters.isSet() && m.methodAnalysis.precondition.isSet());
                if (!linkingAndPreconditionsComputed) {
                    log(DELAYED, "Delaying property @NotNull on {}, waiting for linking and preconditions", fieldInfo.fullyQualifiedName());
                    return DELAYS;
                }

                // check that all methods have a precondition, and that the variable is linked to at least one of the parameters occurring in the precondition
                boolean linkedToVarsInPrecondition = methodsWhereFieldIsAssigned.stream().allMatch(m ->
                        m.methodAnalysis.precondition.isSet() &&
                                !Collections.disjoint(safeLinkedVariables(m.methodLevelData().fieldSummaries.get(fieldInfo)),
                                        m.methodAnalysis.precondition.get().variables()));
                if (linkedToVarsInPrecondition) {
                    // we now check if a not-null is compatible with the pre-condition
                    boolean allCompatible = methodsWhereFieldIsAssigned.stream().allMatch(m -> {
                        Value assignment = m.methodLevelData().fieldSummaries.get(fieldInfo).value.get();
                        // the properties of the fieldSummary are in the "properties" of TransferValue, not in the value
                        // TODO there should be a better way to do this
                        Value fieldIsNotNull = NegatedValue.negate(EqualsValue.equals(NullValue.NULL_VALUE, assignment, ObjectFlow.NO_FLOW, null));
                        Value andValue = new AndValue(ObjectFlow.NO_FLOW).append(m.methodAnalysis.precondition.get(), fieldIsNotNull);
                        return andValue != BoolValue.FALSE;
                    });
                    if (allCompatible) {
                        log(NOT_NULL, "Setting @Nullable on {}, already in precondition", fieldInfo.fullyQualifiedName());
                        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, MultiLevel.NULLABLE);
                        return DONE;
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
        return DONE;
    }

    private static Set<Variable> safeLinkedVariables(TransferValue transferValue) {
        return transferValue.linkedVariables.isSet() ? transferValue.linkedVariables.get() : Set.of();
    }

    private List<MethodAnalyser> methodsWhereFieldIsAssigned() {
        return allMethodsAndConstructors.stream()
                .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                .filter(m -> m.methodLevelData().fieldSummaries.get(fieldInfo)
                        .getProperty(VariableProperty.ASSIGNED) >= Level.TRUE)
                .collect(Collectors.toList());
    }

    private AnalysisStatus fieldErrors() {
        assert !fieldAnalysis.fieldError.isSet();

        if (fieldInspection.modifiers.contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                if (fieldSummariesNotYetSet) return DELAYS;
                int readInMethods = allMethodsAndConstructors.stream()
                        .filter(m -> !(m.methodInfo.isConstructor && m.methodInfo.typeInfo == fieldInfo.owner)) // not my own constructors
                        .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo)) // field seen
                        .mapToInt(m -> m.methodLevelData().fieldSummaries.get(fieldInfo)
                                .properties.getOtherwise(VariableProperty.READ, Level.FALSE))
                        .max().orElse(Level.FALSE);
                if (readInMethods == Level.DELAY) {
                    log(DELAYED, "Not yet ready to decide on read outside constructors");
                    return DELAYS;
                }
                boolean notRead = readInMethods == Level.FALSE;
                fieldAnalysis.fieldError.set(notRead);
                if (notRead) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.PRIVATE_FIELD_NOT_READ));
                }
                return DONE;
            }
        } else if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.FALSE) {
            // only react once we're certain the variable is not effectively final
            // error, unless we're in a record
            boolean record = fieldInfo.owner.isRecord();
            fieldAnalysis.fieldError.set(!record);
            if (!record) {
                messages.add(Message.newMessage(new Location(fieldInfo), Message.NON_PRIVATE_FIELD_NOT_FINAL));
            } // else: nested private types can have fields the way they like it
            return DONE;
        }
        // not for me
        return DONE;
    }

    private AnalysisStatus analyseDynamicTypeAnnotation(int iteration, VariableProperty property) {
        if (fieldInfo.type.isFunctionalInterface()) return DONE; // not for me
        // not an assert, because the value is not directly determined by the actual property
        if (fieldAnalysis.getProperty(property) != Level.DELAY) return DONE;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying {} on {} until we know about @Final", property, fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisType) {
            log(NOT_NULL, "Field {} cannot be {}: it is not @Final, and it can be assigned to from outside this class",
                    fieldInfo.fullyQualifiedName(), property);
            fieldAnalysis.setProperty(property, property.falseValue); // in the case of size, FALSE means >= 0
            return DONE;
        }
        if (fieldSummariesNotYetSet) return DELAYS;
        if (someAssignmentValuesUndefined(property)) return DELAYS;

        boolean allDelaysResolved = delaysOnFieldSummariesResolved();

        // compute the value of the assignments
        int valueFromAssignment = computeValueFromAssignment(iteration, property, allDelaysResolved);
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property {} on field {}, initialiser delayed", property, fieldInfo.fullyQualifiedName());
            return DELAYS; // delay
        }
        log(DYNAMIC, "Set property {} on field {} to value {}", property, fieldInfo.fullyQualifiedName(), valueFromAssignment);
        fieldAnalysis.setProperty(property, valueFromAssignment);
        return DONE;
    }

    private boolean someAssignmentValuesUndefined(VariableProperty property) {
        boolean allAssignmentValuesDefined = allMethodsAndConstructors.stream().allMatch(m ->
                // field is not present in the method
                !m.methodLevelData().fieldSummaries.isSet(fieldInfo) ||
                        // field is not assigned to in the method
                        m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.ASSIGNED) < Level.TRUE ||
                        // if it is present, assigned to, it needs to have a value
                        m.methodLevelData().fieldSummaries.get(fieldInfo).value.isSet());

        if (!allAssignmentValuesDefined) {
            log(DELAYED, "Delaying property {} on field {}, not all assignment values defined",
                    property, fieldInfo.fullyQualifiedName());
            return true;
        }
        return false;
    }

    private boolean delaysOnFieldSummariesResolved() {
        return allMethodsAndConstructors.stream()
                .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                .map(m -> m.methodLevelData().fieldSummaries.get(fieldInfo))
                .noneMatch(fs -> fs.getProperty(VariableProperty.METHOD_DELAY_RESOLVED) == Level.FALSE);
        // FALSE indicates that there are delays, TRUE that they have been resolved, DELAY that we're not aware
    }

    private int computeValueFromContext(VariableProperty property, boolean allDelaysResolved) {
        IntStream contextRestrictions = allMethodsAndConstructors.stream()
                .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                .mapToInt(m -> m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(property));
        int result = contextRestrictions.max().orElse(Level.DELAY);
        if (result == Level.DELAY && allDelaysResolved) return property.falseValue;
        return result;
    }

    private int computeValueFromAssignment(int iteration, VariableProperty property, boolean allDelaysResolved) {
        // we can make this very efficient with streams, but it becomes pretty hard to debug
        List<Integer> values = new ArrayList<>();
        EvaluationContext evaluationContext = new EvaluationContextImpl(iteration, new ConditionManager());
        allMethodsAndConstructors.forEach(m -> {
            if (m.methodLevelData().fieldSummaries.isSet(fieldInfo)) {
                TransferValue tv = m.methodLevelData().fieldSummaries.get(fieldInfo);
                if (tv.value.isSet()) {
                    int v = evaluationContext.getProperty(tv.value.get(), property);
                    values.add(v);
                }
            }
        });
        if (haveInitialiser) {
            int v = evaluationContext.getProperty(initialiserValue, property);
            values.add(v);
        }
        int result = property == VariableProperty.SIZE ? MethodAnalyser.safeMinimumForSize(messages,
                new Location(fieldInfo), values.stream().mapToInt(Integer::intValue)) :
                values.stream().mapToInt(Integer::intValue).min().orElse(property.falseValue);
        if (result == Level.DELAY && allDelaysResolved) return property.falseValue;
        return result;
    }

    private AnalysisStatus analyseFinalValue() {
        List<Value> values = new LinkedList<>();
        if (haveInitialiser) {
            if (initialiserValue == NO_VALUE) {
                log(DELAYED, "Delaying consistent value for field " + fieldInfo.fullyQualifiedName());
                return DELAYS;
            }
            values.add(initialiserValue);
        }
        if (!(fieldInfo.isExplicitlyFinal() && haveInitialiser)) {
            if (fieldSummariesNotYetSet) return DELAYS;
            for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
                if (methodAnalyser.methodLevelData().fieldSummaries.isSet(fieldInfo)) {
                    TransferValue tv = methodAnalyser.methodLevelData().fieldSummaries.get(fieldInfo);
                    if (tv.getProperty(VariableProperty.ASSIGNED) >= Level.TRUE) {
                        if (tv.value.isSet()) {
                            values.add(tv.value.get());
                        } else {
                            log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                            return DELAYS;
                        }
                    }
                }
            }
        }

        // field linked to parameter

        if (values.size() == 1) {
            VariableValue variableValue = values.get(0).asInstanceOf(VariableValue.class);
            if (variableValue != null) {
                if (variableValue.variable instanceof ParameterInfo parameterInfo) {
                    ParameterAnalyser parameterAnalyser = analyserContext.getParameterAnalysers().get(parameterInfo);
                    if (parameterAnalyser.parameterAnalysis.assignedToField.isSet()) {
                        parameterAnalyser.parameterAnalysis.assignedToField.set(fieldInfo);
                        log(CONSTANT, "Field {} has been assigned to parameter {}", fieldInfo.name, parameterInfo.fullyQualifiedName());
                    }
                } else {
                    log(CONSTANT, "Field {} is assignment linked to another field? what would be the purpose?", fieldInfo.fullyQualifiedName());
                }
            }
        }

        // we could have checked this at the start, but then we'd miss the potential assignment between parameter and field

        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE || fieldAnalysis.effectivelyFinalValue.isSet())
            return DONE;

        // compute and set the combined value

        if (!fieldAnalysis.internalObjectFlows.isSet()) {
            log(DELAYED, "Delaying effectively final value because internal object flows not yet known, {}", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        Value effectivelyFinalValue = determineEffectivelyFinalValue(values);

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

        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (effectivelyFinalValue.isConstant()) {
            // directly adding the annotation; it will not be used for inspection
            AnnotationExpression constantAnnotation = CheckConstant.createConstantAnnotation(e2, initialiserValue);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
        } else {
            log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
            fieldAnalysis.annotations.put(e2.constant.get(), false);
        }

        log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                fieldInfo.fullyQualifiedName(), effectivelyFinalValue);
        return DONE;
    }

    private Value determineEffectivelyFinalValue(List<Value> values) {
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
        This thisVariable = new This(fieldInfo.owner);
        FieldReference fieldReference = new FieldReference(fieldInfo, fieldInfo.isStatic() ? null : thisVariable);
        Map<VariableProperty, Integer> properties = new HashMap<>();
        for (VariableProperty variableProperty : VariableProperty.FROM_FIELD_TO_PROPERTIES) {
            properties.put(variableProperty, fieldAnalysis.getProperty(variableProperty));
        }
        Set<Variable> linkedVariables = fieldAnalysis.variablesLinkedToMe.isSet() ? fieldAnalysis.variablesLinkedToMe.get() : Set.of();
        return new FinalFieldValue(fieldReference, properties, linkedVariables, combinedValue.getObjectFlow());
    }

    private AnalysisStatus analyseLinked() {
        assert !fieldAnalysis.variablesLinkedToMe.isSet();

        boolean allDefined = allMethodsAndConstructors.stream()
                .allMatch(m ->
                        m.methodLevelData().variablesLinkedToFieldsAndParameters.isSet() && (
                                !m.methodLevelData().fieldSummaries.isSet(fieldInfo) ||
                                        m.methodLevelData().fieldSummaries.get(fieldInfo).linkedVariables.isSet()));
        if (!allDefined) {
            if (Logger.isLogEnabled(DELAYED)) {
                log(DELAYED, "VariablesLinkedToFieldsAndParameters not yet set for methods: [{}]",
                        allMethodsAndConstructors.stream()
                                .filter(m -> !m.methodLevelData().variablesLinkedToFieldsAndParameters.isSet())
                                .map(m -> m.methodInfo.name).collect(Collectors.joining(", ")));
                log(DELAYED, "LinkedVariables not yet set for methods: [{}]",
                        allMethodsAndConstructors.stream()
                                .filter(m -> m.methodLevelData().variablesLinkedToFieldsAndParameters.isSet())
                                .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo) &&
                                        !m.methodLevelData().fieldSummaries.get(fieldInfo).linkedVariables.isSet())
                                .map(m -> m.methodInfo.name).collect(Collectors.joining(", ")));
            }
            return DELAYS;
        }

        Set<Variable> links = new HashSet<>();
        allMethodsAndConstructors.stream()
                .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                .filter(m -> m.methodLevelData().fieldSummaries.get(fieldInfo).linkedVariables.isSet())
                .forEach(m -> links.addAll(m.methodLevelData().fieldSummaries.get(fieldInfo).linkedVariables.get()));
        fieldAnalysis.variablesLinkedToMe.set(ImmutableSet.copyOf(links));
        log(LINKED_VARIABLES, "FA: Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.fullyQualifiedName(links));

        // explicitly adding the annotation here; it will not be inspected.
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        AnnotationExpression linkAnnotation = CheckLinks.createLinkAnnotation(e2, links);
        fieldAnalysis.annotations.put(linkAnnotation, !links.isEmpty());
        return DONE;
    }

    private AnalysisStatus analyseFinal() {
        assert fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.DELAY;

        if (fieldInfo.isExplicitlyFinal()) {
            fieldAnalysis.setProperty(VariableProperty.FINAL, Level.TRUE);
            return DONE;
        }

        if (fieldSummariesNotYetSet) return DELAYS;

        boolean isFinal;
        if (fieldCanBeWrittenFromOutsideThisType) {
            // this means other types can write to the field... not final by definition
            isFinal = false;
        } else {
            int isAssignedOutsideConstructors = allMethodsAndConstructors.stream()
                    .filter(m -> !m.methodInfo.isPrivate() || m.methodInfo.isCalledFromNonPrivateMethod())
                    .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                    .mapToInt(m -> m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.ASSIGNED))
                    .max().orElse(Level.DELAY);
            isFinal = isAssignedOutsideConstructors < Level.TRUE;
        }
        fieldAnalysis.setProperty(VariableProperty.FINAL, isFinal);
        if (isFinal && fieldInfo.type.isRecordType()) {
            messages.add(Message.newMessage(new Location(fieldInfo), Message.EFFECTIVELY_FINAL_FIELD_NOT_RECORD));
        }
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final", fieldInfo.fullyQualifiedName());
        return DONE;
    }

    private AnalysisStatus analyseModified() {
        assert fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY;

        if (fieldInfo.type.isFunctionalInterface()) {
            return analyseNotModifiedFunctionalInterface();
        }

        // the reason we intercept this here is that while the type may be dynamically level 2 immutable, the user
        // may still try to call a modifying method. This will cause an error, however, it would also change the modification status
        // of the field, which is not good.
        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (MultiLevel.isE2Immutable(immutable)) {
            log(NOT_MODIFIED, "Field {} is @NotModified, since it is @Final and @E2Immutable", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
            return DONE;
        }

        if (fieldSummariesNotYetSet) return DELAYS;

        // we only consider methods, not constructors!
        boolean allContentModificationsDefined = allMethodsAndConstructors.stream().allMatch(m ->
                !m.methodLevelData().fieldSummaries.isSet(fieldInfo) ||
                        m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) < Level.TRUE ||
                        m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) != Level.DELAY);

        if (allContentModificationsDefined) {
            boolean modified = fieldCanBeWrittenFromOutsideThisType ||
                    allMethodsAndConstructors.stream()
                            .filter(m -> m.methodLevelData().fieldSummaries.isSet(fieldInfo))
                            .filter(m -> m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) >= Level.TRUE)
                            .anyMatch(m -> m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) == Level.TRUE);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, modified);
            log(NOT_MODIFIED, "Mark field {} as {}", fieldInfo.fullyQualifiedName(), modified ? "@Modified" : "@NotModified");
            return DONE;
        }
        if (Logger.isLogEnabled(DELAYED)) {
            log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or defined",
                    fieldInfo.fullyQualifiedName());
            allMethodsAndConstructors.stream().filter(m ->
                    m.methodLevelData().fieldSummaries.isSet(fieldInfo) &&
                            m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.READ) == Level.TRUE &&
                            m.methodLevelData().fieldSummaries.get(fieldInfo).getProperty(VariableProperty.MODIFIED) == Level.DELAY)
                    .forEach(m -> log(DELAYED, "... method {} reads the field, but we're still waiting", m.methodInfo.name));
        }
        return DELAYS;
    }

    /*
    TODO at some point this should go beyond functional interfaces.

    TODO at some point this should go beyond the initializer; it should look at all assignments
     */
    private AnalysisStatus analyseNotModifiedFunctionalInterface() {
        if (sam != null) {
            int modified = sam.methodAnalysis.getProperty(VariableProperty.MODIFIED);

            log(NOT_MODIFIED, "Field {} of functional interface type: copying MODIFIED {} from SAM", fieldInfo.fullyQualifiedName(), modified);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, modified);
            return DONE;
        }
        log(NOT_MODIFIED, "Field {} of functional interface type: undeclared, so not modified", fieldInfo.fullyQualifiedName());
        fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.FALSE);
        return DONE;
    }

    @Override
    protected Value getVariableValue(Variable variable) {
        FieldReference fieldReference = (FieldReference) variable;
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysers().get(fieldReference.fieldInfo).fieldAnalysis;
        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return NO_VALUE;
        ObjectFlow objectFlow = fieldAnalysis.getObjectFlow();
        if (effectivelyFinal == Level.FALSE) {
            return new VariableValue(variable, objectFlow, true);
        }
        Value effectivelyFinalValue = fieldAnalysis.getEffectivelyFinalValue();
        return Objects.requireNonNullElseGet(effectivelyFinalValue,
                () -> new VariableValue(variable, objectFlow, false));
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        // before we check, we copy the properties into annotations
        fieldAnalysis.transferPropertiesToAnnotations(e2);

        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        // TODO check the correct field name in @Linked(to="")
        check(Linked.class, e2.linked.get());
        check(NotModified.class, e2.notModified.get());
        check(NotNull.class, e2.notNull.get());
        check(Final.class, e2.effectivelyFinal.get());

        // dynamic type annotations
        check(E1Immutable.class, e2.e1Immutable.get());
        check(E2Immutable.class, e2.e2Immutable.get());
        check(Container.class, e2.container.get());
        check(E1Container.class, e2.e1Container.get());
        check(E2Container.class, e2.e2Container.get());

        // checks for dynamic properties of functional interface types
        check(NotModified1.class, e2.notModified1.get());

        // opposites
        check(org.e2immu.annotation.Variable.class, e2.variableField.get());
        check(Modified.class, e2.modified.get());
        check(Nullable.class, e2.nullable.get());

        CheckConstant.checkConstantForFields(messages, fieldInfo, fieldAnalysis);
        CheckSize.checkSizeForFields(messages, fieldInfo, fieldAnalysis);
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(fieldAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(new Location(fieldInfo),
                    mustBeAbsent ? Message.ANNOTATION_UNEXPECTEDLY_PRESENT : Message.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    public Stream<Message> getMessageStream() {
        return messages.getMessageStream();
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager) {
            super(iteration, conditionManager);
        }

        @Override
        public boolean isNotNull0(Value value) {
            // will crash for variable values, but that should be fine
            return MultiLevel.isEffectivelyNotNull(value.getProperty(this, VariableProperty.NOT_NULL));
        }

        @Override
        public TypeAnalyser getCurrentType() {
            return myTypeAnalyser;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public FieldAnalyser getCurrentField() {
            return FieldAnalyser.this;
        }

        @Override
        public MethodAnalysis getCurrentMethodAnalysis() {
            return null;
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return null;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return null;
        }

        @Override
        public Location getLocation() {
            return new Location(fieldInfo);
        }

        @Override
        public Location getLocation(Expression expression) {
            return new Location(fieldInfo, expression);
        }

        // rest will be more or less the same as for Methods

        // used in short-circuiting, inline conditional, and lambda

        @Override
        public EvaluationContext child(Value condition) {
            return FieldAnalyser.this.new EvaluationContextImpl(iteration, conditionManager.addCondition(condition));
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable) {
            return currentValue(variable).getObjectFlow();
        }

        @Override
        public int getProperty(Value value, VariableProperty variableProperty) {
            if (value instanceof VariableValue variableValue) {
                if (variableValue.variable instanceof FieldReference fieldReference) {
                    return getFieldAnalysis(fieldReference.fieldInfo).getProperty(variableProperty);
                }
                if (variableValue.variable instanceof This thisVariable) {
                    return getTypeAnalysis(thisVariable.typeInfo).getProperty(variableProperty);
                }
                if (variableValue.variable instanceof ParameterInfo parameterInfo) {
                    return getParameterAnalysis(parameterInfo).getProperty(variableProperty);
                }
                throw new UnsupportedOperationException("?? variable of " + variableValue.variable.getClass());
            }
            return value.getPropertyOutsideContext(variableProperty);
        }

        @Override
        public Value currentValue(Variable variable) {
            if (variable instanceof FieldReference) {
                return getVariableValue(variable);
            }
            if (variable instanceof This || variable instanceof DependentVariable) {
                return myTypeAnalyser.getVariableValue(variable);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return internalObjectFlows.stream();
        }

        @Override
        public Value currentValue(String variableName) {
            FieldInfo fieldInfo = primaryType.getFieldByName(variableName, true);
            return getVariableValue(new FieldReference(fieldInfo, new This(fieldInfo.owner)));
        }

    }

}
