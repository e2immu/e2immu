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
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.config.FieldAnalyserVisitor;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.Logger;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.model.expression.EmptyExpression.NO_VALUE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class FieldAnalyser extends AbstractAnalyser {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FieldAnalyser.class);

    // analyser components, constants are used in tests
    public static final String COMPUTE_IMPLICITLY_IMMUTABLE_DATA_TYPE = "computeImplicitlyImmutableDataType";
    public static final String EVALUATE_INITIALISER = "evaluateInitialiser";
    public static final String ANALYSE_FINAL = "analyseFinal";
    public static final String ANALYSE_FINAL_VALUE = "analyseFinalValue";
    public static final String ANALYSE_IMMUTABLE = "analyseDynamicTypeAnnotation:IMMUTABLE";
    public static final String ANALYSE_NOT_NULL = "analyseNotNull";
    public static final String ANALYSE_MODIFIED = "analyseModified";
    public static final String ANALYSE_NOT_MODIFIED_1 = "analyseNotModified1";
    public static final String ANALYSE_LINKED = "analyseLinked";
    public static final String FIELD_ERRORS = "fieldErrors";
    public static final String ANALYSE_ASSIGNMENTS = "allAssignmentsHaveBeenSet";
    private static final String ANALYSE_LINKS_HAVE_BEEN_ESTABLISHED = "allLinksHaveBeenEstablished";

    public final TypeInfo primaryType;
    public final FieldInfo fieldInfo;
    public final FieldInspection fieldInspection;
    public final FieldAnalysisImpl.Builder fieldAnalysis;
    public final MethodAnalyser sam;
    private final boolean fieldCanBeWrittenFromOutsideThisType;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final CheckConstant checkConstant;
    private final CheckLinks checkLinks;
    private final boolean haveInitialiser;

    // set at initialisation time
    private List<MethodAnalyser> allMethodsAndConstructors;
    private List<MethodAnalyser> myMethodsAndConstructors;
    private TypeAnalyser myTypeAnalyser;

    private record SharedState(int iteration, EvaluationContext closure) {
    }

    public FieldAnalyser(FieldInfo fieldInfo,
                         TypeInfo primaryType,
                         TypeAnalysis ownerTypeAnalysis,
                         MethodAnalyser sam,
                         AnalyserContext analyserContext) {
        super("Field " + fieldInfo.name, analyserContext);
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());
        this.checkLinks = new CheckLinks(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());

        this.fieldInfo = fieldInfo;
        fieldInspection = fieldInfo.fieldInspection.get();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, fieldInfo, ownerTypeAnalysis);
        this.primaryType = primaryType;
        this.sam = sam;
        fieldCanBeWrittenFromOutsideThisType = fieldInfo.owner.isRecord() || !fieldInfo.isPrivate() && !fieldInfo.isExplicitlyFinal();
        haveInitialiser = fieldInspection.fieldInitialiserIsSet() && fieldInspection.getFieldInitialiser().initialiser() != EmptyExpression.EMPTY_EXPRESSION;

        analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                .add(COMPUTE_IMPLICITLY_IMMUTABLE_DATA_TYPE, (iteration) -> computeImplicitlyImmutableDataType())
                .add(EVALUATE_INITIALISER, this::evaluateInitialiser)
                .add(ANALYSE_FINAL, this::analyseFinal)
                .add(ANALYSE_ASSIGNMENTS, (iteration) -> allAssignmentsHaveBeenSet())
                .add(ANALYSE_LINKS_HAVE_BEEN_ESTABLISHED, (iteration) -> allLinksHaveBeenEstablished())
                .add(ANALYSE_IMMUTABLE, this::analyseImmutable)
                .add(ANALYSE_MODIFIED, (iteration) -> analyseModified())
                .add(ANALYSE_FINAL_VALUE, (iteration) -> analyseFinalValue())
                .add(ANALYSE_NOT_NULL, this::analyseNotNull)
                .add(ANALYSE_NOT_MODIFIED_1, (iteration) -> analyseNotModified1())
                .add(ANALYSE_LINKED, (iteration) -> analyseLinked())
                .add(FIELD_ERRORS, (iteration) -> fieldErrors())
                .build();
    }

    @Override
    public AnalyserComponents<String, SharedState> getAnalyserComponents() {
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

        analyserContext.methodAnalyserStream().forEach(analyser -> {
            allMethodsAndConstructors.add(analyser);
            if (analyser.methodInfo.typeInfo == fieldInfo.owner) {
                myMethodsAndConstructors.add(analyser);
            }
        });
        myTypeAnalyser = analyserContext.getTypeAnalyser(fieldInfo.owner);
        this.allMethodsAndConstructors = allMethodsAndConstructors.build();
        this.myMethodsAndConstructors = myMethodsAndConstructors.build();
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing field {}", fieldInfo.fullyQualifiedName());

        // analyser visitors
        try {
            SharedState sharedState = new SharedState(iteration, closure);
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);

            List<FieldAnalyserVisitor> visitors = analyserContext.getConfiguration().debugConfiguration.afterFieldAnalyserVisitors;
            if (!visitors.isEmpty()) {
                EvaluationContext evaluationContext = new EvaluationContextImpl(iteration,
                        ConditionManager.initialConditionManager(analyserContext.getPrimitives()), closure);
                for (FieldAnalyserVisitor fieldAnalyserVisitor : visitors) {
                    fieldAnalyserVisitor.visit(new FieldAnalyserVisitor.Data(iteration, evaluationContext,
                            fieldInfo, fieldAnalysis, analyserComponents.getStatusesAsMap()));
                }
            }
            return analysisStatus;
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", fieldInfo.fullyQualifiedName());
            throw rte;
        }
    }

    public void write() {
        // before we check, we copy the properties into annotations
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        fieldAnalysis.transferPropertiesToAnnotations(analyserContext, e2);
    }

    private AnalysisStatus evaluateInitialiser(SharedState sharedState) {
        if (fieldInspection.fieldInitialiserIsSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
            if (fieldInitialiser.initialiser() != EmptyExpression.EMPTY_EXPRESSION) {
                EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration(),
                        ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure());
                EvaluationResult evaluationResult = fieldInitialiser.initialiser().evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                Expression initialiserValue = evaluationResult.value();
                if (initialiserValue != NO_VALUE) {
                    fieldAnalysis.initialValue.set(initialiserValue);
                }
                AnalysisStatus resultOfObjectFlow = makeInternalObjectFlowsPermanent(evaluationResult);
                log(FINAL, "Set initialiser of field {} to {}", fieldInfo.fullyQualifiedName(), evaluationResult.value());
                return resultOfObjectFlow.combine(initialiserValue == NO_VALUE ? DELAYS : DONE);
            }
        }
        fieldAnalysis.initialValue.set(ConstantExpression.nullValue(analyserContext.getPrimitives(), fieldInfo.type.bestTypeInfo()));
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
        fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, Level.fromBool(allParametersNotModified));
        return DONE;
    }

    private AnalysisStatus analyseNotNull(SharedState sharedState) {
        if (fieldAnalysis.getProperty(VariableProperty.NOT_NULL) != Level.DELAY) return DONE;

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
        if (!fieldAnalysis.values.isSet()) return DELAYS;

        boolean allDelaysResolved = fieldAnalysis.allLinksHaveBeenEstablished.isSet();
        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure);

        int worstOverValues = fieldAnalysis.values.get().stream()
                .mapToInt(expression -> evaluationContext.getProperty(expression, VariableProperty.NOT_NULL))
                .min().orElse(MultiLevel.NULLABLE);
        int valueFromAssignment = worstOverValues == Level.DELAY && allDelaysResolved ? MultiLevel.NULLABLE : worstOverValues;
        if (valueFromAssignment == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int bestOverContext = allMethodsAndConstructors.stream()
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                .mapToInt(vi -> vi.getProperty(VariableProperty.NOT_NULL)).max().orElse(Level.DELAY);
        int valueFromContext = bestOverContext == Level.DELAY && allDelaysResolved ? MultiLevel.NULLABLE : bestOverContext;
        if (valueFromContext == Level.DELAY) {
            log(DELAYED, "Delaying property @NotNull on {}, context property delay", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int finalNotNullValue = MultiLevel.bestNotNull(valueFromAssignment, valueFromContext);
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fieldInfo.fullyQualifiedName(), finalNotNullValue);

        fieldAnalysis.setProperty(VariableProperty.NOT_NULL, finalNotNullValue);
        return DONE;
    }

    private AnalysisStatus fieldErrors() {
        assert !fieldAnalysis.fieldError.isSet();

        if (fieldInspection.getModifiers().contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = allMethodsAndConstructors.stream()
                        .filter(m -> !(m.methodInfo.isConstructor && m.methodInfo.typeInfo == fieldInfo.owner)) // not my own constructors
                        .anyMatch(this::isReadInMethod);
                fieldAnalysis.fieldError.set(!readInMethods);
                if (!readInMethods) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.PRIVATE_FIELD_NOT_READ));
                }
                return DONE;
            }
        } else {
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.FALSE) {
                // only react once we're certain the variable is not effectively final
                // error, unless we're in a record
                boolean record = fieldInfo.owner.isRecord();
                fieldAnalysis.fieldError.set(!record);
                if (!record) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.NON_PRIVATE_FIELD_NOT_FINAL));
                } // else: nested private types can have fields the way they like it
                return DONE;
            } else if (effectivelyFinal == Level.DELAY) {
                log(DELAYED, "Not yet ready to decide on non-private non-final");
                return DELAYS;
            }
        }
        // not for me
        return DONE;
    }

    private boolean isReadInMethod(MethodAnalyser methodAnalyser) {
        return methodAnalyser.getFieldAsVariableStream(fieldInfo, true).anyMatch(VariableInfo::isRead);
    }

    private AnalysisStatus analyseImmutable(SharedState sharedState) {
        if (fieldInfo.type.isFunctionalInterface()) return DONE; // not for me
        // not an assert, because the value is not directly determined by the actual property
        if (fieldAnalysis.getProperty(VariableProperty.IMMUTABLE) != Level.DELAY) return DONE;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying {} on {} until we know about @Final", VariableProperty.IMMUTABLE, fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisType) {
            log(NOT_NULL, "Field {} cannot be immutable: it is not @Final, and it can be assigned to from outside this class",
                    fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.IMMUTABLE, MultiLevel.MUTABLE);
            return DONE;
        }

        int staticallyImmutable = fieldInfo.type.getProperty(analyserContext, VariableProperty.IMMUTABLE);
        if (MultiLevel.isE2Immutable(staticallyImmutable)) {
            log(E2IMMUTABLE, "Field {} is statically @E2Immutable", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.IMMUTABLE, staticallyImmutable);
            return DONE;
        }

        if (!fieldAnalysis.values.isSet()) {
            return DELAYS;
        }

        boolean allDelaysResolved = fieldAnalysis.allLinksHaveBeenEstablished.isSet();

        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure);
        int valueFromAssignment = fieldAnalysis.values.get().stream()
                .mapToInt(expression -> evaluationContext.getProperty(expression, VariableProperty.IMMUTABLE))
                .min()
                .orElse(MultiLevel.MUTABLE);
        int breakDelays = valueFromAssignment == Level.DELAY && allDelaysResolved ?
                VariableProperty.IMMUTABLE.falseValue : valueFromAssignment;
        // compute the value of the assignments
        if (breakDelays == Level.DELAY) {
            log(DELAYED, "Delaying immutable on field {}, initialiser delayed", fieldInfo.fullyQualifiedName());
            return DELAYS; // delay
        }
        log(DYNAMIC, "Set immutable on field {} to value {}", fieldInfo.fullyQualifiedName(), breakDelays);
        fieldAnalysis.setProperty(VariableProperty.IMMUTABLE, breakDelays);
        return DONE;
    }

    private AnalysisStatus allAssignmentsHaveBeenSet() {
        assert !fieldAnalysis.values.isSet();

        List<Expression> values = new LinkedList<>();
        if (haveInitialiser) {
            if (fieldAnalysis.getInitialValue() == NO_VALUE) {
                log(DELAYED, "Delaying consistent value for field " + fieldInfo.fullyQualifiedName());
                return DELAYS;
            }
            values.add(fieldAnalysis.getInitialValue());
        }
        // collect all the other values, bail out when delays
        if (!(fieldInfo.isExplicitlyFinal() && haveInitialiser)) {
            for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
                for (VariableInfo vi : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                    if (vi.isAssigned()) {
                        Expression value = vi.getValue();
                        if (value != NO_VALUE) {
                            values.add(value);
                        } else {
                            log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                            return DELAYS;
                        }
                    }
                }
            }
        }
        fieldAnalysis.values.set(ImmutableList.copyOf(values));
        return DONE;
    }

    private AnalysisStatus allLinksHaveBeenEstablished() {
        assert !fieldAnalysis.allLinksHaveBeenEstablished.isSet();
        boolean res = allMethodsAndConstructors.stream()
                .filter(m -> !m.getFieldAsVariable(fieldInfo, false).isEmpty())
                .allMatch(m -> m.methodLevelData().linksHaveBeenEstablished.isSet());
        if (res) {
            fieldAnalysis.allLinksHaveBeenEstablished.set();
            return DONE;
        }
        return DELAYS;
    }

    /*
    Nothing gets set before we know (a) the initialiser, if it is there, (b) values in the constructor, and (c)
    the decision on @Final.

    Even if there is an initialiser and the field is explicitly final (final modifier), that value does NOT
    necessarily become the final value. Consider Modification_0, where the field is assigned a "new HashSet()"
    but where a modifying method (add to the set) changes this "new" object into a normal, modified instance.

    Only when the field is @NotModified and not exposed, or @E2Immutable, the initialiser remains.
    Because @Modified cannot be computed in the first iteration, the effectively final value cannot (generally)
    be computed in the first iteration either (it can if the type is immediately known to be @E2Immutable, such as for primitives)
     */
    private AnalysisStatus analyseFinalValue() {
        if (fieldAnalysis.effectivelyFinalValue.isSet()) {
            return DONE;
        }
        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE) {
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.FALSE);
            return DONE;
        }
        if (!fieldAnalysis.values.isSet()) {
            log(DELAYED, "Delaying, have no values yet for field " + fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY) {
            log(DELAYED, "Waiting with effectively final value  until decision on @E2Immutable for {}", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        boolean downgradeFromNewInstanceWithConstructor = !MultiLevel.isE2Immutable(immutable);

        // compute and set the combined value

        if (!fieldAnalysis.internalObjectFlows.isSet()) {
            log(DELAYED, "Delaying effectively final value because internal object flows not yet known, {}", fieldInfo.fullyQualifiedName());
            //      return DELAYS; TODO see TestFieldNotRead, but for now waiting until we sort out internalObjectFlows
        }
        Expression effectivelyFinalValue = determineEffectivelyFinalValue(fieldAnalysis.values.get(), downgradeFromNewInstanceWithConstructor);

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

        // check constant, but before we set the effectively final value
        Boolean recursivelyConstant;
        if (downgradeFromNewInstanceWithConstructor) recursivelyConstant = false;
        else recursivelyConstant = recursivelyConstant(effectivelyFinalValue);
        if (recursivelyConstant == null) {
            log(DELAYED, "Delaying effectively final value because of recursively constant computation on value {} of {}",
                    fieldInfo.fullyQualifiedName(), effectivelyFinalValue);
            return DELAYS;
        }

        fieldAnalysis.effectivelyFinalValue.set(effectivelyFinalValue);

        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        if (recursivelyConstant) {
            // directly adding the annotation; it will not be used for inspection
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, effectivelyFinalValue);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.TRUE);
            log(CONSTANT, "Added @Constant annotation on field {}", fieldInfo.fullyQualifiedName());
        } else {
            log(CONSTANT, "Marked that field {} cannot be @Constant", fieldInfo.fullyQualifiedName());
            fieldAnalysis.annotations.put(e2.constant, false);
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.FALSE);
        }

        log(CONSTANT, "Setting initial value of effectively final of field {} to {}",
                fieldInfo.fullyQualifiedName(), effectivelyFinalValue);

        return DONE;
    }

    /*
    we already know that this type is @E2Immutable, but does it contain only constants?
     */
    private Boolean recursivelyConstant(Expression effectivelyFinalValue) {
        if (effectivelyFinalValue.isConstant()) return true;
        if (effectivelyFinalValue instanceof NewObject newObject) {
            if (newObject.constructor() == null) return false;
            for (Expression parameter : newObject.getParameterExpressions()) {
                if (!parameter.isConstant()) {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(0, // IMPROVE
                            ConditionManager.initialConditionManager(fieldAnalysis.primitives), null);
                    int immutable = evaluationContext.getProperty(parameter, VariableProperty.IMMUTABLE);
                    if (immutable == Level.DELAY) return null;
                    if (!MultiLevel.isEffectivelyNotNull(immutable)) return false;
                    Boolean recursively = recursivelyConstant(parameter);
                    if (recursively == null) return null;
                    if (!recursively) return false;
                }
            }
            return true;
        }
        return false;
    }

    private Expression determineEffectivelyFinalValue(List<Expression> values, boolean downgradeFromNewInstanceWithConstructor) {
        // suppose there are 2 constructors, and the field gets exactly the same value...
        Set<Expression> set = new HashSet<>(values);
        if (set.size() == 1) {
            Expression expression = values.get(0);
            BooleanConstant TRUE = new BooleanConstant(analyserContext.getPrimitives(), true);
            if (expression instanceof NewObject newObject) {
                // now the state of the new object may survive if there are no modifying methods called,
                // but that's too early to know now
                fieldAnalysis.setStateOfEffectivelyFinalValue(newObject.state());
                return downgradeFromNewInstanceWithConstructor ?
                        newObject.copyAfterModifyingMethodOnConstructor(TRUE) : newObject.copyWithNewState(TRUE);
            }
            fieldAnalysis.setStateOfEffectivelyFinalValue(TRUE);
            return expression;
        }
        This thisVariable = new This(analyserContext, fieldInfo.owner);
        FieldReference fieldReference = new FieldReference(analyserContext,
                fieldInfo, fieldInfo.isStatic() ? null : thisVariable);

        return new VariableExpression(fieldReference, ObjectFlow.NO_FLOW);
    }

    private AnalysisStatus analyseLinked() {
        assert !fieldAnalysis.linkedVariables.isSet();

        // we ONLY look at the linked variables of fields that have been assigned to
        boolean allDefined = allMethodsAndConstructors.stream()
                .allMatch(m -> {
                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, false);
                    return variableInfoList.isEmpty() ||
                            variableInfoList.stream().noneMatch(VariableInfo::isAssigned) ||
                            variableInfoList.stream().allMatch(VariableInfo::linkedVariablesIsSet);
                });
        if (!allDefined) {
            if (Logger.isLogEnabled(DELAYED)) {
                log(DELAYED, "LinkedVariables not yet set for {} in methods: [{}]",
                        fieldInfo.fullyQualifiedName(),
                        allMethodsAndConstructors.stream()
                                .filter(m -> {
                                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, false);
                                    return !variableInfoList.isEmpty() &&
                                            variableInfoList.stream().allMatch(VariableInfo::isAssigned) &&
                                            !variableInfoList.stream().allMatch(VariableInfo::linkedVariablesIsSet);
                                })
                                .map(m -> m.methodInfo.name).collect(Collectors.joining(", ")));
            }
            return DELAYS;
        }

        Set<Variable> linkedVariables = allMethodsAndConstructors.stream()
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                .filter(VariableInfo::linkedVariablesIsSet)
                .flatMap(vi -> vi.getLinkedVariables().variables().stream())
                .filter(v -> !(v instanceof LocalVariableReference)) // especially local variable copies of the field itself
                .collect(Collectors.toSet());
        fieldAnalysis.linkedVariables.set(new LinkedVariables(linkedVariables));
        log(LINKED_VARIABLES, "FA: Set links of {} to [{}]", fieldInfo.fullyQualifiedName(), Variable.fullyQualifiedName(linkedVariables));

        // explicitly adding the annotation here; it will not be inspected.
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        AnnotationExpression linkAnnotation = checkLinks.createLinkAnnotation(e2, linkedVariables);
        fieldAnalysis.annotations.put(linkAnnotation, !linkedVariables.isEmpty());
        return DONE;
    }

    private AnalysisStatus analyseFinal(SharedState sharedState) {
        assert fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.DELAY;
        assert sharedState.iteration == 0;

        if (fieldInfo.isExplicitlyFinal()) {
            fieldAnalysis.setProperty(VariableProperty.FINAL, Level.TRUE);
            return DONE;
        }

        boolean isFinal;
        if (fieldCanBeWrittenFromOutsideThisType) {
            // this means other types can write to the field... not final by definition
            isFinal = false;
        } else {
            isFinal = allMethodsAndConstructors.stream()
                    .filter(m -> m.methodInfo.methodResolution.get().partOfConstruction().accessibleFromTheOutside())
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                    .noneMatch(VariableInfo::isAssigned);
        }
        fieldAnalysis.setProperty(VariableProperty.FINAL, Level.fromBool(isFinal));
        if (isFinal && fieldInfo.type.isRecordType()) {
            messages.add(Message.newMessage(new Location(fieldInfo), Message.EFFECTIVELY_FINAL_FIELD_NOT_RECORD));
        }
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final", fieldInfo.fullyQualifiedName());
        return DONE;
    }

    private AnalysisStatus analyseModified() {
        assert fieldAnalysis.getProperty(VariableProperty.MODIFIED) == Level.DELAY;

        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return DELAYS;
        if (effectivelyFinal == Level.FALSE) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.TRUE);
            log(NOT_MODIFIED, "Field {} is @Modified, because it is @Variable", fieldInfo.fullyQualifiedName());
            return DONE;
        }

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

        // we only consider methods, not constructors!
        boolean allContentModificationsDefined = allMethodsAndConstructors.stream()
                .filter(m -> !m.methodInfo.isConstructor)
                .allMatch(m -> {
                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, true);
                    return variableInfoList.isEmpty() ||
                            variableInfoList.stream().noneMatch(VariableInfo::isRead) ||
                            m.methodLevelData().linksHaveBeenEstablished.isSet();
                });

        if (allContentModificationsDefined) {
            boolean modified = fieldCanBeWrittenFromOutsideThisType ||
                    allMethodsAndConstructors.stream()
                            .filter(m -> !m.methodInfo.isConstructor)
                            .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                            .filter(VariableInfo::isRead)
                            .anyMatch(vi -> vi.getProperty(VariableProperty.MODIFIED) == Level.TRUE);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED, Level.fromBool(modified));
            log(NOT_MODIFIED, "Mark field {} as {}", fieldInfo.fullyQualifiedName(), modified ? "@Modified" : "@NotModified");
            return DONE;
        }
        if (Logger.isLogEnabled(DELAYED)) {
            log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or links",
                    fieldInfo.fullyQualifiedName());
            allMethodsAndConstructors.stream().filter(m -> !m.methodInfo.isConstructor &&
                    !m.getFieldAsVariable(fieldInfo, true).isEmpty() &&
                    m.getFieldAsVariable(fieldInfo, true).stream().anyMatch(VariableInfo::isRead) &&
                    !m.methodLevelData().linksHaveBeenEstablished.isSet())
                    .forEach(m -> log(DELAYED, "... method {} reads the field, but we're still waiting on links to be established", m.methodInfo.name));
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
    protected Expression getVariableValue(Variable variable) {
        FieldReference fieldReference = (FieldReference) variable;
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalyser(fieldReference.fieldInfo).fieldAnalysis;
        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return NO_VALUE;
        ObjectFlow objectFlow = fieldAnalysis.getObjectFlow();
        if (effectivelyFinal == Level.FALSE) {
            return new VariableExpression(variable, objectFlow);
        }
        Expression effectivelyFinalValue = fieldAnalysis.getEffectivelyFinalValue();
        return Objects.requireNonNullElseGet(effectivelyFinalValue,
                () -> new VariableExpression(variable, objectFlow));
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        check(NotModified.class, e2.notModified);
        check(NotNull.class, e2.notNull);
        check(Final.class, e2.effectivelyFinal);

        // dynamic type annotations
        check(E1Immutable.class, e2.e1Immutable);
        check(E2Immutable.class, e2.e2Immutable);
        check(Container.class, e2.container);
        check(E1Container.class, e2.e1Container);
        check(E2Container.class, e2.e2Container);

        // checks for dynamic properties of functional interface types
        check(NotModified1.class, e2.notModified1);

        // opposites
        check(org.e2immu.annotation.Variable.class, e2.variableField);
        check(Modified.class, e2.modified);
        check(Nullable.class, e2.nullable);

        checkLinks.checkLinksForFields(messages, fieldInfo, fieldAnalysis);
        checkConstant.checkConstantForFields(messages, fieldInfo, fieldAnalysis);
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

        private EvaluationContextImpl(int iteration, ConditionManager conditionManager, EvaluationContext closure) {
            super(iteration, conditionManager, closure);
        }

        @Override
        public TypeInfo getCurrentType() {
            return fieldInfo.owner;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
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
        public EvaluationContext child(Expression condition) {
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition);
            return FieldAnalyser.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public ObjectFlow getObjectFlow(Variable variable, int statementTime) {
            return currentValue(variable, statementTime, true).getObjectFlow();
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty) {
            if (value instanceof VariableExpression variableValue) {
                Variable variable = variableValue.variable();
                return getProperty(variable, variableProperty);
            }
            return value.getProperty(this, variableProperty);
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            if (variable instanceof FieldReference fieldReference) {
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(variableProperty);
            }
            if (variable instanceof This thisVariable) {
                return getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(variableProperty);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(variableProperty);
            }
            throw new UnsupportedOperationException("?? variable of " + variable.getClass());
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, boolean isNotAssignmentTarget) {
            if (variable instanceof FieldReference) {
                return getVariableValue(variable);
            }
            if (variable instanceof This) {
                return myTypeAnalyser.getVariableValue(variable);
            }
            if (variable instanceof ParameterInfo) {
                /*
                 the parameter must belong to the closure somewhere (otherwise you can't have parameters in field expressions,
                 see test SubTypes_5
                 */
                assert closure != null;
                return closure.currentValue(variable, statementTime, isNotAssignmentTarget);
            }

            throw new UnsupportedOperationException("Variable of " + variable.getClass() + " not implemented here");
        }

        @Override
        public Stream<ObjectFlow> getInternalObjectFlows() {
            return internalObjectFlows.stream();
        }

    }

}
