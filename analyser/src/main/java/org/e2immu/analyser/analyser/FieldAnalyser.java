/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckFinalNotModified;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
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
    private final boolean fieldCanBeWrittenFromOutsideThisPrimaryType;
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
        this.checkLinks = new CheckLinks(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());

        this.fieldInfo = fieldInfo;
        fieldInspection = fieldInfo.fieldInspection.get();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, fieldInfo, ownerTypeAnalysis);
        this.primaryType = primaryType;
        this.sam = sam;
        fieldCanBeWrittenFromOutsideThisPrimaryType = !fieldInfo.isPrivate() &&
                !fieldInfo.isExplicitlyFinal() && !fieldInfo.owner.isPrivateOrEnclosingIsPrivate();
        haveInitialiser = fieldInspection.fieldInitialiserIsSet() && fieldInspection.getFieldInitialiser().initialiser() != EmptyExpression.EMPTY_EXPRESSION;

        analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                .add(COMPUTE_IMPLICITLY_IMMUTABLE_DATA_TYPE, sharedState -> computeImplicitlyImmutableDataType())
                .add(EVALUATE_INITIALISER, this::evaluateInitialiser)
                .add(ANALYSE_FINAL, this::analyseFinal)
                .add(ANALYSE_ASSIGNMENTS, sharedState -> allAssignmentsHaveBeenSet())
                .add(ANALYSE_LINKS_HAVE_BEEN_ESTABLISHED, sharedState -> allLinksHaveBeenEstablished())
                .add(ANALYSE_IMMUTABLE, this::analyseImmutable)
                .add(ANALYSE_MODIFIED, sharedState -> analyseModified())
                .add(ANALYSE_FINAL_VALUE, sharedState -> analyseFinalValue())
                .add(ANALYSE_NOT_NULL, this::analyseNotNull)
                .add(ANALYSE_NOT_MODIFIED_1, sharedState -> analyseNotModified1())
                .add(ANALYSE_LINKED, sharedState -> analyseLinked())
                .add("analyseLinked1", sharedState -> analyseLinked1())
                .add("analysePropagateMo", sharedState -> analysePropagateModification())
                .add(FIELD_ERRORS, sharedState -> fieldErrors())
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAnalyser that = (FieldAnalyser) o;
        return fieldInfo.equals(that.fieldInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldInfo);
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
        List<MethodAnalyser> allMethodsAndConstructors = new LinkedList<>();
        List<MethodAnalyser> myMethodsAndConstructors = new LinkedList<>();

        messages.addAll(fieldAnalysis.fromAnnotationsIntoProperties(VariableProperty.EXTERNAL_NOT_NULL,
                AnalyserIdentification.FIELD, false,
                fieldInfo.fieldInspection.get().getAnnotations(), analyserContext.getE2ImmuAnnotationExpressions()));

        analyserContext.methodAnalyserStream().forEach(analyser -> {
            allMethodsAndConstructors.add(analyser);
            if (analyser.methodInfo.typeInfo == fieldInfo.owner) {
                myMethodsAndConstructors.add(analyser);
            }
        });
        myTypeAnalyser = analyserContext.getTypeAnalyser(fieldInfo.owner);
        this.allMethodsAndConstructors = List.copyOf(allMethodsAndConstructors);
        this.myMethodsAndConstructors = List.copyOf(myMethodsAndConstructors);
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
                            fieldInfo, fieldAnalysis, this::getMessageStream, analyserComponents.getStatusesAsMap()));
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
                if (!evaluationResult.someValueWasDelayed()) {
                    fieldAnalysis.initialValue.set(initialiserValue);
                }
                log(FINAL, "Set initialiser of field {} to {}", fieldInfo.fullyQualifiedName(), evaluationResult.value());
                return evaluationResult.someValueWasDelayed() ? DELAYS : DONE;
            }
        }
        fieldAnalysis.initialValue.set(ConstantExpression.nullValue(analyserContext.getPrimitives(), fieldInfo.type.bestTypeInfo()));
        return DONE;
    }


    private AnalysisStatus computeImplicitlyImmutableDataType() {
        assert !fieldAnalysis.isOfImplicitlyImmutableDataTypeIsSet();
        if (myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes() == null) return DELAYS;
        boolean implicit = myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes().contains(fieldInfo.type);
        fieldAnalysis.setImplicitlyImmutableDataType(implicit);
        return DONE;
    }

    private AnalysisStatus analyseNotModified1() {
        if (!fieldInfo.type.isFunctionalInterface() || sam == null) return DONE; // not for me
        assert fieldAnalysis.getProperty(VariableProperty.NOT_MODIFIED_1) == Level.DELAY;

        boolean someParameterModificationUnknown = sam.getParameterAnalysers().stream().anyMatch(p ->
                p.parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE) == Level.DELAY);
        if (someParameterModificationUnknown) {
            log(NOT_MODIFIED, "Delaying @NotModified1 on {}, some parameters have no @Modified status yet",
                    fieldInfo.fullyQualifiedName());
        }
        boolean allParametersNotModified = sam.getParameterAnalysers().stream().allMatch(p ->
                p.parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE) == Level.FALSE);

        log(NOT_MODIFIED, "Set @NotModified1 on {} to {}", fieldInfo.fullyQualifiedName(), allParametersNotModified);
        fieldAnalysis.setProperty(VariableProperty.NOT_MODIFIED_1, Level.fromBool(allParametersNotModified));
        return DONE;
    }

    private static final IntBinaryOperator MAX = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY ? Level.DELAY :
            Math.max(i1, i2);

    /*
    not null has been intentionally decoupled from value and linked values

    for methods/constructors with assignment to the variable, we wait for linked variables to be set AND for not null delays.
    for methods which only read, we only wait for not-null delays to be resolved.
     */
    private AnalysisStatus analyseNotNull(SharedState sharedState) {
        if (fieldAnalysis.getProperty(VariableProperty.EXTERNAL_NOT_NULL) != Level.DELAY) return DONE;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it be assigned to from outside this primary type",
                    fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.NULLABLE);
            return DONE;
        }

        int finalNotNullValue;

        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure);

        boolean notOnlyAssignedToParameters = fieldAnalysis.getValues().stream()
                .anyMatch(e -> {
                    VariableExpression ve;
                    return !e.isDelayed(evaluationContext) &&
                            ((ve = e.asInstanceOf(VariableExpression.class)) == null ||
                                    !(ve.variable() instanceof ParameterInfo));
                });

        boolean onlyAssignedToParameters = fieldAnalysis.getValues().stream()
                // parameters can be influenced by context not null, all the rest cannot
                .allMatch(e -> {
                    VariableExpression ve;
                    return (ve = e.asInstanceOf(VariableExpression.class)) != null &&
                            ve.variable() instanceof ParameterInfo;
                });

        if (!notOnlyAssignedToParameters && !onlyAssignedToParameters) {
            log(DELAYED, "Delaying @NotNull on {}, waiting for values", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        if (onlyAssignedToParameters) {
            int bestOverContext = allMethodsAndConstructors.stream()
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                    .mapToInt(vi -> vi.getProperty(VariableProperty.CONTEXT_NOT_NULL))
                    .reduce(MultiLevel.NULLABLE, MAX);
            if (bestOverContext == Level.DELAY) {
                log(DELAYED, "Delay @NotNull on {}, waiting for CNN", fieldInfo.fullyQualifiedName());
                return DELAYS;
            }
            if (bestOverContext < MultiLevel.EFFECTIVELY_NOT_NULL) {
                if (fieldAnalysis.valuesIsNotSet()) return DELAYS;
                assert fieldAnalysis.getValues().expressions().length > 0;

                int worstOverValues = fieldAnalysis.getValues().stream()
                        .mapToInt(expression -> evaluationContext
                                .getProperty(expression, VariableProperty.NOT_NULL_EXPRESSION, false))
                        .min().orElse(MultiLevel.NULLABLE);
                // IMPORTANT: we do not take delays into account!
                finalNotNullValue = Math.max(worstOverValues, bestOverContext);
            } else {
                finalNotNullValue = bestOverContext;
            }
        } else {
            boolean hardNull = fieldAnalysis.getValues().stream().anyMatch(e -> e instanceof NullConstant);
            if (hardNull) {
                finalNotNullValue = MultiLevel.NULLABLE;
            } else {
                if (fieldAnalysis.valuesIsNotSet()) return DELAYS;
                assert fieldAnalysis.getValues().expressions().length > 0;
                int worstOverValuesBreakParameterDelay = fieldAnalysis.getValues().stream()
                        .mapToInt(expression -> notNullBreakParameterDelay(evaluationContext, expression))
                        .min().orElse(MultiLevel.NULLABLE);
                if (worstOverValuesBreakParameterDelay == Level.DELAY) {
                    log(DELAYED, "Delay @NotNull on {}, waiting for values", fieldInfo.fullyQualifiedName());
                    return DELAYS;
                }
                finalNotNullValue = worstOverValuesBreakParameterDelay;
            }
        }
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fieldInfo.fullyQualifiedName(), finalNotNullValue);

        fieldAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, finalNotNullValue);
        return DONE;
    }

    private int notNullBreakParameterDelay(EvaluationContext evaluationContext, Expression expression) {
        int nne = evaluationContext.getProperty(expression, VariableProperty.NOT_NULL_EXPRESSION, false);
        if (nne != Level.DELAY) return nne;
        if (expression.variables().stream().allMatch(v -> v instanceof ParameterInfo)) {
            return MultiLevel.NULLABLE;
        }
        return Level.DELAY;
    }

    private AnalysisStatus fieldErrors() {
        if (fieldInspection.getModifiers().contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = allMethodsAndConstructors.stream()
                        .filter(m -> !(m.methodInfo.isConstructor && m.methodInfo.typeInfo == fieldInfo.owner)) // not my own constructors
                        .anyMatch(this::isReadInMethod);
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
                if (!fieldInfo.owner.isPrivateNested()) {
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
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be immutable: it is not @Final," +
                            " and it can be assigned to from outside this primary type",
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

        if (fieldAnalysis.valuesIsNotSet()) {
            return DELAYS;
        }

        boolean allDelaysResolved = fieldAnalysis.allLinksHaveBeenEstablished.isSet();

        EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration,
                ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure);
        int valueFromAssignment = fieldAnalysis.getValues().stream()
                .mapToInt(expression -> evaluationContext.getProperty(expression, VariableProperty.IMMUTABLE, false))
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
        assert fieldAnalysis.valuesIsNotSet();
        Expression nullValue = ConstantExpression.nullValue(analyserContext.getPrimitives(), fieldInfo.type.bestTypeInfo());
        List<Expression> values = new LinkedList<>();
        boolean delays = false;
        if (haveInitialiser) {
            if (fieldAnalysis.getInitialValue() == null) {
                log(DELAYED, "Delaying consistent value for field " + fieldInfo.fullyQualifiedName());
                delays = true;
            } else {
                values.add(fieldAnalysis.getInitialValue());
            }
        }
        // collect all the other values, bail out when delays
        boolean ignorePrivateConstructors = myTypeAnalyser.ignorePrivateConstructorsForFieldValue();

        boolean occursInAllConstructors = true;
        if (!(fieldInfo.isExplicitlyFinal() && haveInitialiser)) {
            for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
                if (!methodAnalyser.methodInfo.isPrivate() || !ignorePrivateConstructors) {
                    boolean added = false;
                    for (VariableInfo vi : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                        if (vi.isAssigned()) {
                            values.add(vi.getValue());
                            added = true;
                            if (vi.isDelayed()) {
                                log(DELAYED, "Delay consistent value for field {}", fieldInfo.fullyQualifiedName());
                                delays = true;
                            }
                        }
                    }
                    if (!added && methodAnalyser.methodInfo.isConstructor) {
                        occursInAllConstructors = false;
                    }
                }
            }
        }
        if (!haveInitialiser && !occursInAllConstructors) {
            values.add(0, nullValue);
        }
        // order does not matter for this class, but is handy for testing
        values.sort(ExpressionComparator.SINGLETON);
        fieldAnalysis.setValues(MultiExpression.create(values), delays);
        return delays ? DELAYS : DONE;
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
        if (Logger.isLogEnabled(DELAYED)) {
            allMethodsAndConstructors.stream()
                    .filter(m -> !m.getFieldAsVariable(fieldInfo, false).isEmpty())
                    .forEach(m -> log(DELAYED, "Field {}: links have not been established yet in method {}",
                            fieldInfo.name, m.methodInfo.name));
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
        if (fieldAnalysis.valuesIsNotSet()) {
            log(DELAYED, "Delaying, have no values yet for field " + fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;

        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (immutable == Level.DELAY && !fieldOfOwnType) {
            log(DELAYED, "Waiting with effectively final value  until decision on @E2Immutable for {}", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        boolean downgradeFromNewInstanceWithConstructor = !fieldOfOwnType && !MultiLevel.isE2Immutable(immutable);

        // compute and set the combined value
        Expression effectivelyFinalValue = determineEffectivelyFinalValue(fieldAnalysis.getValues(),
                downgradeFromNewInstanceWithConstructor);

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
                    int immutable = evaluationContext.getProperty(parameter, VariableProperty.IMMUTABLE, false);
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

    private Expression determineEffectivelyFinalValue(MultiExpression values,
                                                      boolean downgradeFromNewInstanceWithConstructor) {
        // suppose there are 2 constructors, and the field gets exactly the same value...
        Set<Expression> set = new HashSet<>();
        Collections.addAll(set, values.expressions());

        if (set.size() == 1) {
            Expression expression = values.expressions()[0];
            BooleanConstant TRUE = new BooleanConstant(analyserContext.getPrimitives(), true);
            if (expression instanceof NewObject newObject) {
                // now the state of the new object may survive if there are no modifying methods called,
                // but that's too early to know now
                return downgradeFromNewInstanceWithConstructor ?
                        newObject.copyAfterModifyingMethodOnConstructor(TRUE) : newObject.copyWithNewState(TRUE);
            }
            return expression;
        }
        return new MultiValue(analyserContext, values, fieldInfo.type);
    }

    private AnalysisStatus analyseLinked1() {
        assert !fieldAnalysis.linked1Variables.isSet();

        // we ONLY look at the linked variables of fields that have been assigned to
        Optional<MethodInfo> notDefined = allMethodsAndConstructors.stream()
                .filter(m -> {
                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, false);
                    return !variableInfoList.isEmpty() &&
                            variableInfoList.stream().anyMatch(VariableInfo::isAssigned) &&
                            !variableInfoList.stream().allMatch(VariableInfo::staticallyAssignedVariablesIsSet);
                }).map(ma -> ma.methodInfo).findFirst();
        if (notDefined.isPresent()) {
            log(DELAYED, "Linked1Variables not yet set for {} in method (findFirst): {}",
                    notDefined.get().fullyQualifiedName);
            return DELAYS;
        }

        if (myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes() == null) {
            log(DELAYED, "Linked1Variables not yet set for {}, waiting on implicitly immutable types",
                    fieldInfo.fullyQualifiedName());
            return DELAYS;
        }

        Set<Variable> linked1Variables = allMethodsAndConstructors.stream()
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                .filter(VariableInfo::staticallyAssignedVariablesIsSet)
                .flatMap(vi -> vi.getStaticallyAssignedVariables().variables().stream())
                .filter(v -> !(v instanceof LocalVariableReference)) // especially local variable copies of the field itself
                .filter(v -> myTypeAnalyser.typeAnalysis.getImplicitlyImmutableDataTypes().contains(v.parameterizedType()))
                .collect(Collectors.toSet());
        fieldAnalysis.linked1Variables.set(new LinkedVariables(linked1Variables));
        log(LINKED_VARIABLES, "FA: Set link1s of {} to [{}]", fieldInfo.fullyQualifiedName(),
                Variable.fullyQualifiedName(linked1Variables));

        // explicitly adding the annotation here; it will not be inspected.
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        AnnotationExpression link1Annotation = checkLinks.createLinkAnnotation(e2.linked1.typeInfo(), linked1Variables);
        fieldAnalysis.annotations.put(link1Annotation, !linked1Variables.isEmpty());
        return DONE;
    }

    private AnalysisStatus analyseLinked() {
        assert !fieldAnalysis.linkedVariables.isSet();

        int immutable = fieldAnalysis.getProperty(VariableProperty.IMMUTABLE);
        if (immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
            fieldAnalysis.linkedVariables.set(LinkedVariables.EMPTY);
            log(LINKED_VARIABLES, "Setting linked variables to empty for field {}, @E2Immutable type");
            return DONE;
        }

        // we ONLY look at the linked variables of fields that have been assigned to
        Optional<MethodInfo> notDefined = allMethodsAndConstructors.stream()
                .filter(m -> {
                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, false);
                    return !variableInfoList.isEmpty() &&
                            variableInfoList.stream().anyMatch(VariableInfo::isAssigned) &&
                            !variableInfoList.stream().allMatch(VariableInfo::linkedVariablesIsSet);
                }).map(ma -> ma.methodInfo).findFirst();
        if (notDefined.isPresent()) {
            log(DELAYED, "LinkedVariables not yet set for {} in method (findFirst): {}",
                    notDefined.get().fullyQualifiedName);
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
        AnnotationExpression linkAnnotation = checkLinks.createLinkAnnotation(e2.linked.typeInfo(), linkedVariables);
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
        if (fieldCanBeWrittenFromOutsideThisPrimaryType) {
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

    private AnalysisStatus analysePropagateModification() {
        int epm = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_PROPAGATE_MOD);
        if (epm != Level.DELAY) return DONE;
        String fqn = fieldInfo.fullyQualifiedName();

        if (!fieldAnalysis.allLinksHaveBeenEstablished.isSet()) {
            log(DELAYED, "Delaying, have no linked variables yet for field {}", fqn);
            return DELAYS;
        }
        int max = allMethodsAndConstructors.stream()
                .filter(m -> m.methodAnalysis.getLastStatement() != null)
                .filter(m -> m.methodAnalysis.getLastStatement().variables.isSet(fqn))
                .mapToInt(m -> {
                    VariableInfo variableInfo = m.methodAnalysis.getLastStatement().variables.get(fqn).current();
                    return variableInfo.getProperty(VariableProperty.CONTEXT_PROPAGATE_MOD);
                })
                .max().orElse(Level.FALSE);
        assert max != Level.DELAY : "There should not be a delay after links have been established";
        fieldAnalysis.setProperty(VariableProperty.EXTERNAL_PROPAGATE_MOD, max);
        log(PROPAGATE_MODIFICATION, "Set PM of field {} to {}", fqn, max);
        return DONE;
    }

    private AnalysisStatus analyseModified() {
        int contract = fieldAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
        if (contract != Level.DELAY) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, contract);
            return DONE;
        }
        assert fieldAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD) == Level.DELAY;

        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return DELAYS;
        if (effectivelyFinal == Level.FALSE) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.TRUE);
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
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
            return DONE;
        }

        boolean modified = fieldCanBeWrittenFromOutsideThisPrimaryType ||
                allMethodsAndConstructors.stream()
                        .filter(m -> !m.methodInfo.isConstructor)
                        .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                        .filter(VariableInfo::isRead)
                        .anyMatch(vi -> vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.TRUE);

        if (modified) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.TRUE);
            log(NOT_MODIFIED, "Mark field {} as @Modified", fieldInfo.fullyQualifiedName());
            return DONE;
        }

        // we only consider methods, not constructors!
        boolean allContextModificationsDefined = allMethodsAndConstructors.stream()
                .filter(m -> !m.methodInfo.isConstructor)
                .allMatch(m -> {
                    List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, true);
                    return variableInfoList.isEmpty() ||
                            variableInfoList.stream().noneMatch(VariableInfo::isRead) ||
                            m.methodLevelData().linksHaveBeenEstablished.isSet();
                });

        if (allContextModificationsDefined) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
            log(NOT_MODIFIED, "Mark field {} as @NotModified", fieldInfo.fullyQualifiedName());
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
    FIXME at some point this should go beyond functional interfaces.

    TODO at some point this should go beyond the initializer; it should look at all assignments
     */
    private AnalysisStatus analyseNotModifiedFunctionalInterface() {
        if (sam != null) {
            int modified = sam.methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD);
            if (modified == Level.DELAY) {
                log(DELAYED, "Field {} of functional interface type: waiting for MODIFIED on SAM", fieldInfo.fullyQualifiedName());
                return DELAYS;
            }
            log(NOT_MODIFIED, "Field {} of functional interface type: copying MODIFIED {} from SAM", fieldInfo.fullyQualifiedName(), modified);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, modified);
            return DONE;
        }
        log(NOT_MODIFIED, "Field {} of functional interface type: undeclared, so not modified", fieldInfo.fullyQualifiedName());
        fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
        return DONE;
    }

    @Override
    protected Expression getVariableValue(Variable variable) {
        FieldReference fieldReference = (FieldReference) variable;
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalyser(fieldReference.fieldInfo).fieldAnalysis;
        int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (effectivelyFinal == Level.DELAY) return DelayedVariableExpression.forField(fieldReference);
        if (effectivelyFinal == Level.FALSE) {
            return new VariableExpression(variable);
        }
        Expression effectivelyFinalValue = fieldAnalysis.getEffectivelyFinalValue();
        return Objects.requireNonNullElseGet(effectivelyFinalValue, () -> new VariableExpression(variable));
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        log(ANALYSER, "Checking field {}", fieldInfo.fullyQualifiedName());

        check(NotNull.class, e2.notNull);
        check(NotNull1.class, e2.notNull1);
        check(NotNull2.class, e2.notNull2);
        CheckFinalNotModified.check(messages, fieldInfo, Final.class, e2.effectivelyFinal, fieldAnalysis, myTypeAnalyser.typeAnalysis);
        CheckFinalNotModified.check(messages, fieldInfo, NotModified.class, e2.notModified, fieldAnalysis, myTypeAnalyser.typeAnalysis);

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
        checkLinks.checkLink1sForFields(messages, fieldInfo, fieldAnalysis);

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
            boolean conditionIsDelayed = isDelayed(condition);
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, conditionIsDelayed,
                    Precondition.empty(getPrimitives()), false);
            return FieldAnalyser.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty, boolean duringEvaluation) {
            if (value instanceof VariableExpression variableValue) {
                Variable variable = variableValue.variable();
                return getProperty(variable, variableProperty == VariableProperty.NOT_NULL_EXPRESSION ?
                        VariableProperty.EXTERNAL_NOT_NULL : variableProperty);
            }
            return value.getProperty(this, variableProperty, true);
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            if (variable instanceof FieldReference fieldReference) {
                VariableProperty vp = variableProperty == VariableProperty.NOT_NULL_EXPRESSION
                        ? VariableProperty.EXTERNAL_NOT_NULL : variableProperty;
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof This thisVariable) {
                return getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(variableProperty);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                VariableProperty vp = variableProperty == VariableProperty.NOT_NULL_EXPRESSION
                        ? VariableProperty.NOT_NULL_PARAMETER : variableProperty;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
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
        public String newObjectIdentifier() {
            return fieldInfo.fullyQualifiedName();
        }
    }

}
