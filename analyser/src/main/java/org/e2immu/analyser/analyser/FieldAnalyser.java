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
import org.e2immu.analyser.analyser.check.CheckImmutable;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class FieldAnalyser extends AbstractAnalyser {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FieldAnalyser.class);

    // analyser components, constants are used in tests and delay debugging
    public static final String COMPUTE_TRANSPARENT_TYPE = "computeTransparentType";
    public static final String EVALUATE_INITIALISER = "evaluateInitialiser";
    public static final String ANALYSE_FINAL = "analyseFinal";
    public static final String ANALYSE_FINAL_VALUE = "analyseFinalValue";
    public static final String ANALYSE_IMMUTABLE = "analyseImmutable";
    public static final String ANALYSE_INDEPENDENT = "analyseIndependent";
    public static final String ANALYSE_NOT_NULL = "analyseNotNull";
    public static final String ANALYSE_MODIFIED = "analyseModified";
    public static final String ANALYSE_CONTAINER = "analyseContainer";
    public static final String ANALYSE_LINKED = "analyseLinked";
    public static final String FIELD_ERRORS = "fieldErrors";
    public static final String ANALYSE_ASSIGNMENTS = "allAssignmentsHaveBeenSet";
    public static final String ANALYSE_LINKS_HAVE_BEEN_ESTABLISHED = "allLinksHaveBeenEstablished";
    public static final String ANALYSE_CONSTANT = "analyseConstant";

    public final TypeInfo primaryType;
    public final FieldInfo fieldInfo;
    public final String fqn; // of fieldInfo, saves a lot of typing
    public final FieldInspection fieldInspection;
    public final FieldAnalysisImpl.Builder fieldAnalysis;
    public final MethodAnalyser sam;
    private final boolean fieldCanBeWrittenFromOutsideThisPrimaryType;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final CheckConstant checkConstant;
    private final CheckLinks checkLinks;
    private final boolean haveInitialiser;

    // set at initialisation time
    private List<MethodAnalyser> myMethodsAndConstructors;
    private List<MethodAnalyser> myStaticBlocks;
    private TypeAnalyser myTypeAnalyser;

    private record SharedState(int iteration, EvaluationContext closure) {
    }

    private final Predicate<WithInspectionAndAnalysis> ignoreMyConstructors;

    public FieldAnalyser(FieldInfo fieldInfo,
                         TypeInfo primaryType,
                         TypeAnalysis ownerTypeAnalysis,
                         MethodAnalyser sam,
                         AnalyserContext nonExpandableAnalyserContext) {
        super("Field " + fieldInfo.name, new ExpandableAnalyserContextImpl(nonExpandableAnalyserContext));
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());
        this.checkLinks = new CheckLinks(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());

        this.fieldInfo = fieldInfo;
        ignoreMyConstructors = w -> w instanceof MethodInfo methodInfo
                && methodInfo.isConstructor
                && methodInfo.typeInfo == fieldInfo.owner;
        fqn = fieldInfo.fullyQualifiedName();
        fieldInspection = fieldInfo.fieldInspection.get();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, fieldInfo, ownerTypeAnalysis);
        this.primaryType = primaryType;
        this.sam = sam;
        fieldCanBeWrittenFromOutsideThisPrimaryType = fieldInfo.isAccessibleOutsideOfPrimaryType() &&
                !fieldInfo.isExplicitlyFinal() && !fieldInfo.owner.isPrivateOrEnclosingIsPrivate();
        haveInitialiser = fieldInspection.fieldInitialiserIsSet() && fieldInspection.getFieldInitialiser().initialiser() != EmptyExpression.EMPTY_EXPRESSION;

        analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
                .add(COMPUTE_TRANSPARENT_TYPE, sharedState -> computeTransparentType())
                .add(EVALUATE_INITIALISER, this::evaluateInitialiser)
                .add(ANALYSE_FINAL, this::analyseFinal)
                .add(ANALYSE_ASSIGNMENTS, sharedState -> allAssignmentsHaveBeenSet())
                .add(ANALYSE_LINKED, sharedState -> analyseLinked())
                .add(ANALYSE_LINKS_HAVE_BEEN_ESTABLISHED, sharedState -> allLinksHaveBeenEstablished())
                .add(ANALYSE_IMMUTABLE, this::analyseImmutable)
                .add(ANALYSE_MODIFIED, sharedState -> analyseModified())
                .add(ANALYSE_FINAL_VALUE, sharedState -> analyseFinalValue())
                .add(ANALYSE_CONSTANT, sharedState -> analyseConstant())
                .add(ANALYSE_INDEPENDENT, this::analyseIndependent)
                .add(ANALYSE_NOT_NULL, sharedState -> analyseNotNull())
                .add(ANALYSE_CONTAINER, sharedState -> analyseContainer())
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
        List<MethodAnalyser> myMethodsAndConstructors = new LinkedList<>();
        List<MethodAnalyser> myStaticBlocks = new LinkedList<>();

        messages.addAll(fieldAnalysis.fromAnnotationsIntoProperties(AnalyserIdentification.FIELD, false,
                fieldInfo.fieldInspection.get().getAnnotations(), analyserContext.getE2ImmuAnnotationExpressions()));

        analyserContext.methodAnalyserStream().forEach(analyser -> {
            if (analyser.methodInspection.isStaticBlock()) {
                myStaticBlocks.add(analyser);
            } else if (analyser.methodInfo.typeInfo == fieldInfo.owner) {
                myMethodsAndConstructors.add(analyser);
            }
        });
        myTypeAnalyser = analyserContext.getTypeAnalyser(fieldInfo.owner);
        this.myMethodsAndConstructors = List.copyOf(myMethodsAndConstructors);
        this.myStaticBlocks = List.copyOf(myStaticBlocks);
    }

    private Stream<MethodAnalyser> otherStaticBlocks() {
        TypeInfo primaryType = myTypeAnalyser.primaryType;
        TypeInspection primaryTypeInspection = analyserContext.getTypeInspection(primaryType);
        return primaryTypeInspection.staticBlocksRecursively(analyserContext)
                .filter(m -> !(m.typeInfo == fieldInfo.owner)) // filter out mine
                .map(analyserContext::getMethodAnalyser);
    }

    // group them per type, because we take only one value per type
    private Stream<List<MethodAnalyser>> staticBlocksPerTypeExcludeMine() {
        TypeInfo primaryType = myTypeAnalyser.primaryType;
        TypeInspection primaryTypeInspection = analyserContext.getTypeInspection(primaryType);
        return primaryTypeInspection.staticBlocksPerType(analyserContext)
                .filter(list -> !list.isEmpty() && !(list.get(0).typeInfo == fieldInfo.owner)) // filter out mine
                .map(list -> list.stream().map(analyserContext::getMethodAnalyser).toList());
    }

    private Stream<MethodAnalyser> allMethodsAndConstructors(boolean alsoMyOwnConstructors) {
        return analyserContext.methodAnalyserStream()
                .filter(ma -> !ma.methodInspection.isStaticBlock())
                .filter(ma -> alsoMyOwnConstructors ||
                        !(ma.methodInfo.typeInfo == fieldInfo.owner && ma.methodInfo.isConstructor))
                .flatMap(ma -> Stream.concat(Stream.of(ma),
                        ma.getLocallyCreatedPrimaryTypeAnalysers().flatMap(PrimaryTypeAnalyser::methodAnalyserStream)));
    }

    @Override
    public AnalysisStatus analyse(int iteration, EvaluationContext closure) {
        log(ANALYSER, "Analysing field {}", fqn);

        // analyser visitors
        try {
            SharedState sharedState = new SharedState(iteration, closure);
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);

            List<FieldAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterFieldAnalyserVisitors();
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
            LOGGER.warn("Caught exception in method analyser: {}", fqn);
            throw rte;
        }
    }

    @Override
    public void write() {
        // before we check, we copy the properties into annotations
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        fieldAnalysis.transferPropertiesToAnnotations(e2);
    }

    private AnalysisStatus evaluateInitialiser(SharedState sharedState) {
        if (fieldInspection.fieldInitialiserIsSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
            if (fieldInitialiser.initialiser() != EmptyExpression.EMPTY_EXPRESSION) {
                Expression initializer;
                if (fieldInitialiser.initialiser() instanceof MethodReference) {
                    initializer = ConstructorCall.instanceFromSam(fieldInitialiser.implementationOfSingleAbstractMethod(),
                            fieldInfo.type);
                } else {
                    initializer = fieldInitialiser.initialiser();
                }
                EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration(),
                        ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure());
                EvaluationResult evaluationResult = initializer.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                Expression initialiserValue = evaluationResult.value();
                if (evaluationResult.someValueWasDelayed()) {
                    fieldAnalysis.initialValue.setVariable(initialiserValue);
                } else {
                    fieldAnalysis.initialValue.setFinal(initialiserValue);
                }
                log(FINAL, "Set initialiser of field {} to {}", fqn, evaluationResult.value());
                if (evaluationResult.someValueWasDelayed()) {
                    assert translatedDelay(EVALUATE_INITIALISER, "EXPRESSION " + initialiserValue,
                            fqn + ".initialValue");
                    return DELAYS;
                }
                return DONE;
            }
        }
        Expression nullValue = ConstantExpression.nullValue(analyserContext.getPrimitives(), fieldInfo.type.bestTypeInfo());
        fieldAnalysis.initialValue.setFinal(nullValue);
        return DONE;
    }


    private AnalysisStatus computeTransparentType() {
        assert !fieldAnalysis.isOfTransparentTypeIsSet();
        if (myTypeAnalyser.typeAnalysis.getTransparentTypes() == null) {
            assert translatedDelay(COMPUTE_TRANSPARENT_TYPE,
                    myTypeAnalyser.typeInfo.fullyQualifiedName + D_TRANSPARENT_TYPE,
                    fqn + D_TRANSPARENT_TYPE);
            return DELAYS;
        }
        boolean transparent = myTypeAnalyser.typeAnalysis.getTransparentTypes().contains(fieldInfo.type);
        fieldAnalysis.setTransparentType(transparent);
        return DONE;
    }

    private AnalysisStatus analyseContainer() {
        TypeInfo bestType = fieldInfo.type.bestTypeInfo();
        if (bestType == null) return DONE;
        if (!bestType.isAbstract()) return DONE;

        assert fieldAnalysis.getProperty(VariableProperty.CONTAINER) == Level.DELAY;

        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
        int typeContainer = typeAnalysis.getProperty(VariableProperty.CONTAINER);
        if (typeContainer == Level.DELAY) return DELAYS;
        if (typeContainer == Level.TRUE) {
            fieldAnalysis.setProperty(VariableProperty.CONTAINER, Level.TRUE);
            return DONE;
        }

        // only worth doing something when the field is statically not a container
        boolean someParameterModificationUnknown = methodsForModification()
                .flatMap(method -> method.getParameterAnalysers().stream())
                .anyMatch(p -> p.parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE) == Level.DELAY);
        if (someParameterModificationUnknown) {
            log(MODIFICATION, "Delaying @Container on field {}, some parameters have no @Modified status yet",
                    fqn);
        }
        boolean allParametersNotModified = methodsForModification()
                .flatMap(method -> method.getParameterAnalysers().stream()).allMatch(p ->
                        p.parameterAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE) == Level.FALSE);

        log(MODIFICATION, "Set @Container on {} to {}", fqn, allParametersNotModified);
        fieldAnalysis.setProperty(VariableProperty.CONTAINER, Level.fromBool(allParametersNotModified));
        return DONE;
    }

    private static final IntBinaryOperator MAX = (i1, i2) -> i1 == Level.DELAY || i2 == Level.DELAY ? Level.DELAY :
            Math.max(i1, i2);

    /*
    not null has been intentionally decoupled from value and linked values

    for methods/constructors with assignment to the variable, we wait for linked variables to be set AND for not null delays.
    for methods which only read, we only wait for not-null delays to be resolved.
     */
    private AnalysisStatus analyseNotNull() {
        if (fieldAnalysis.getProperty(VariableProperty.EXTERNAL_NOT_NULL) != Level.DELAY) return DONE;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            assert translatedDelay(ANALYSE_NOT_NULL, fqn + ".FINAL",
                    fqn + D_EXTERNAL_NOT_NULL);
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fqn);
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it be assigned to from outside this primary type",
                    fqn);
            fieldAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, MultiLevel.NULLABLE);
            return DONE;
        }

        int finalNotNullValue;

        Boolean onlyAssignedToParameters = onlyAssignedToParameters();
        if (onlyAssignedToParameters == null) {
            assert translatedDelay(ANALYSE_NOT_NULL, fqn + D_VALUES,
                    fqn + D_EXTERNAL_NOT_NULL);
            log(DELAYED, "Delaying @NotNull on {}, waiting for values", fqn);
            return DELAYS;
        }

        if (onlyAssignedToParameters) {
            int bestOverContext = allMethodsAndConstructors(true)
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                    .mapToInt(vi -> vi.getProperty(VariableProperty.CONTEXT_NOT_NULL))
                    .reduce(MultiLevel.NULLABLE, MAX);
            if (bestOverContext == Level.DELAY) {
                assert translatedDelay(ANALYSE_NOT_NULL, ".CONTEXT_NOT_NULL",  // TODO
                        fqn + D_EXTERNAL_NOT_NULL);
                log(DELAYED, "Delay @NotNull on {}, waiting for CNN", fqn);
                return DELAYS;
            }
            if (bestOverContext < MultiLevel.EFFECTIVELY_NOT_NULL) {
                assert fieldAnalysis.getValues().size() > 0;

                int worstOverValues = fieldAnalysis.getValues().stream()
                        .mapToInt(proxy -> proxy.getProperty(VariableProperty.NOT_NULL_EXPRESSION))
                        .min().orElse(MultiLevel.NULLABLE);
                // IMPORTANT: we do not take delays into account!
                finalNotNullValue = Math.max(worstOverValues, bestOverContext);
            } else {
                finalNotNullValue = bestOverContext;
            }
        } else {
            boolean hardNull = fieldAnalysis.getValues().stream().anyMatch(e -> e.getValue() instanceof NullConstant);
            if (hardNull) {
                finalNotNullValue = MultiLevel.NULLABLE;
            } else {
                // something will be there, and there'll be a delay on the NNE property if needed
                assert fieldAnalysis.getValues().size() > 0;
                int worstOverValuesBreakParameterDelay = fieldAnalysis.getValues().stream()
                        .mapToInt(this::notNullBreakParameterDelay)
                        .min().orElse(MultiLevel.NULLABLE);
                if (worstOverValuesBreakParameterDelay == Level.DELAY) {
                    assert translatedDelay(ANALYSE_NOT_NULL, fqn + D_VALUES, // TODO
                            fqn + D_EXTERNAL_NOT_NULL);
                    log(DELAYED, "Delay @NotNull on {}, waiting for values", fqn);
                    return DELAYS;
                }
                finalNotNullValue = worstOverValuesBreakParameterDelay;
            }
        }
        log(NOT_NULL, "Set property @NotNull on field {} to value {}", fqn, finalNotNullValue);

        fieldAnalysis.setProperty(VariableProperty.EXTERNAL_NOT_NULL, finalNotNullValue);
        return DONE;
    }

    private Boolean onlyAssignedToParameters() {
        boolean notOnlyAssignedToParameters = fieldAnalysis.getValues().stream()
                .anyMatch(proxy -> {
                    Expression e = proxy.getValue();
                    VariableExpression ve;
                    return !proxy.isDelayedValue() &&
                            ((ve = e.asInstanceOf(VariableExpression.class)) == null ||
                                    !(ve.variable() instanceof ParameterInfo));
                });

        boolean onlyAssignedToParameters = fieldAnalysis.getValues().stream()
                // parameters can be influenced by context not null, all the rest cannot
                .allMatch(proxy -> {
                    VariableExpression ve;
                    return (ve = proxy.getValue().asInstanceOf(VariableExpression.class)) != null &&
                            ve.variable() instanceof ParameterInfo;
                });

        if (!notOnlyAssignedToParameters && !onlyAssignedToParameters) {
            return null;
        }
        return onlyAssignedToParameters;
    }

    private int notNullBreakParameterDelay(FieldAnalysisImpl.ValueAndPropertyProxy proxy) {
        int nne = proxy.getProperty(VariableProperty.NOT_NULL_EXPRESSION);
        if (nne != Level.DELAY) return nne;
        List<Variable> variables = proxy.getValue().variables();
        if (!variables.isEmpty() && variables.stream().allMatch(v -> v instanceof ParameterInfo)) {
            return MultiLevel.NULLABLE;
        }
        return Level.DELAY;
    }

    private AnalysisStatus fieldErrors() {
        if (fieldInspection.getModifiers().contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = allMethodsAndConstructors(false).anyMatch(this::isReadInMethod);
                if (!readInMethods) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.Label.PRIVATE_FIELD_NOT_READ));
                }
                return DONE;
            }
        } else {
            int effectivelyFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
            if (effectivelyFinal == Level.FALSE) {
                // only react once we're certain the variable is not effectively final
                // error, unless we're in a record
                if (!fieldInfo.owner.isPrivateNested()) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.Label.NON_PRIVATE_FIELD_NOT_FINAL));
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

    /*
    Similarly to dynamically level 2+ immutable, a field can be dynamically higher level independent!

    If the type of the field is Set<String>, the static independence value is DEPENDENT (there is the iterator.remove())
    But when this field is final and only constructed with Set.copyOf or Set.of, for example, the type will become
    recursively immutable, and therefore also independent.
     */
    private AnalysisStatus analyseIndependent(SharedState sharedState) {
        if (fieldAnalysis.getProperty(VariableProperty.INDEPENDENT) != Level.DELAY) return DONE;

        int staticallyIndependent = fieldInfo.type.defaultIndependent(analyserContext);
        if (staticallyIndependent == MultiLevel.INDEPENDENT) {
            log(INDEPENDENCE, "Field {} set to @Independent: static type",
                    fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(VariableProperty.INDEPENDENT, MultiLevel.INDEPENDENT);
            return DONE;
        }

        int immutable = fieldAnalysis.getPropertyFromMapDelayWhenAbsent(VariableProperty.EXTERNAL_IMMUTABLE);
        if (immutable == Level.DELAY) {
            log(DELAYED, "Field {} independent delayed: wait for immutable", fieldInfo.fullyQualifiedName());
            return DELAYS;
        }
        int immutableLevel = MultiLevel.level(immutable);
        if (immutableLevel >= MultiLevel.LEVEL_2_IMMUTABLE) {
            int independent = MultiLevel.independentCorrespondingToImmutableLevel(immutableLevel);
            log(INDEPENDENCE, "Field {} set to {}, direct correspondence to (dynamically) immutable",
                    fieldInfo.fullyQualifiedName(), MultiLevel.niceIndependent(independent));
            fieldAnalysis.setProperty(VariableProperty.INDEPENDENT, independent);
            return DONE;
        }

        if (staticallyIndependent == Level.DELAY) {
            log(DELAYED, "Field {} independent delayed: wait for type independence of {}",
                    fieldInfo.fullyQualifiedName(), fieldInfo.type);
            return DELAYS;
        }
        fieldAnalysis.setProperty(VariableProperty.INDEPENDENT, staticallyIndependent);
        return DONE;
    }

    /*
    method modelled to that of analyseNotNull.
     */
    private AnalysisStatus analyseImmutable(SharedState sharedState) {
        if (fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE) != Level.DELAY) return DONE;

        int isFinal = fieldAnalysis.getProperty(VariableProperty.FINAL);
        if (isFinal == Level.DELAY) {
            log(DELAYED, "Delaying @Immutable on {} until we know about @Final", fqn);
            return DELAYS;
        }
        if (isFinal == Level.FALSE && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be immutable: it is not @Final," +
                    " and it can be assigned to from outside this primary type", fqn);
            fieldAnalysis.setProperty(VariableProperty.EXTERNAL_IMMUTABLE, MultiLevel.MUTABLE);
            return DONE;
        }

        int staticallyImmutable = fieldInfo.type.defaultImmutable(analyserContext, false);
        if (staticallyImmutable == MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE) {
            log(IMMUTABLE_LOG, "Field {} is statically @ERImmutable", fqn);
            fieldAnalysis.setProperty(VariableProperty.EXTERNAL_IMMUTABLE, staticallyImmutable);
            return DONE; // cannot be improved
        }

        Boolean onlyAssignedToParameters = onlyAssignedToParameters();
        if (onlyAssignedToParameters == null) {
            log(DELAYED, "Delaying @Immutable on {}, waiting for values", fqn);
            return DELAYS;
        }

        int finalImmutable;
        if (onlyAssignedToParameters) {
            int bestOverContext = allMethodsAndConstructors(true)
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                    .mapToInt(vi -> vi.getProperty(VariableProperty.CONTEXT_IMMUTABLE))
                    .reduce(MultiLevel.MUTABLE, MAX);
            if (bestOverContext == Level.DELAY) {
                log(DELAYED, "Delay @Immutable on {}, waiting for context immutable", fqn);
                return DELAYS;
            }
            if (bestOverContext < MultiLevel.EVENTUALLY_E2IMMUTABLE_AFTER_MARK) {
                if (fieldAnalysis.valuesIsNotSet()) {
                    log(DELAYED, "Delaying @Immutable of field {}, parameter values not yet known", fqn);
                    return DELAYS;
                }
                assert fieldAnalysis.getValues().size() > 0;

                int worstOverValues = fieldAnalysis.getValues().stream()
                        .mapToInt(proxy -> proxy.getProperty(VariableProperty.IMMUTABLE))
                        .min()
                        .orElse(MultiLevel.MUTABLE);
                finalImmutable = Math.max(staticallyImmutable, Math.max(worstOverValues, bestOverContext));
            } else {
                finalImmutable = Math.max(staticallyImmutable, bestOverContext);
            }
        } else {
            if (fieldAnalysis.valuesIsNotSet()) {
                log(DELAYED, "Delaying @Immutable of field {}, non-parameter values not yet known", fqn);
                return DELAYS;
            }
            if (!fieldAnalysis.allLinksHaveBeenEstablished.isSet() && isFinal == Level.FALSE) {
                /* if the field is effectively final, we don't need links established because all assignment
                 occurs in the constructor
                 */
                log(DELAYED, "Delaying @Immutable of field {}, not all links have been established", fqn);
                return DELAYS;
            }
            int worstOverValuesBreakParameterDelay = fieldAnalysis.getValues().stream()
                    .filter(proxy -> !(proxy.getValue() instanceof NullConstant))
                    .mapToInt(this::immutableBreakParameterDelay)
                    .min().orElse(MultiLevel.MUTABLE);
            if (worstOverValuesBreakParameterDelay == Level.DELAY) {
                log(DELAYED, "Delay @Immutable on {}, waiting for values", fqn);
                return DELAYS;
            }

            // if we have an assignment to an eventually immutable variable, but somehow the construction context enforces "after"
            // that should be taken into account (see EventuallyImmutableUtil_2 vs E2InContext_2)
            if (MultiLevel.isBefore(worstOverValuesBreakParameterDelay)) {
                int bestOverContext = myMethodsAndConstructors.stream()
                        .filter(m -> m.methodInfo.isConstructor || m.methodInfo.methodResolution.get().partOfConstruction()
                                == MethodResolution.CallStatus.PART_OF_CONSTRUCTION)
                        .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                        .mapToInt(vi -> vi.getProperty(VariableProperty.CONTEXT_IMMUTABLE))
                        .reduce(MultiLevel.MUTABLE, MAX);
                if (bestOverContext == Level.DELAY) {
                    log(DELAYED, "Delay @Immutable on {}, waiting for context immutable", fqn);
                    return DELAYS;
                }
                finalImmutable = Math.max(worstOverValuesBreakParameterDelay, bestOverContext);
            } else {
                finalImmutable = worstOverValuesBreakParameterDelay;
            }
        }
        // See E2InContext_0,1 (field is not private, so if it's before, someone else can change it into after)
        int correctedImmutable = correctForExposureBefore(finalImmutable);
        if (correctedImmutable == Level.DELAY) {
            log(DELAYED, "Delay @Immutable on {}, waiting for exposure to decide on @BeforeMark", fqn);
            // still, we're already marking
            fieldAnalysis.setProperty(VariableProperty.PARTIAL_EXTERNAL_IMMUTABLE, finalImmutable);
            return DELAYS;
        }
        log(IMMUTABLE_LOG, "Set immutable on field {} to value {}", fqn, correctedImmutable);
        fieldAnalysis.setProperty(VariableProperty.EXTERNAL_IMMUTABLE, correctedImmutable);

        return DONE;
    }

    private int correctForExposureBefore(int immutable) {
        if (immutable != MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK &&
                immutable != MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK) {
            return immutable;
        }
        int corrected = immutable == MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK ? MultiLevel.EVENTUALLY_E1IMMUTABLE :
                MultiLevel.EVENTUALLY_E2IMMUTABLE;
        if (fieldInfo.isAccessibleOutsideOfPrimaryType()) {
            return corrected;
        }
        // check exposed via return values of methods
        Optional<MethodAnalyser> delayLinkedVariables = myMethodsAndConstructors.stream()
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .filter(ma -> !ma.methodInfo.isPrivate() && ((ComputingMethodAnalyser) ma).methodLevelData() != null)
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.FINALIZER) != Level.TRUE)
                .filter(ma -> ((ComputingMethodAnalyser) ma).methodLevelData().linksHaveNotYetBeenEstablished(ignoreMyConstructors))
                .findFirst();
        if (delayLinkedVariables.isPresent()) {
            log(DELAYED, "Exposure computation on {} delayed by links of {}", fqn,
                    delayLinkedVariables.get().methodInfo.fullyQualifiedName);
            return Level.DELAY;
        }
        FieldReference me = new FieldReference(analyserContext, fieldInfo);
        boolean linkedToMe = myMethodsAndConstructors.stream()
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .filter(ma -> !ma.methodInfo.isPrivate() && ((ComputingMethodAnalyser) ma).methodLevelData() != null)
                .filter(ma -> ma.methodAnalysis.getProperty(VariableProperty.FINALIZER) != Level.TRUE)
                .anyMatch(ma -> {
                    if (ma.methodInfo.hasReturnValue()) {
                        LinkedVariables linkedVariables = ((ComputingMethodAnalyser) ma).getReturnAsVariable().getLinkedVariables();
                        if (linkedVariables.value(me) == LinkedVariables.DEPENDENT) return true;
                    }
                    return ma.methodAnalysis.getLastStatement().variableStream()
                            .filter(vi -> vi.variable() instanceof ParameterInfo)
                            .anyMatch(vi -> vi.getLinkedVariables().contains(me));
                });
        return linkedToMe ? corrected : immutable;
    }

    private int immutableBreakParameterDelay(FieldAnalysisImpl.ValueAndPropertyProxy proxy) {
        int imm = proxy.getProperty(VariableProperty.IMMUTABLE);
        if (imm != Level.DELAY) return imm;
        if (proxy.getValue().returnType().bestTypeInfo() == fieldInfo.owner) {
            // we cannot break a delay for our own type
            return Level.DELAY;
        }
        //
        LinkedVariables variables = proxy.getLinkedVariables()
                .remove(v -> v instanceof FieldReference fr && fr.fieldInfo == fieldInfo);
        if (!variables.isEmpty() && variables.variablesWithLevel(LinkedVariables.DEPENDENT)
                .allMatch(v -> v instanceof ParameterInfo)) {
            return MultiLevel.MUTABLE;
        }
        return Level.DELAY;
    }

    record OccursAndDelay(boolean occurs, int occurrenceCountForError, boolean delay) {
    }

    // NOTE: we're also considering non-private methods here, like setters: IS THIS WISE?

    private OccursAndDelay occursInAllConstructors(List<FieldAnalysisImpl.ValueAndPropertyProxy> values,
                                                   boolean ignorePrivateConstructors) {
        boolean occurs = true;
        boolean delays = false;
        int occurrenceCountForError = 0;
        for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
            if (methodAnalyser.methodAnalysis.getProperty(VariableProperty.FINALIZER) != Level.TRUE &&
                    (!methodAnalyser.methodInfo.isPrivate() ||
                            methodAnalyser.methodInfo.isConstructor && !ignorePrivateConstructors)) {
                boolean added = false;
                for (VariableInfo vi : methodAnalyser.getFieldAsVariableAssigned(fieldInfo)) {
                    Expression expression = vi.getValue();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodAnalyser.methodInfo.fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    values.add(new FieldAnalysisImpl.ValueAndPropertyProxy() {
                        @Override
                        public Expression getValue() {
                            return vi.getValue();
                        }

                        @Override
                        public int getProperty(VariableProperty variableProperty) {
                            return vi.getProperty(variableProperty);
                        }

                        @Override
                        public boolean isDelayedValue() {
                            return vi.isDelayed();
                        }

                        @Override
                        public LinkedVariables getLinkedVariables() {
                            return vi.getLinkedVariables();
                        }

                        @Override
                        public String toString() {
                            return "ALL_CONSTR:" + getValue().toString();
                        }
                    });
                    if (!fieldInspection.isStatic() && methodAnalyser.methodInfo.isConstructor) {
                        // we'll warn for the combination of field initializer, and occurrence in at least one constructor
                        occurrenceCountForError++;
                    }
                    added = true;
                    if (vi.isDelayed()) {
                        log(DELAYED, "Delay consistent value for field {}", fqn);
                        delays = true;
                    }
                }
                if (!added && methodAnalyser.methodInfo.isConstructor) {
                    occurs = false;
                }
            }
        }
        return new OccursAndDelay(occurs, occurrenceCountForError, delays);
    }

    private OccursAndDelay occursInStaticBlocks(List<MethodAnalyser> staticBlocks, List<FieldAnalysisImpl.ValueAndPropertyProxy> values) {
        boolean delays = false;
        FieldAnalysisImpl.ValueAndPropertyProxy latestBlock = null;
        for (MethodAnalyser methodAnalyser : staticBlocks) {
            for (VariableInfo vi : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                if (vi.isAssigned()) {
                    Expression expression = vi.getValue();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodAnalyser.methodInfo.fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    latestBlock = new FieldAnalysisImpl.ValueAndPropertyProxy() {
                        @Override
                        public Expression getValue() {
                            return vi.getValue();
                        }

                        @Override
                        public int getProperty(VariableProperty variableProperty) {
                            return vi.getProperty(variableProperty);
                        }

                        @Override
                        public LinkedVariables getLinkedVariables() {
                            return vi.getLinkedVariables();
                        }

                        @Override
                        public boolean isDelayedValue() {
                            return vi.isDelayed();
                        }

                        @Override
                        public String toString() {
                            return "STATIC_BLOCK:" + getValue().toString();
                        }
                    };
                    delays = vi.isDelayed();
                }
            }
        }
        if (latestBlock != null) {
            values.add(latestBlock);
            if (delays) {
                log(DELAYED, "Delaying initialization of field {} in static block", fieldInfo.fullyQualifiedName());
            }
            return new OccursAndDelay(true, 1, delays);
        }
        return new OccursAndDelay(false, 0, false);
    }

    private AnalysisStatus allAssignmentsHaveBeenSet() {
        assert fieldAnalysis.valuesIsNotSet();
        List<FieldAnalysisImpl.ValueAndPropertyProxy> values = new ArrayList<>();
        boolean delays = false;
        if (haveInitialiser) {
            delays = fieldAnalysis.initialValue.isVariable();
            EvaluationContext ec = new EvaluationContextImpl(0,
                    ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null);
            values.add(new FieldAnalysisImpl.ValueAndPropertyProxy() {
                @Override
                public Expression getValue() {
                    return fieldAnalysis.getInitialValue();
                }

                @Override
                public boolean isDelayedValue() {
                    return fieldAnalysis.initialValue.isVariable();
                }

                @Override
                public LinkedVariables getLinkedVariables() {
                    return fieldAnalysis.getLinkedVariables();
                }

                @Override
                public int getProperty(VariableProperty variableProperty) {
                    return ec.getProperty(getValue(), variableProperty, false, false);
                }

                @Override
                public String toString() {
                    return "ALL_SET:" + getValue().toString();
                }
            });
        }
        // collect all the other values, bail out when delays

        boolean occursInAllConstructorsOrOneStaticBlock;
        boolean cannotGoTogetherWithInitialiser;
        if (fieldInfo.isExplicitlyFinal() && haveInitialiser) {
            occursInAllConstructorsOrOneStaticBlock = true;
            cannotGoTogetherWithInitialiser = false;
        } else {
            boolean ignorePrivateConstructors = myTypeAnalyser.ignorePrivateConstructorsForFieldValue();
            OccursAndDelay oad = occursInAllConstructors(values, ignorePrivateConstructors);
            occursInAllConstructorsOrOneStaticBlock = oad.occurs;
            cannotGoTogetherWithInitialiser = oad.occurrenceCountForError > 0;
            delays |= oad.delay;

            if (fieldInspection.isStatic()) {
                // also look in my static blocks
                OccursAndDelay myBlocks = occursInStaticBlocks(myStaticBlocks, values);
                delays |= myBlocks.delay;
                occursInAllConstructorsOrOneStaticBlock = myBlocks.occurs;

                // and add values of other static blocks
                delays |= staticBlocksPerTypeExcludeMine().map(list -> {
                    OccursAndDelay staticOad = occursInStaticBlocks(list, values);
                    return staticOad.delay;
                }).reduce(false, (v, w) -> v || w);
            }
        }
        if (!delays && haveInitialiser && cannotGoTogetherWithInitialiser) {
            Message message = Message.newMessage(new Location(fieldInfo), Message.Label.UNNECESSARY_FIELD_INITIALIZER);
            messages.add(message);
        }
        if (!haveInitialiser && !occursInAllConstructorsOrOneStaticBlock) {
            Expression nullValue = ConstantExpression.nullValue(analyserContext.getPrimitives(),
                    fieldInfo.type.bestTypeInfo());
            values.add(0, new FieldAnalysisImpl.ValueAndPropertyProxy() {
                @Override
                public Expression getValue() {
                    return nullValue;
                }

                @Override
                public boolean isDelayedValue() {
                    return false;
                }

                @Override
                public LinkedVariables getLinkedVariables() {
                    return LinkedVariables.EMPTY;
                }

                @Override
                public int getProperty(VariableProperty variableProperty) {
                    return getValue().getProperty(null, variableProperty, false);
                }

                @Override
                public String toString() {
                    return "NO_INIT:" + getValue().toString();
                }
            });
        }
        // order does not matter for this class, but is handy for testing
        values.sort(FieldAnalysisImpl.ValueAndPropertyProxy.COMPARATOR);
        fieldAnalysis.setValues(values, delays);
        return delays ? DELAYS : DONE;
    }

    private AnalysisStatus allLinksHaveBeenEstablished() {
        assert !fieldAnalysis.allLinksHaveBeenEstablished.isSet();
        if (fieldAnalysis.linkedVariables.isSet()) {
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
        assert !fieldAnalysis.effectivelyFinalValue.isSet();

        if (fieldAnalysis.getProperty(VariableProperty.FINAL) != Level.TRUE) {
            fieldAnalysis.effectivelyFinalValue.set(new UnknownExpression(fieldInfo.type, UnknownExpression.VARIABLE));
            return DONE;
        }
        if (fieldAnalysis.valuesIsNotSet()) {
            log(DELAYED, "Delaying, have no values yet for field " + fqn);
            return DELAYS;
        }
        List<FieldAnalysisImpl.ValueAndPropertyProxy> values = fieldAnalysis.getValues();


        // compute and set the combined value
        Expression effectivelyFinalValue;

        // suppose there are 2 constructors, and the field gets exactly the same value...
        List<Expression> expressions = values.stream().map(FieldAnalysisImpl.ValueAndPropertyProxy::getValue).toList();
        Set<Expression> set = new HashSet<>(expressions);

        if (set.size() == 1) {
            Expression expression = values.get(0).getValue();
            ConstructorCall constructorCall;
            if ((constructorCall = expression.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                // now the state of the new object may survive if there are no modifying methods called,
                // but that's too early to know now
                int immutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
                if (immutable == Level.DELAY) {
                    // see analyseImmutable, @BeforeMark
                    immutable = fieldAnalysis.getProperty(VariableProperty.PARTIAL_EXTERNAL_IMMUTABLE);
                }
                boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;

                if (immutable == Level.DELAY && !fieldOfOwnType) {
                    log(DELAYED, "Waiting with effectively final value  until decision on @E2Immutable for {}", fqn);
                    fieldAnalysis.setProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY, Level.TRUE);
                    return DELAYS;
                }
                boolean downgradeFromNewInstanceWithConstructor = !fieldOfOwnType && immutable < MultiLevel.EFFECTIVELY_E2IMMUTABLE;
                if (downgradeFromNewInstanceWithConstructor) {
                    effectivelyFinalValue = constructorCall.removeConstructor();
                } else {
                    effectivelyFinalValue = constructorCall;
                }
            } else {
                effectivelyFinalValue = expression;
            }
        } else {
            MultiExpression multiExpression = MultiExpression.create(expressions);
            effectivelyFinalValue = new MultiValue(fieldInfo.getIdentifier(), analyserContext, multiExpression, fieldInfo.type);
        }

        // check constant, but before we set the effectively final value

        log(FINAL, "Setting initial value of effectively final of field {} to {}",
                fqn, effectivelyFinalValue);
        fieldAnalysis.effectivelyFinalValue.set(effectivelyFinalValue);
        return DONE;
    }

    private AnalysisStatus analyseConstant() {
        if (fieldAnalysis.getProperty(VariableProperty.CONSTANT) != Level.DELAY) return DONE;
        if (!fieldAnalysis.effectivelyFinalValue.isSet()) {
            log(DELAYED, "Delaying @Constant, effectively final value not yet set");
            return DELAYS;
        }

        Expression effectivelyFinalValue = fieldAnalysis.effectivelyFinalValue.get();
        if (effectivelyFinalValue.isUnknown()) {
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.FALSE);
            return DONE;
        }

        boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;
        int immutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
        if (immutable == Level.DELAY && !fieldOfOwnType) {
            log(DELAYED, "Waiting with @Constant until decision on @E2Immutable for {}",
                    fqn);
            return DELAYS;
        }

        Boolean recursivelyConstant;
        if (!fieldOfOwnType && !MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) recursivelyConstant = false;
        else recursivelyConstant = recursivelyConstant(effectivelyFinalValue);
        if (recursivelyConstant == null) {
            log(DELAYED, "Delaying @Constant because of recursively constant computation on value {} of {}",
                    fqn, effectivelyFinalValue);
            return DELAYS;
        }

        if (recursivelyConstant) {
            // directly adding the annotation; it will not be used for inspection
            E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, effectivelyFinalValue);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.TRUE);
            log(FINAL, "Added @Constant annotation on field {}", fqn);
        } else {
            fieldAnalysis.setProperty(VariableProperty.CONSTANT, Level.FALSE);
        }
        return DONE;
    }

    /*
    we already know that this type is @E2Immutable, but does it contain only constants?
     */
    private Boolean recursivelyConstant(Expression effectivelyFinalValue) {
        if (effectivelyFinalValue.isConstant()) return true;
        ConstructorCall constructorCall;
        if ((constructorCall = effectivelyFinalValue.asInstanceOf(ConstructorCall.class)) != null) {
            if (constructorCall.constructor() == null) return false;
            for (Expression parameter : constructorCall.getParameterExpressions()) {
                if (!parameter.isConstant()) {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(0, // IMPROVE
                            ConditionManager.initialConditionManager(fieldAnalysis.primitives), null);
                    int immutable = evaluationContext.getProperty(parameter, VariableProperty.IMMUTABLE, false, false);
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

    private AnalysisStatus analyseLinked() {
        assert !fieldAnalysis.linkedVariables.isSet();

        int immutable = fieldAnalysis.getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
        if (immutable >= MultiLevel.EFFECTIVELY_E2IMMUTABLE) {
            fieldAnalysis.linkedVariables.set(LinkedVariables.EMPTY);
            log(LINKED_VARIABLES, "Setting linked variables to empty for field {}, @E2Immutable type");
            // finalizer check at assignment only
            return DONE;
        }

        // we ONLY look at the linked variables of fields that have been assigned to
        Optional<MethodInfo> notDefined = allMethodsAndConstructors(true)
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

        Map<Variable, Integer> map = allMethodsAndConstructors(true)
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                .filter(VariableInfo::linkedVariablesIsSet)
                .flatMap(vi -> vi.getLinkedVariables().variables().entrySet().stream())
                .filter(e -> !(e.getKey() instanceof LocalVariableReference)
                        && !(e.getKey() instanceof ReturnVariable)
                        && !(e.getKey() instanceof FieldReference fr && fr.scopeIsThis() && fr.fieldInfo == fieldInfo)) // especially local variable copies of the field itself
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, LinkedVariables::bestValue));

        LinkedVariables linkedVariables = new LinkedVariables(map);
        fieldAnalysis.linkedVariables.set(linkedVariables);
        log(LINKED_VARIABLES, "FA: Set links of {} to [{}]", fqn, linkedVariables);

        // explicitly adding the annotation here; it will not be inspected.
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        Set<Variable> dependent = linkedVariables.variablesAssignedOrDependent().collect(Collectors.toUnmodifiableSet());
        AnnotationExpression linkAnnotation = checkLinks.createLinkAnnotation(e2.linked.typeInfo(), dependent);
        fieldAnalysis.annotations.put(linkAnnotation, !dependent.isEmpty());

        Set<Variable> independent1 = linkedVariables.independent1Variables().collect(Collectors.toUnmodifiableSet());
        AnnotationExpression link1Annotation = checkLinks.createLinkAnnotation(e2.linked1.typeInfo(), independent1);
        fieldAnalysis.annotations.put(link1Annotation, !independent1.isEmpty());

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
            // final if only written in constructors, or methods exclusively reached from constructors
            // for static fields, we'll take ALL methods and constructors (only the static blocks are allowed)
            Stream<MethodAnalyser> stream = methodsForFinal();
            isFinal = stream.filter(m -> fieldInspection.isStatic() ||
                            m.methodInfo.methodResolution.get().partOfConstruction().accessibleFromTheOutside())
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                    .noneMatch(VariableInfo::isAssigned);
        }
        fieldAnalysis.setProperty(VariableProperty.FINAL, Level.fromBool(isFinal));
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final", fqn);

        if (!isFinal) {
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            if (bestType != null) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
                if (typeAnalysis.getProperty(VariableProperty.FINALIZER) == Level.TRUE) {
                    messages.add(Message.newMessage(new Location(fieldInfo), Message.Label.TYPES_WITH_FINALIZER_ONLY_EFFECTIVELY_FINAL));
                }
            }
        }
        return DONE;
    }

    private Stream<MethodAnalyser> methodsForModification() {
        if (fieldInspection.isStatic()) {
            // look at all methods, except MY static blocks
            return Stream.concat(allMethodsAndConstructors(true), otherStaticBlocks());
        }
        // look at all methods and constructors, but ignore my constructors
        return allMethodsAndConstructors(false);
    }

    private Stream<MethodAnalyser> methodsForFinal() {
        if (fieldInspection.isStatic()) {
            return Stream.concat(allMethodsAndConstructors(true), otherStaticBlocks());
        }
        return allMethodsAndConstructors(true);
    }

    private AnalysisStatus analyseModified() {
        int contract = fieldAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE);
        if (contract != Level.DELAY) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, contract);
            return DONE;
        }
        assert fieldAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD) == Level.DELAY;

        boolean isPrimitive = Primitives.isPrimitiveExcludingVoid(fieldInfo.type);
        // too dangerous to catch @E2Immutable because of down-casts
        if (isPrimitive) {
            log(MODIFICATION, "Field {} is @NotModified, since it is final and primitive", fqn);
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
            return DONE;
        }

        Stream<MethodAnalyser> stream = methodsForModification();
        boolean modified = fieldCanBeWrittenFromOutsideThisPrimaryType ||
                stream.flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                        .filter(VariableInfo::isRead)
                        .anyMatch(vi -> vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.TRUE);

        if (modified) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.TRUE);
            log(MODIFICATION, "Mark field {} as @Modified", fqn);
            return DONE;
        }

        // we only consider methods, not constructors (unless the field is static)!
        Stream<MethodAnalyser> stream2 = methodsForModification();
        boolean allContextModificationsDefined = stream2.allMatch(m -> {
            List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, true);
            // AggregatingMethodAnalyser returns empty list, so cast is safe
            return variableInfoList.isEmpty() ||
                    variableInfoList.stream().noneMatch(VariableInfo::isRead) ||
                    variableInfoList.stream().noneMatch(vi -> vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.DELAY);
            //    ((ComputingMethodAnalyser) m).methodLevelData().acceptLinksHaveBeenEstablished(ignoreMyConstructors);
        });

        if (allContextModificationsDefined) {
            fieldAnalysis.setProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD, Level.FALSE);
            log(MODIFICATION, "Mark field {} as @NotModified", fqn);
            return DONE;
        }

        if (Logger.isLogEnabled(DELAYED)) {
            log(DELAYED, "Cannot yet conclude if field {}'s contents have been modified, not all read or links",
                    fqn);
            allMethodsAndConstructors(false).filter(m -> !m.methodInfo.isConstructor &&
                            !m.getFieldAsVariable(fieldInfo, true).isEmpty() &&
                            m.getFieldAsVariable(fieldInfo, true).stream().anyMatch(VariableInfo::isRead) &&
                            ((ComputingMethodAnalyser) m).methodLevelData().linksHaveNotYetBeenEstablished(ignoreMyConstructors))
                    .forEach(m -> log(DELAYED, "... method {} reads the field, but we're still waiting on links to be established", m.methodInfo.name));
        }
        return DELAYS;
    }

    private Expression getVariableValue(Variable variable) {
        FieldReference fieldReference = (FieldReference) variable;
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
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

        log(ANALYSER, "Checking field {}", fqn);

        check(NotNull.class, e2.notNull);
        check(NotNull1.class, e2.notNull1);
        CheckFinalNotModified.check(messages, fieldInfo, Final.class, e2.effectivelyFinal, fieldAnalysis, myTypeAnalyser.typeAnalysis);
        CheckFinalNotModified.check(messages, fieldInfo, NotModified.class, e2.notModified, fieldAnalysis, myTypeAnalyser.typeAnalysis);

        // dynamic type annotations
        check(Container.class, e2.container);

        check(E1Immutable.class, e2.e1Immutable);
        check(E1Container.class, e2.e1Container);
        CheckImmutable.check(messages, fieldInfo, E2Immutable.class, e2.e2Immutable, fieldAnalysis, false, true, true);
        CheckImmutable.check(messages, fieldInfo, E2Container.class, e2.e2Container, fieldAnalysis, false, true, false);
        check(ERContainer.class, e2.eRContainer);

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
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
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
        public Stream<DelayDebugNode> streamNodes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean createDelay(String where, String delayFqn) {
            return FieldAnalyser.super.createDelay(where, delayFqn);
        }

        @Override
        public boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
            return FieldAnalyser.super.translatedDelay(where, delayFromFqn, newDelayFqn);
        }

        @Override
        public boolean foundDelay(String where, String delayFqn) {
            return FieldAnalyser.super.foundDelay(where, delayFqn);
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
        public Location getLocation() {
            return new Location(fieldInfo);
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return new Location(fieldInfo, identifier);
        }

        // rest will be more or less the same as for Methods

        // used in short-circuiting, inline conditional, and lambda

        @Override
        public EvaluationContext child(Expression condition) {
            Set<Variable> conditionIsDelayed = isDelayedSet(condition);
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, conditionIsDelayed,
                    Precondition.empty(getPrimitives()), null);
            return FieldAnalyser.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public int getProperty(Expression value,
                               VariableProperty variableProperty,
                               boolean duringEvaluation,
                               boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression variableValue) {
                Variable variable = variableValue.variable();
                return getProperty(variable, variableProperty);
            }
            try {
                return value.getProperty(this, variableProperty, true);
            } catch(RuntimeException re) {
                LOGGER.error("Caught exception while evaluating expression '{}'", value);
                throw re;
            }
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            if (variable instanceof FieldReference fieldReference) {
                VariableProperty vp = replaceForFieldAnalyser(variableProperty);
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
            throw new UnsupportedOperationException("?? variable " + variable.fullyQualifiedName() + " of " + variable.getClass());
        }

        private VariableProperty replaceForFieldAnalyser(VariableProperty variableProperty) {
            if (variableProperty == VariableProperty.NOT_NULL_EXPRESSION) return VariableProperty.EXTERNAL_NOT_NULL;
            if (variableProperty == VariableProperty.IMMUTABLE) return VariableProperty.EXTERNAL_IMMUTABLE;
            return variableProperty;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            if (variable instanceof FieldReference) {
                return getVariableValue(variable);
            }
            if (variable instanceof This) {
                return ComputingTypeAnalyser.getVariableValue(variable);
            }
            if (variable instanceof ParameterInfo) {
                /*
                 the parameter must belong to the closure somewhere (otherwise you can't have parameters in field expressions,
                 see test SubTypes_5
                 */
                assert closure != null;
                return closure.currentValue(variable, statementTime, forwardEvaluationInfo);
            }

            throw new UnsupportedOperationException("Variable of " + variable.getClass() + " not implemented here");
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            return LinkedVariables.EMPTY; // TODO make sure this is right
        }
    }

    @Override
    protected String where(String componentName) {
        return fqn + ":" + componentName;
    }
}
