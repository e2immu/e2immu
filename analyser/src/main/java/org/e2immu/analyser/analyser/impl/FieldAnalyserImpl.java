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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckFinalNotModified;
import org.e2immu.analyser.analyser.check.CheckImmutable;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.annotation.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.config.AnalyserProgram.Step.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class FieldAnalyserImpl extends AbstractAnalyser implements FieldAnalyser {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FieldAnalyserImpl.class);

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
    public static final String ANALYSE_VALUES = "analyseValues";
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

    public FieldAnalyserImpl(FieldInfo fieldInfo,
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

        AnalyserProgram analyserProgram = nonExpandableAnalyserContext.getAnalyserProgram();
        analyserComponents = new AnalyserComponents.Builder<String, SharedState>(analyserProgram)
                .add(COMPUTE_TRANSPARENT_TYPE, TRANSPARENT, sharedState -> computeTransparentType())
                .add(EVALUATE_INITIALISER, FIELD_FINAL, this::evaluateInitializer)
                .add(ANALYSE_FINAL, FIELD_FINAL, this::analyseFinal)
                .add(ANALYSE_VALUES, sharedState -> analyseValues())
                .add(ANALYSE_IMMUTABLE, this::analyseImmutable)
                .add(ANALYSE_NOT_NULL, sharedState -> analyseNotNull())
                .add(ANALYSE_INDEPENDENT, this::analyseIndependent)
                .add(ANALYSE_CONTAINER, sharedState -> analyseContainer())
                .add(ANALYSE_FINAL_VALUE, sharedState -> analyseFinalValue())
                .add(ANALYSE_CONSTANT, sharedState -> analyseConstant())
                .add(ANALYSE_LINKED, sharedState -> analyseLinked())
                .add(ANALYSE_MODIFIED, sharedState -> analyseModified())
                .add(FIELD_ERRORS, sharedState -> fieldErrors())
                .build();
    }

    @Override
    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    @Override
    public TypeInfo getPrimaryType() {
        return primaryType;
    }

    @Override
    public FieldAnalysisImpl.Builder getFieldAnalysis() {
        return fieldAnalysis;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldAnalyserImpl that = (FieldAnalyserImpl) o;
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
            if (analyser.getMethodInspection().isStaticBlock()) {
                myStaticBlocks.add(analyser);
            } else if (analyser.getMethodInfo().typeInfo == fieldInfo.owner) {
                myMethodsAndConstructors.add(analyser);
            }
        });
        myTypeAnalyser = analyserContext.getTypeAnalyser(fieldInfo.owner);
        this.myMethodsAndConstructors = List.copyOf(myMethodsAndConstructors);
        this.myStaticBlocks = List.copyOf(myStaticBlocks);
    }

    private Stream<MethodAnalyser> otherStaticBlocks() {
        TypeInfo primaryType = myTypeAnalyser.getPrimaryType();
        TypeInspection primaryTypeInspection = analyserContext.getTypeInspection(primaryType);
        return primaryTypeInspection.staticBlocksRecursively(analyserContext)
                .filter(m -> !(m.typeInfo == fieldInfo.owner)) // filter out mine
                .map(analyserContext::getMethodAnalyser);
    }

    // group them per type, because we take only one value per type
    private Stream<List<MethodAnalyser>> staticBlocksPerTypeExcludeMine() {
        TypeInfo primaryType = myTypeAnalyser.getPrimaryType();
        TypeInspection primaryTypeInspection = analyserContext.getTypeInspection(primaryType);
        return primaryTypeInspection.staticBlocksPerType(analyserContext)
                .filter(list -> !list.isEmpty() && !(list.get(0).typeInfo == fieldInfo.owner)) // filter out mine
                .map(list -> list.stream().map(analyserContext::getMethodAnalyser).toList());
    }

    private Stream<MethodAnalyser> allMethodsAndConstructors(boolean alsoMyOwnConstructors) {
        return analyserContext.methodAnalyserStream()
                .filter(ma -> !ma.getMethodInspection().isStaticBlock())
                .filter(ma -> alsoMyOwnConstructors ||
                        !(ma.getMethodInfo().typeInfo == fieldInfo.owner && ma.getMethodInfo().isConstructor))
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

    private AnalysisStatus evaluateInitializer(SharedState sharedState) {
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
                fieldAnalysis.setInitialiserValue(initialiserValue);
                log(FINAL, "Set initialiser of field {} to {}", fqn, evaluationResult.value());
                if (evaluationResult.causesOfDelay().isDelayed()) {
                    return evaluationResult.causesOfDelay();
                }
                return DONE;
            }
        }
        Expression nullValue = ConstantExpression.nullValue(analyserContext.getPrimitives(), fieldInfo.type.bestTypeInfo());
        fieldAnalysis.setInitialiserValue(nullValue);
        return DONE;
    }


    private AnalysisStatus computeTransparentType() {
        assert fieldAnalysis.isTransparentType().isDelayed();
        CausesOfDelay causes = myTypeAnalyser.getTypeAnalysis().hiddenContentTypeStatus();
        if (causes.isDelayed()) {
            return causes;
        }
        boolean transparent = myTypeAnalyser.getTypeAnalysis().getTransparentTypes().contains(fieldInfo.type);
        fieldAnalysis.setTransparentType(DV.fromBoolDv(transparent));
        return DONE;
    }

    private AnalysisStatus analyseContainer() {
        if (fieldAnalysis.getPropertyFromMapDelayWhenAbsent(Property.CONTAINER).isDone()) return DONE;

        TypeInfo bestType = fieldInfo.type.bestTypeInfo();
        if (bestType == null || !bestType.isAbstract()) {
            // value does not matter
            fieldAnalysis.setProperty(Property.CONTAINER, DV.FALSE_DV);
            return DONE;
        }

        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
        DV typeContainer = typeAnalysis.getProperty(Property.CONTAINER);
        if (typeContainer.isDelayed()) return typeContainer.causesOfDelay();
        if (typeContainer.valueIsTrue()) {
            fieldAnalysis.setProperty(Property.CONTAINER, DV.TRUE_DV);
            return DONE;
        }

        // only worth doing something when the field is statically not a container
        DV parameterModification = methodsForModification()
                .flatMap(method -> method.getParameterAnalysers().stream())
                .map(pa -> pa.getParameterAnalysis().getProperty(Property.MODIFIED_VARIABLE))
                .reduce(Property.MODIFIED_VARIABLE.falseDv, DV::max);
        if (parameterModification.isDelayed()) {
            log(MODIFICATION, "Delaying @Container on field {}, some parameters have no @Modified status yet",
                    fqn);
            return parameterModification.causesOfDelay();
        }
        boolean allParametersNotModified = parameterModification.valueIsFalse();
        log(MODIFICATION, "Set @Container on {} to {}", fqn, allParametersNotModified);
        fieldAnalysis.setProperty(Property.CONTAINER, DV.fromBoolDv(allParametersNotModified));
        return DONE;
    }

    private AnalysisStatus analyseNotNull() {
        if (fieldAnalysis.getProperty(Property.EXTERNAL_NOT_NULL).isDone()) return DONE;

        if (fieldInfo.type.isPrimitiveExcludingVoid()) {
            log(NOT_NULL, "Field {} is effectively not null, it is of primitive type", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            return DONE;
        }

        DV isFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (isFinal.isDelayed()) {
            log(DELAYED, "Delaying @NotNull on {} until we know about @Final", fqn);
            return isFinal.causesOfDelay();
        }
        if (isFinal.valueIsFalse() && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be @NotNull: it be assigned to from outside this primary type",
                    fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.NULLABLE_DV);
            return DONE;
        }

        /*
        The choice here is between deciding the @NotNull based on the value and the context of
        - the construction methods only
        - all methods

        There are cases to be made for both situations; we simply avoid making a decision here.
        Note that as soon as bestOverContext is better that ENN, we still cannot skip the @NotNull of the values,
        because the value would contain the contracted @NotNull at a parameter after assignment.
         */
        boolean computeContextPropertiesOverAllMethods = analyserContext.getConfiguration().analyserConfiguration()
                .computeContextPropertiesOverAllMethods();

        DV bestOverContext = allMethodsAndConstructors(true)
                .filter(m -> computeContextPropertiesOverAllMethods ||
                        m.getMethodInfo().methodResolution.get().partOfConstruction() == MethodResolution.CallStatus.PART_OF_CONSTRUCTION)
                .peek(m -> LOGGER.info("Considering " + m.getMethodInfo().fullyQualifiedName))
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                .map(vi -> vi.getProperty(Property.CONTEXT_NOT_NULL))
                .reduce(MultiLevel.NULLABLE_DV, DV::max);
        if (bestOverContext.isDelayed()) {
            log(DELAYED, "Delay @NotNull on {}, waiting for CNN", fqn);
            return bestOverContext.causesOfDelay();
        }

        if (fieldAnalysis.valuesStatus().isDelayed()) {
            log(DELAYED, "Delay @NotNull until all values are known");
            return fieldAnalysis.valuesStatus();
        }
        assert fieldAnalysis.getValues().size() > 0;

        DV worstOverValues = fieldAnalysis.getValues().stream()
                .map(proxy -> proxy.getProperty(Property.NOT_NULL_EXPRESSION))
                .reduce(DV.MAX_INT_DV, DV::min);
        assert worstOverValues != DV.MAX_INT_DV;

        if (computeContextPropertiesOverAllMethods && worstOverValues.lt(bestOverContext)) {
            // see Basics_2b; we need the setter to run before the add-method can be called
            Message message = Message.newMessage(fieldAnalysis.location(), Message.Label.FIELD_INITIALIZATION_NOT_NULL_CONFLICT);
            messages.add(message);
        }
        DV finalNotNullValue = worstOverValues.max(bestOverContext);
        fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, finalNotNullValue);
        return AnalysisStatus.of(finalNotNullValue.causesOfDelay());
    }

    private AnalysisStatus fieldErrors() {
        if (fieldInspection.getModifiers().contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = allMethodsAndConstructors(false).anyMatch(this::isReadInMethod);
                if (!readInMethods) {
                    messages.add(Message.newMessage(fieldInfo.newLocation(), Message.Label.PRIVATE_FIELD_NOT_READ));
                }
                return DONE;
            }
        } else {
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            if (effectivelyFinal.valueIsFalse()) {
                // only react once we're certain the variable is not effectively final
                // error, unless we're in a record
                if (!fieldInfo.owner.isPrivateNested()) {
                    messages.add(Message.newMessage(fieldInfo.newLocation(), Message.Label.NON_PRIVATE_FIELD_NOT_FINAL));
                } // else: nested private types can have fields the way they like it
                return DONE;
            } else if (effectivelyFinal.isDelayed()) {
                log(DELAYED, "Not yet ready to decide on non-private non-final");
                return effectivelyFinal.causesOfDelay();
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
        if (fieldAnalysis.getProperty(Property.INDEPENDENT).isDone()) return DONE;

        DV staticallyIndependent = analyserContext.defaultIndependent(fieldInfo.type);
        if (staticallyIndependent.equals(MultiLevel.INDEPENDENT_DV)) {
            log(INDEPENDENCE, "Field {} set to @Independent: static type",
                    fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }

        DV immutable = fieldAnalysis.getPropertyFromMapDelayWhenAbsent(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed()) {
            log(DELAYED, "Field {} independent delayed: wait for immutable", fieldInfo.fullyQualifiedName());
            return immutable.causesOfDelay();
        }
        int immutableLevel = MultiLevel.level(immutable);
        if (immutableLevel >= MultiLevel.Level.IMMUTABLE_2.level) {
            DV independent = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
            log(INDEPENDENCE, "Field {} set to {}, direct correspondence to (dynamically) immutable",
                    fieldInfo.fullyQualifiedName(), independent);
            fieldAnalysis.setProperty(Property.INDEPENDENT, independent);
            return DONE;
        }

        if (staticallyIndependent.isDelayed()) {
            log(DELAYED, "Field {} independent delayed: wait for type independence of {}",
                    fieldInfo.fullyQualifiedName(), fieldInfo.type);
            return staticallyIndependent.causesOfDelay();
        }
        fieldAnalysis.setProperty(Property.INDEPENDENT, staticallyIndependent);
        return DONE;
    }

    /*
    method modelled to that of analyseNotNull.
     */
    private AnalysisStatus analyseImmutable(SharedState sharedState) {
        if (fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE).isDone()) return DONE;

        DV isFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (isFinal.isDelayed()) {
            log(DELAYED, "Delaying @Immutable on {} until we know about @Final", fqn);
            return isFinal.causesOfDelay();
        }
        if (isFinal.valueIsFalse() && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            log(NOT_NULL, "Field {} cannot be immutable: it is not @Final," +
                    " and it can be assigned to from outside this primary type", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, MultiLevel.MUTABLE_DV);
            return DONE;
        }

        DV staticallyImmutable = analyserContext.defaultImmutable(fieldInfo.type, false);
        if (staticallyImmutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
            log(IMMUTABLE_LOG, "Field {} is statically @ERImmutable", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, staticallyImmutable);
            return DONE; // cannot be improved
        }

        DV finalImmutable;

        CausesOfDelay valuesStatus = fieldAnalysis.valuesStatus();
        if (valuesStatus.isDelayed()) {
            log(DELAYED, "Delaying @Immutable of field {}, non-parameter values not yet known", fqn);
            return valuesStatus;
        }
        CausesOfDelay allLinksStatus = fieldAnalysis.allLinksHaveBeenEstablished();
        if (allLinksStatus.isDelayed() && isFinal.valueIsFalse()) {
                /* if the field is effectively final, we don't need links established because all assignment
                 occurs in the constructor
                 */
            log(DELAYED, "Delaying @Immutable of field {}, not all links have been established", fqn);
            return allLinksStatus;
        }
        DV worstOverValuesPrep = fieldAnalysis.getValues().stream()
                .filter(proxy -> !(proxy.getValue() instanceof NullConstant))
                .map(proxy -> proxy.getProperty(Property.IMMUTABLE))
                .reduce(DV.MAX_INT_DV, DV::min);
        DV worstOverValues = worstOverValuesPrep == DV.MAX_INT_DV ? MultiLevel.MUTABLE_DV : worstOverValuesPrep;
        if (worstOverValues.isDelayed()) {
            log(DELAYED, "Delay @Immutable on {}, waiting for values", fqn);
            return worstOverValues.causesOfDelay();
        }

        // if we have an assignment to an eventually immutable variable, but somehow the construction context enforces "after"
        // that should be taken into account (see EventuallyImmutableUtil_2 vs E2InContext_2)
        if (MultiLevel.isBefore(worstOverValues)) {
            DV bestOverContext = myMethodsAndConstructors.stream()
                    .filter(m -> m.getMethodInfo().isConstructor || m.getMethodInfo().methodResolution.get().partOfConstruction()
                            == MethodResolution.CallStatus.PART_OF_CONSTRUCTION)
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                    .map(vi -> vi.getProperty(Property.CONTEXT_IMMUTABLE))
                    .reduce(MultiLevel.MUTABLE_DV, DV::max);
            if (bestOverContext.isDelayed()) {
                log(DELAYED, "Delay @Immutable on {}, waiting for context immutable", fqn);
                return bestOverContext.causesOfDelay();
            }
            finalImmutable = bestOverContext.max(worstOverValues);
        } else {
            finalImmutable = worstOverValues;
        }

        // See E2InContext_0,1 (field is not private, so if it's before, someone else can change it into after)
        DV correctedImmutable = correctForExposureBefore(finalImmutable);
        if (correctedImmutable.isDelayed()) {
            log(DELAYED, "Delay @Immutable on {}, waiting for exposure to decide on @BeforeMark", fqn);
            // still, we're already marking
            fieldAnalysis.setProperty(Property.PARTIAL_EXTERNAL_IMMUTABLE, finalImmutable);
            return correctedImmutable.causesOfDelay();
        }
        log(IMMUTABLE_LOG, "Set immutable on field {} to value {}", fqn, correctedImmutable);
        fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, correctedImmutable);

        return DONE;
    }

    private DV correctForExposureBefore(DV immutable) {
        if (!immutable.equals(MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV) &&
                !immutable.equals(MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK_DV)) {
            return immutable;
        }
        DV corrected = immutable.equals(MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK_DV) ? MultiLevel.EVENTUALLY_E1IMMUTABLE_DV :
                MultiLevel.EVENTUALLY_E2IMMUTABLE_DV;
        if (fieldInfo.isAccessibleOutsideOfPrimaryType()) {
            return corrected;
        }
        // check exposed via return values of methods
        // FIXME ignoreMyConstructors is a delay breaking measure, needs re-implementing
        CausesOfDelay delayLinkedVariables = myMethodsAndConstructors.stream()
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .filter(ma -> !ma.getMethodInfo().isPrivate() && ((ComputingMethodAnalyser) ma).methodLevelData() != null)
                .filter(ma -> !ma.getMethodAnalysis().getProperty(Property.FINALIZER).valueIsTrue())
                .map(ma -> ((ComputingMethodAnalyser) ma).methodLevelData().linksHaveNotYetBeenEstablished(ignoreMyConstructors))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delayLinkedVariables.isDelayed()) {
            log(DELAYED, "Exposure computation on {} delayed by links: {}", fqn, delayLinkedVariables);
            return delayLinkedVariables;
        }
        FieldReference me = new FieldReference(analyserContext, fieldInfo);
        boolean linkedToMe = myMethodsAndConstructors.stream()
                .filter(ma -> ma instanceof ComputingMethodAnalyser)
                .filter(ma -> !ma.getMethodInfo().isPrivate() && ((ComputingMethodAnalyser) ma).methodLevelData() != null)
                .filter(ma -> !ma.getMethodAnalysis().getProperty(Property.FINALIZER).valueIsTrue())
                .anyMatch(ma -> {
                    if (ma.getMethodInfo().hasReturnValue()) {
                        LinkedVariables linkedVariables = ((ComputingMethodAnalyser) ma).getReturnAsVariable().getLinkedVariables();
                        if (linkedVariables.value(me) == LinkedVariables.DEPENDENT_DV) return true;
                    }
                    return ma.getMethodAnalysis().getLastStatement().variableStream()
                            .filter(vi -> vi.variable() instanceof ParameterInfo)
                            .anyMatch(vi -> vi.getLinkedVariables().contains(me));
                });
        return linkedToMe ? corrected : immutable;
    }


    record OccursAndDelay(boolean occurs, int occurrenceCountForError, CausesOfDelay delay) {
    }

    // NOTE: we're also considering non-private methods here, like setters: IS THIS WISE?

    private OccursAndDelay occursInAllConstructors(List<FieldAnalysisImpl.ValueAndPropertyProxy> values,
                                                   boolean ignorePrivateConstructors) {
        boolean occurs = true;
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        int occurrenceCountForError = 0;
        for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
            DV finalizer = methodAnalyser.getMethodAnalysis().getProperty(Property.FINALIZER);
            assert finalizer.isDone();
            if (finalizer.valueIsFalse() && (!methodAnalyser.getMethodInspection().isPrivate() ||
                    methodAnalyser.getMethodInfo().isConstructor && !ignorePrivateConstructors)) {
                boolean added = false;
                for (VariableInfo vi : methodAnalyser.getFieldAsVariableAssigned(fieldInfo)) {
                    Expression expression = vi.getValue();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodAnalyser.getMethodInfo().fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    values.add(new FieldAnalysisImpl.ValueAndPropertyProxy() {
                        @Override
                        public Expression getValue() {
                            return vi.getValue();
                        }

                        @Override
                        public DV getProperty(Property property) {
                            return vi.getProperty(property);
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
                    if (!fieldInspection.isStatic() && methodAnalyser.getMethodInfo().isConstructor) {
                        // we'll warn for the combination of field initializer, and occurrence in at least one constructor
                        occurrenceCountForError++;
                    }
                    added = true;
                    if (vi.isDelayed()) {
                        log(DELAYED, "Delay consistent value for field {} because of {}", fqn,
                                vi.getValue().toString());
                        delays = delays.merge(vi.getValue().causesOfDelay());
                    }
                }
                if (!added && methodAnalyser.getMethodInfo().isConstructor) {
                    occurs = false;
                }
            }
        }
        return new OccursAndDelay(occurs, occurrenceCountForError, delays);
    }

    private OccursAndDelay occursInStaticBlocks(List<MethodAnalyser> staticBlocks, List<FieldAnalysisImpl.ValueAndPropertyProxy> values) {
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        FieldAnalysisImpl.ValueAndPropertyProxy latestBlock = null;
        for (MethodAnalyser methodAnalyser : staticBlocks) {
            for (VariableInfo vi : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                if (vi.isAssigned()) {
                    Expression expression = vi.getValue();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodAnalyser.getMethodInfo().fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    latestBlock = new FieldAnalysisImpl.ValueAndPropertyProxy() {
                        @Override
                        public Expression getValue() {
                            return vi.getValue();
                        }

                        @Override
                        public DV getProperty(Property property) {
                            return vi.getProperty(property);
                        }

                        @Override
                        public LinkedVariables getLinkedVariables() {
                            return vi.getLinkedVariables();
                        }

                        @Override
                        public String toString() {
                            return "STATIC_BLOCK:" + getValue().toString();
                        }
                    };
                    delays = delays.merge(vi.getValue().causesOfDelay());
                }
            }
        }
        if (latestBlock != null) {
            values.add(latestBlock);
            if (delays.isDelayed()) {
                log(DELAYED, "Delaying initialization of field {} in static block", fieldInfo.fullyQualifiedName());
            }
            return new OccursAndDelay(true, 1, delays);
        }
        return new OccursAndDelay(false, 0, CausesOfDelay.EMPTY);
    }

    private AnalysisStatus analyseValues() {
        assert fieldAnalysis.valuesStatus().isDelayed();
        List<FieldAnalysisImpl.ValueAndPropertyProxy> values = new ArrayList<>();
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        if (haveInitialiser) {
            delays = fieldAnalysis.getInitializerValue().causesOfDelay();
            EvaluationContext ec = new EvaluationContextImpl(0,
                    ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null);
            values.add(new FieldAnalysisImpl.ValueAndPropertyProxy() {
                @Override
                public Expression getValue() {
                    return fieldAnalysis.getInitializerValue();
                }

                @Override
                public LinkedVariables getLinkedVariables() {
                    return fieldAnalysis.getLinkedVariables();
                }

                @Override
                public DV getProperty(Property property) {
                    return ec.getProperty(getValue(), property, false, false);
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
            delays = delays.merge(oad.delay);

            if (fieldInspection.isStatic()) {
                // also look in my static blocks
                OccursAndDelay myBlocks = occursInStaticBlocks(myStaticBlocks, values);
                delays = delays.merge(myBlocks.delay);
                occursInAllConstructorsOrOneStaticBlock = myBlocks.occurs;

                // and add values of other static blocks
                CausesOfDelay staticDelays = staticBlocksPerTypeExcludeMine().map(list -> {
                    OccursAndDelay staticOad = occursInStaticBlocks(list, values);
                    return staticOad.delay;
                }).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
                delays = delays.merge(staticDelays);
            }
        }
        if (delays.isDone() && haveInitialiser && cannotGoTogetherWithInitialiser) {
            Message message = Message.newMessage(fieldInfo.newLocation(), Message.Label.UNNECESSARY_FIELD_INITIALIZER);
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
                public LinkedVariables getLinkedVariables() {
                    return LinkedVariables.EMPTY;
                }

                @Override
                public DV getProperty(Property property) {
                    return getValue().getProperty(null, property, false);
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
        return AnalysisStatus.of(delays);
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
        assert fieldAnalysis.getValue().isDelayed();

        if (!fieldAnalysis.getProperty(Property.FINAL).valueIsTrue()) {
            fieldAnalysis.setValue(new UnknownExpression(fieldInfo.type, UnknownExpression.VARIABLE));
            return DONE;
        }
        CausesOfDelay valuesStatus = fieldAnalysis.valuesStatus();
        if (valuesStatus.isDelayed()) {
            log(DELAYED, "Delaying final value, have no values yet for field " + fqn);
            fieldAnalysis.setValue(DelayedExpression.forInitialFieldValue(fieldInfo,
                    fieldAnalysis.getLinkedVariables(),
                    fieldAnalysis.valuesStatus()));
            return valuesStatus;
        }
        List<FieldAnalysisImpl.ValueAndPropertyProxy> values = fieldAnalysis.getValues();


        // compute and set the combined value
        Expression effectivelyFinalValue;

        // suppose there are 2 constructors, and the field gets exactly the same value...
        List<Expression> expressions = values.stream().map(FieldAnalysisImpl.ValueAndPropertyProxy::getValue).toList();
        Set<Expression> set = new HashSet<>(expressions);

        if (set.size() == 1) {
            FieldAnalysisImpl.ValueAndPropertyProxy proxy = values.get(0);
            Expression expression = proxy.getValue();
            ConstructorCall constructorCall;
            if ((constructorCall = expression.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                // now the state of the new object may survive if there are no modifying methods called,
                // but that's too early to know now
                DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
                if (immutable.isDelayed()) {
                    // see analyseImmutable, @BeforeMark
                    immutable = fieldAnalysis.getProperty(Property.PARTIAL_EXTERNAL_IMMUTABLE);
                }
                boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;

                if (immutable.isDelayed() && !fieldOfOwnType) {
                    log(DELAYED, "Waiting with effectively final value  until decision on @E2Immutable for {}", fqn);
                    //fieldAnalysis.setProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY, Level.TRUE);
                    return immutable.causesOfDelay();
                }
                boolean downgradeFromNewInstanceWithConstructor = !fieldOfOwnType && immutable.lt(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV);
                if (downgradeFromNewInstanceWithConstructor) {
                    Map<Property, DV> valueProperties = Map.of(
                            Property.NOT_NULL_EXPRESSION, proxy.getProperty(Property.NOT_NULL_EXPRESSION),
                            Property.IMMUTABLE, proxy.getProperty(Property.IMMUTABLE),
                            Property.INDEPENDENT, proxy.getProperty(Property.INDEPENDENT),
                            Property.CONTAINER, proxy.getProperty(Property.CONTAINER),
                            Property.IDENTITY, proxy.getProperty(Property.IDENTITY)
                    );
                    effectivelyFinalValue = constructorCall.removeConstructor(valueProperties);
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

        if (effectivelyFinalValue.isDelayed()) {
            log(FINAL, "Delaying final value of field {}", fieldInfo.fullyQualifiedName());
            return effectivelyFinalValue.causesOfDelay();
        }

        // check constant, but before we set the effectively final value

        log(FINAL, "Setting final value of effectively final field {} to {}", fqn, effectivelyFinalValue);
        fieldAnalysis.setValue(effectivelyFinalValue);
        return DONE;
    }

    private AnalysisStatus analyseConstant() {
        if (fieldAnalysis.getProperty(Property.CONSTANT).isDone()) return DONE;

        Expression value = fieldAnalysis.getValue();
        if (value.isDelayed()) {
            log(DELAYED, "Delaying @Constant, effectively final value not yet set");
            return value.causesOfDelay();
        }

        if (value.isUnknown()) {
            log(FINAL, "@Constant of {} false, because not final", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(Property.CONSTANT, DV.FALSE_DV);
            return DONE;
        }

        boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;
        DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed() && !fieldOfOwnType) {
            log(DELAYED, "Waiting with @Constant until decision on @E2Immutable for {}",
                    fqn);
            return immutable.causesOfDelay();
        }

        DV recursivelyConstant;
        if (!fieldOfOwnType && !MultiLevel.isAtLeastEventuallyE2Immutable(immutable))
            recursivelyConstant = DV.FALSE_DV;
        else recursivelyConstant = recursivelyConstant(value);
        if (recursivelyConstant.isDelayed()) {
            log(DELAYED, "Delaying @Constant because of recursively constant computation on value {} of {}",
                    fqn, value);
            return recursivelyConstant.causesOfDelay();
        }

        if (recursivelyConstant.valueIsTrue()) {
            // directly adding the annotation; it will not be used for inspection
            E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            log(FINAL, "Added @Constant annotation on field {}", fqn);
        }
        fieldAnalysis.setProperty(Property.CONSTANT, recursivelyConstant);
        return DONE;
    }

    /*
    we already know that this type is @E2Immutable, but does it contain only constants?
     */
    private DV recursivelyConstant(Expression effectivelyFinalValue) {
        if (effectivelyFinalValue.isConstant()) return DV.TRUE_DV;
        ConstructorCall constructorCall;
        if ((constructorCall = effectivelyFinalValue.asInstanceOf(ConstructorCall.class)) != null) {
            if (constructorCall.constructor() == null) return DV.FALSE_DV;
            for (Expression parameter : constructorCall.getParameterExpressions()) {
                if (!parameter.isConstant()) {
                    EvaluationContext evaluationContext = new EvaluationContextImpl(0, // IMPROVE
                            ConditionManager.initialConditionManager(fieldAnalysis.primitives), null);
                    DV immutable = evaluationContext.getProperty(parameter, Property.IMMUTABLE, false, false);
                    if (immutable.isDelayed()) return immutable;
                    if (!MultiLevel.isEffectivelyNotNull(immutable)) return DV.FALSE_DV;
                    DV recursively = recursivelyConstant(parameter);
                    if (!recursively.valueIsTrue()) return recursively;
                }
            }
            return DV.TRUE_DV;
        }
        return DV.FALSE_DV;
    }

    private AnalysisStatus analyseLinked() {
        assert fieldAnalysis.linkedVariables.isVariable();
/*
        DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
        if (MultiLevel.isAtLeastEffectivelyE2Immutable(immutable)) {
            fieldAnalysis.setLinkedVariables(LinkedVariables.EMPTY);
            log(LINKED_VARIABLES, "Setting linked variables to empty for field {}, @E2Immutable type");
            // finalizer check at assignment only
            return DONE;
        }
*/
        // we ONLY look at the linked variables of fields that have been assigned to
        CausesOfDelay causesOfDelay = allMethodsAndConstructors(true)
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false)
                        .filter(VariableInfo::isAssigned)
                        .map(vi -> vi.getLinkedVariables().causesOfDelay()))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (causesOfDelay.isDelayed()) {
            log(DELAYED, "LinkedVariables not yet set for {}", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setLinkedVariables(LinkedVariables.delayedEmpty(causesOfDelay));
            return causesOfDelay.causesOfDelay();
        }

        Map<Variable, DV> map = allMethodsAndConstructors(true)
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                .filter(VariableInfo::linkedVariablesIsSet)
                .flatMap(vi -> vi.getLinkedVariables().variables().entrySet().stream())
                .filter(e -> !(e.getKey() instanceof LocalVariableReference)
                        && !(e.getKey() instanceof ReturnVariable)
                        && !(e.getKey() instanceof FieldReference fr && fr.scopeIsThis() && fr.fieldInfo == fieldInfo)) // especially local variable copies of the field itself
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, DV::max));

        LinkedVariables linkedVariables = new LinkedVariables(map);
        fieldAnalysis.setLinkedVariables(linkedVariables);
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
        assert fieldAnalysis.getProperty(Property.FINAL).isDelayed();
        assert sharedState.iteration == 0;

        if (fieldInfo.isExplicitlyFinal()) {
            fieldAnalysis.setProperty(Property.FINAL, DV.TRUE_DV);
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
                            m.getMethodInfo().methodResolution.get().partOfConstruction().accessibleFromTheOutside())
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo, false))
                    .noneMatch(VariableInfo::isAssigned);
        }
        fieldAnalysis.setProperty(Property.FINAL, DV.fromBoolDv(isFinal));
        log(FINAL, "Mark field {} as " + (isFinal ? "" : "not ") +
                "effectively final", fqn);

        if (!isFinal) {
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            if (bestType != null) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
                if (typeAnalysis.getProperty(Property.FINALIZER).valueIsTrue()) {
                    messages.add(Message.newMessage(fieldInfo.newLocation(), Message.Label.TYPES_WITH_FINALIZER_ONLY_EFFECTIVELY_FINAL));
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
        DV contract = fieldAnalysis.getProperty(Property.MODIFIED_VARIABLE);
        if (contract.isDone()) {
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, contract);
            return DONE;
        }
        assert fieldAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD).isDelayed();

        boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();
        // too dangerous to catch @E2Immutable because of down-casts
        if (isPrimitive) {
            log(MODIFICATION, "Field {} is @NotModified, since it is final and primitive", fqn);
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
            return DONE;
        }

        Stream<MethodAnalyser> stream = methodsForModification();
        boolean modified = fieldCanBeWrittenFromOutsideThisPrimaryType ||
                stream.flatMap(m -> m.getFieldAsVariableStream(fieldInfo, true))
                        .filter(VariableInfo::isRead)
                        .anyMatch(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).valueIsTrue());

        if (modified) {
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.TRUE_DV);
            log(MODIFICATION, "Mark field {} as @Modified", fqn);
            return DONE;
        }

        // we only consider methods, not constructors (unless the field is static)!
        Stream<MethodAnalyser> stream2 = methodsForModification();
        CausesOfDelay contextModifications = stream2.flatMap(m -> {
            List<VariableInfo> variableInfoList = m.getFieldAsVariable(fieldInfo, true);
            return variableInfoList.stream()
                    .filter(VariableInfo::isRead)
                    .map(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).causesOfDelay());
        }).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        if (contextModifications.isDone()) {
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
            log(MODIFICATION, "Mark field {} as @NotModified", fqn);
            return DONE;
        }
        return contextModifications;
    }

    private Expression getVariableValue(Variable variable) {
        FieldReference fieldReference = (FieldReference) variable;
        FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fieldReference.fieldInfo);
        DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (effectivelyFinal.isDelayed()) {
            return DelayedVariableExpression.forField(fieldReference,
                    new VariableCause(fieldReference, fieldInfo.newLocation(), CauseOfDelay.Cause.FIELD_FINAL));
        }
        if (effectivelyFinal.valueIsFalse()) {
            return new VariableExpression(variable);
        }
        Expression effectivelyFinalValue = fieldAnalysis.getValue();
        return Objects.requireNonNullElseGet(effectivelyFinalValue, () -> new VariableExpression(variable));
    }

    @Override
    public void check() {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();

        AnalyserProgram analyserProgram = analyserContext.getAnalyserProgram();
        if (analyserProgram.accepts(FIELD_FINAL)) {
            CheckFinalNotModified.check(messages, fieldInfo, Final.class, e2.effectivelyFinal, fieldAnalysis,
                    myTypeAnalyser.getTypeAnalysis());
            check(org.e2immu.annotation.Variable.class, e2.variableField);
        }
        if (analyserProgram.accepts(ALL)) {
            log(ANALYSER, "Checking field {}", fqn);

            check(NotNull.class, e2.notNull);
            check(NotNull1.class, e2.notNull1);

            CheckFinalNotModified.check(messages, fieldInfo, NotModified.class, e2.notModified, fieldAnalysis,
                    myTypeAnalyser.getTypeAnalysis());

            // dynamic type annotations
            check(Container.class, e2.container);

            check(E1Immutable.class, e2.e1Immutable);
            check(E1Container.class, e2.e1Container);
            CheckImmutable.check(messages, fieldInfo, E2Immutable.class, e2.e2Immutable, fieldAnalysis, false, true, true);
            CheckImmutable.check(messages, fieldInfo, E2Container.class, e2.e2Container, fieldAnalysis, false, true, false);
            check(ERContainer.class, e2.eRContainer);

            check(Modified.class, e2.modified);
            check(Nullable.class, e2.nullable);

            checkLinks.checkLinksForFields(messages, fieldInfo, fieldAnalysis);
            checkLinks.checkLink1sForFields(messages, fieldInfo, fieldAnalysis);

            checkConstant.checkConstantForFields(messages, fieldInfo, fieldAnalysis);
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(fieldAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent -> {
            Message error = Message.newMessage(fieldInfo.newLocation(),
                    mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                            : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName());
            messages.add(error);
        });
    }

    @Override
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
        public Location getLocation() {
            return fieldInfo.newLocation();
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return new LocationImpl(fieldInfo, identifier);
        }

        // rest will be more or less the same as for Methods

        // used in short-circuiting, inline conditional, and lambda

        @Override
        public EvaluationContext child(Expression condition) {
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition, condition.causesOfDelay(),
                    Precondition.empty(getPrimitives()), null);
            return FieldAnalyserImpl.this.new EvaluationContextImpl(iteration, cm, closure);
        }

        @Override
        public DV getProperty(Expression value,
                              Property property,
                              boolean duringEvaluation,
                              boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression variableValue) {
                Variable variable = variableValue.variable();
                return getProperty(variable, property);
            }
            try {
                return value.getProperty(this, property, true);
            } catch (RuntimeException re) {
                LOGGER.error("Caught exception while evaluating expression '{}'", value);
                throw re;
            }
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            if (variable instanceof FieldReference fieldReference) {
                Property vp = replaceForFieldAnalyser(property);
                return getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo).getProperty(vp);
            }
            if (variable instanceof This thisVariable) {
                return getAnalyserContext().getTypeAnalysis(thisVariable.typeInfo).getProperty(property);
            }
            if (variable instanceof ParameterInfo parameterInfo) {
                Property vp = property == Property.NOT_NULL_EXPRESSION
                        ? Property.NOT_NULL_PARAMETER : property;
                return getAnalyserContext().getParameterAnalysis(parameterInfo).getProperty(vp);
            }
            throw new UnsupportedOperationException("?? variable " + variable.fullyQualifiedName() + " of " + variable.getClass());
        }

        private Property replaceForFieldAnalyser(Property property) {
            if (property == Property.NOT_NULL_EXPRESSION) return Property.EXTERNAL_NOT_NULL;
            if (property == Property.IMMUTABLE) return Property.EXTERNAL_IMMUTABLE;
            return property;
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
}