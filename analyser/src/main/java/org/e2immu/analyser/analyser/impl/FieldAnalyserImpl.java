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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.check.CheckConstant;
import org.e2immu.analyser.analyser.check.CheckFinalNotModified;
import org.e2immu.analyser.analyser.check.CheckImmutable;
import org.e2immu.analyser.analyser.check.CheckLinks;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analyser.nonanalyserimpl.ExpandableAnalyserContextImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ValueAndPropertyProxy;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.resolver.SortedType;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.annotation.*;
import org.e2immu.support.Either;
import org.e2immu.support.EventuallyFinal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.AnalysisStatus.NOT_YET_EXECUTED;
import static org.e2immu.analyser.analyser.Property.CONTAINER;
import static org.e2immu.analyser.analyser.Property.EXTERNAL_CONTAINER;
import static org.e2immu.analyser.config.AnalyserProgram.Step.*;

public class FieldAnalyserImpl extends AbstractAnalyser implements FieldAnalyser {
    private static final Logger LOGGER = LoggerFactory.getLogger(FieldAnalyserImpl.class);

    // analyser components, constants are used in tests and delay debugging
    public static final String COMPUTE_TRANSPARENT_TYPE = "computeTransparentType";
    public static final String ANONYMOUS_TYPE_ANALYSER = "anonymousTypeAnalyser";
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
    public final EventuallyFinal<PrimaryTypeAnalyser> anonymousTypeAnalyser = new EventuallyFinal<>();
    private final boolean fieldCanBeWrittenFromOutsideThisPrimaryType;
    private final AnalyserComponents<String, SharedState> analyserComponents;
    private final CheckConstant checkConstant;
    private final CheckLinks checkLinks;
    private final boolean haveInitialiser;
    private final boolean acrossAllMethods;

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
                             AnalyserContext nonExpandableAnalyserContext) {
        super("Field " + fieldInfo.name, new ExpandableAnalyserContextImpl(nonExpandableAnalyserContext));
        this.checkConstant = new CheckConstant(analyserContext.getPrimitives(), analyserContext.getE2ImmuAnnotationExpressions());
        this.checkLinks = new CheckLinks(analyserContext, analyserContext.getE2ImmuAnnotationExpressions());

        this.acrossAllMethods = analyserContext.getConfiguration().analyserConfiguration().computeFieldAnalyserAcrossAllMethods();

        this.fieldInfo = fieldInfo;
        ignoreMyConstructors = w -> w instanceof MethodInfo methodInfo
                && methodInfo.isConstructor
                && methodInfo.typeInfo == fieldInfo.owner;
        fqn = fieldInfo.fullyQualifiedName();
        fieldInspection = fieldInfo.fieldInspection.get();
        fieldAnalysis = new FieldAnalysisImpl.Builder(analyserContext.getPrimitives(), analyserContext, fieldInfo, ownerTypeAnalysis);
        this.primaryType = primaryType;
        fieldCanBeWrittenFromOutsideThisPrimaryType = fieldInfo.isAccessibleOutsideOfPrimaryType() &&
                !fieldInfo.isExplicitlyFinal() && !fieldInfo.owner.isPrivateOrEnclosingIsPrivate();
        haveInitialiser = fieldInspection.fieldInitialiserIsSet() && fieldInspection.getFieldInitialiser().initialiser() != EmptyExpression.EMPTY_EXPRESSION;
        AnalyserProgram analyserProgram = nonExpandableAnalyserContext.getAnalyserProgram();

        AnalysisStatus.AnalysisResultSupplier<SharedState> anonymousTypeAnalyser = (sharedState) -> {
            AnalyserResult analyserResult = runAnonymousTypeAnalyser(sharedState);
            analyserResultBuilder.add(analyserResult);
            return analyserResult.analysisStatus();
        };

        analyserComponents = new AnalyserComponents.Builder<String, SharedState>(analyserProgram)
                .add(ANONYMOUS_TYPE_ANALYSER, INITIALISE, anonymousTypeAnalyser)
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
    public Stream<Message> getMessageStream() {
        PrimaryTypeAnalyser analyser = anonymousTypeAnalyser.get();
        return Stream.concat(super.getMessageStream(), analyser == null ? Stream.of() : analyser.getMessageStream());
    }

    @Override
    public void initialize() {
        List<MethodAnalyser> myMethodsAndConstructors = new LinkedList<>();
        List<MethodAnalyser> myStaticBlocks = new LinkedList<>();

        analyserResultBuilder.addMessages(fieldAnalysis
                .fromAnnotationsIntoProperties(AnalyserIdentification.FIELD, false,
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

    // FIXME should this also have an "enclosed in"?
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
        TypeInfo typeInfo = acrossAllMethods ? null
                : fieldInfo.owner.topOfInterdependentClassHierarchy();
        return allMethodsAndConstructors(typeInfo, alsoMyOwnConstructors);
    }

    private Stream<MethodAnalyser> allMethodsAndConstructors(TypeInfo enclosedIn, boolean alsoMyOwnConstructors) {
        return analyserContext.methodAnalyserStream()
                .filter(ma -> !ma.getMethodInspection().isStaticBlock())
                .filter(ma -> enclosedIn == null || ma.getMethodInfo().typeInfo.isEnclosedIn(enclosedIn))
                .filter(ma -> alsoMyOwnConstructors ||
                        !(ma.getMethodInfo().typeInfo == fieldInfo.owner && ma.getMethodInfo().isConstructor))
                .flatMap(ma -> Stream.concat(Stream.of(ma),
                        ma.getLocallyCreatedPrimaryTypeAnalysers().flatMap(PrimaryTypeAnalyser::methodAnalyserStream)));
    }

    @Override
    public AnalyserResult analyse(int iteration, EvaluationContext closure) {
        LOGGER.debug("Analysing field {}", fqn);

        // analyser visitors
        try {
            SharedState sharedState = new SharedState(iteration, closure);
            AnalysisStatus analysisStatus = analyserComponents.run(sharedState);
            analyserResultBuilder.setAnalysisStatus(analysisStatus);

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
            return analyserResultBuilder.build();
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in method analyser: {}", fqn);
            throw rte;
        }
    }

    @Override
    public void write() {
        assert anonymousTypeAnalyser.isFinal();
        if (anonymousTypeAnalyser.get() != null) {
            anonymousTypeAnalyser.get().write();
        }

        // before we check, we copy the properties into annotations
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        fieldAnalysis.transferPropertiesToAnnotations(e2);
    }

    @Override
    public void makeImmutable() {
        assert anonymousTypeAnalyser.isFinal();
        if (anonymousTypeAnalyser.get() != null) {
            anonymousTypeAnalyser.get().makeImmutable();
        }
    }

    private AnalysisStatus evaluateInitializer(SharedState sharedState) {
        if (fieldInspection.fieldInitialiserIsSet()) {
            FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
            Expression expression = fieldInitialiser.initialiser();
            if (expression != EmptyExpression.EMPTY_EXPRESSION) {
                Expression toEvaluate;
                MethodInfo sam = fieldInitialiser.implementationOfSingleAbstractMethod();
                if (expression instanceof MethodReference) {
                    toEvaluate = ConstructorCall.instanceFromSam(sam, fieldInfo.type);
                } else if (fieldInitialiser.callGetOnSam()) {
                    Expression object = ConstructorCall.withAnonymousClass(fieldInfo.type, sam.typeInfo, Diamond.NO);
                    toEvaluate = new MethodCall(expression.getIdentifier(), false, object, sam,
                            sam.returnType(), List.of());
                } else {
                    toEvaluate = expression;
                }

                EvaluationContext evaluationContext = new EvaluationContextImpl(sharedState.iteration(),
                        ConditionManager.initialConditionManager(analyserContext.getPrimitives()), sharedState.closure());
                EvaluationResult evaluationResult = toEvaluate.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                Expression initialiserValue = evaluationResult.value();
                fieldAnalysis.setInitialiserValue(initialiserValue);

                makeVariableAccessReport(initialiserValue, sharedState.closure());
                LOGGER.debug("Set initialiser of field {} to {}", fqn, evaluationResult.value());
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

    // code very similar to StatementAnalyserImpl.transferFromClosureToResult
    // we try to record access to variables that are out of our PTA
    private void makeVariableAccessReport(Expression value, EvaluationContext closure) {
        if (closure == null) return;
        VariableAccessReport.Builder builder = new VariableAccessReport.Builder();
        for (Variable variable : value.variables(true)) {
            if (closure.isPresent(variable)) {
                builder.addVariableRead(variable);
            }
            if (variable instanceof FieldReference fr && fr.fieldInfo.owner !=
                    fieldInfo.owner && fr.fieldInfo.owner.primaryType().equals(primaryType)) {
                builder.addVariableRead(fr);
            }
        }
        analyserResultBuilder.addVariableAccessReport(builder.build());
    }

    /*
    The anonymous type comes in two flavours: either the initialiser is a Lambda, method reference, directly assigned,
    or the initialiser is an expression which internally contains type creations such as lambda's.
    This expression is converted into an anonymous supplier, which will be analysed here. The field's value is then
    obtained by calling "get" on this anonymous type.
     */
    private AnalyserResult runAnonymousTypeAnalyser(SharedState sharedState) {
        if (!anonymousTypeAnalyser.isFinal()) {

            if (fieldInspection.fieldInitialiserIsSet()) {
                FieldInspection.FieldInitialiser fieldInitialiser = fieldInspection.getFieldInitialiser();
                if (fieldInitialiser.anonymousTypeCreated() != null) {
                    // the resolver has caught all variants into the SAM

                    SortedType sortedType = fieldInitialiser.anonymousTypeCreated().typeResolution.get().sortedType();
                    PrimaryTypeAnalyser primaryTypeAnalyser = new PrimaryTypeAnalyserImpl(analyserContext,
                            List.of(sortedType),
                            analyserContext.getConfiguration(),
                            analyserContext.getPrimitives(),
                            Either.left(analyserContext.getPatternMatcher()),
                            analyserContext.getE2ImmuAnnotationExpressions());
                    primaryTypeAnalyser.initialize();
                    anonymousTypeAnalyser.setFinal(primaryTypeAnalyser);
                    recursivelyAddPrimaryTypeAnalyserToAnalyserContext(primaryTypeAnalyser);
                }
            }
            if (!anonymousTypeAnalyser.isFinal()) anonymousTypeAnalyser.setFinal(null);
        }

        PrimaryTypeAnalyser analyser = anonymousTypeAnalyser.get();
        if (analyser != null) {
            AnalyserResult.Builder builder = new AnalyserResult.Builder();
            builder.setAnalysisStatus(NOT_YET_EXECUTED);
            LOGGER.debug("------- Starting local analyser {} ------", analyser.getName());
            AnalyserResult lambdaResult = analyser.analyse(sharedState.iteration(), sharedState.closure());
            LOGGER.debug("------- Ending local analyser   {} ------", analyser.getName());
            builder.add(lambdaResult);
            return builder.build();
        }

        return AnalyserResult.EMPTY;
    }

    private void recursivelyAddPrimaryTypeAnalyserToAnalyserContext(PrimaryTypeAnalyser analyser) {
        AnalyserContext context = analyserContext;
        while (context != null) {
            if (context instanceof ExpandableAnalyserContextImpl expandable) {
                expandable.addPrimaryTypeAnalyser(analyser);
            }
            context = context.getParent();
        }
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

    /*
    SAFE, no need to look at best of method, no way best of method can influence this:
    Unbound parameter type -> container (a bit by definition, there's no methods to call)
    Interfaces with @Contracted==TRUE
    Final type -> @Contracted, T or F
    Constructor call non-final class -> T or F
    Value with @Container TRUE in the value property

    Best of methods = values from CONTEXT_CONTAINER taken from the methods
    Interfaces with @Contracted==FALSE
    non-final classes, with @Contracted==FALSE

    leaves: non-final class, @Contracted==FALSE. For now, simply return FALSE.
     */
    private AnalysisStatus analyseContainer() {
        if (fieldAnalysis.getPropertyFromMapDelayWhenAbsent(EXTERNAL_CONTAINER).isDone()) return DONE;

        DV safe = analyserContext.safeContainer(fieldInfo.type);
        if (safe != null && safe.isDone()) {
            LOGGER.debug("Set @Container on {} to safe value {}", fqn, safe);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, safe);
            return DONE;
        }
        TypeInfo formalType = fieldInfo.type.bestTypeInfo();
        assert formalType != null;
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(formalType);
        DV formal = typeAnalysis.getProperty(Property.CONTAINER);
        if (formal.isDelayed()) {
            LOGGER.debug("Delaying @Container of field {}, waiting for @Container of formal type", fqn);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, formal);
            return AnalysisStatus.of(formal);
        }

        if (fieldAnalysis.valuesStatus().isDelayed()) {
            LOGGER.debug("Delaying @Container on field {}, waiting for values", fqn);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, fieldAnalysis.valuesStatus());
            return fieldAnalysis.valuesStatus();
        }
        DV safeMinimum = DV.MIN_INT_DV; // so we know if a safe minimum was reached
        boolean otherValues = false;
        for (ValueAndPropertyProxy proxy : fieldAnalysis.getValues()) {
            DV safeDv = safeContainer(proxy);
            if (safeDv != null) {
                safeMinimum = safeDv.min(safeMinimum);
            } else {
                otherValues = true;
            }
        }
        if (safeMinimum.isDelayed() && safeMinimum != DV.MIN_INT_DV) {
            LOGGER.debug("Delaying @Container on field {}, waiting for container on values", fqn);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, safeMinimum);
            return AnalysisStatus.of(safeMinimum);
        }
        if (safeMinimum.equals(MultiLevel.CONTAINER_DV) || safeMinimum.equals(MultiLevel.NOT_CONTAINER_DV) && !otherValues) {
            LOGGER.debug("Set @Container on {} to safe minimum over values: {}", fqn, safeMinimum);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, safeMinimum);
            return DONE;
        }

        // there is at least one assignment value which is not safe
        DV bestOverContext = myMethodsAndConstructors.stream()
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                .filter(VariableInfo::isRead)
                .map(vi -> vi.getProperty(Property.CONTEXT_CONTAINER))
                .reduce(MultiLevel.NOT_CONTAINER_DV, DV::max);
        if (bestOverContext.isDelayed()) {
            LOGGER.debug("Delay @Container on {}, waiting for context container", fqn);
            fieldAnalysis.setProperty(EXTERNAL_CONTAINER, bestOverContext);
            return AnalysisStatus.of(bestOverContext);
        }

        LOGGER.debug("@Container on field {}: value of best over context: {}", fqn, bestOverContext);
        fieldAnalysis.setProperty(EXTERNAL_CONTAINER, bestOverContext);
        return AnalysisStatus.of(bestOverContext);
    }

    private DV safeContainer(ValueAndPropertyProxy proxy) {
        // for non-final classes, safe is always null
        Expression value = proxy.getValue();
        if (value instanceof NullConstant) return null;
        DV safe = analyserContext.safeContainer(value.returnType());
        if (safe != null) return safe;
        // but if value is a normal constructor call or an instance with a constructor state
        // we can trust that its value is safe
        if (value instanceof ConstructorCall cc && cc.anonymousClass() == null
                && cc.returnType().typeInfo.typeInspection.get().typeNature() == TypeNature.CLASS) {
            // we're creating an object, so we don't need the
            return analyserContext.defaultContainer(cc.returnType());
        }
        DV container = proxy.getProperty(CONTAINER);
        if (MultiLevel.CONTAINER_DV.equals(container)) return MultiLevel.CONTAINER_DV;
        if (container.isDelayed()) return container;

        ParameterizedType type = proxy.getValue().returnType();
        if (type.bestTypeInfo().typeInspection.get().typeNature() == TypeNature.CLASS) {
            return analyserContext.defaultContainer(type);
        }
        return null;
    }

    private AnalysisStatus analyseNotNull() {
        if (fieldAnalysis.getProperty(Property.EXTERNAL_NOT_NULL).isDone()) return DONE;

        if (fieldInfo.type.isPrimitiveExcludingVoid()) {
            LOGGER.debug("Field {} is effectively not null, it is of primitive type", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
            return DONE;
        }

        DV isFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (isFinal.isDelayed()) {
            LOGGER.debug("Delaying @NotNull on {} until we know about @Final", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, isFinal);
            return isFinal.causesOfDelay();
        }
        if (isFinal.valueIsFalse() && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            LOGGER.debug("Field {} cannot be @NotNull: it be assigned to from outside this primary type", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.NULLABLE_DV);
            return DONE;
        }
        // whatever we do, if one of the values is the null constant, then the result must be nullable
        if (fieldAnalysis.valuesStatus().isDelayed()) {
            LOGGER.debug("Delay @NotNull until all values are known");
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, fieldAnalysis.valuesStatus());
            return fieldAnalysis.valuesStatus();
        }
        assert fieldAnalysis.getValues().size() > 0;

        /*
        The choice here is between deciding the @NotNull based on the value and the context of
        - the construction methods only
        - all methods

        There are cases to be made for both situations; we simply avoid making a decision here
        and let the user decide.

        Note that as soon as bestOverContext is better that ENN, we still cannot skip the @NotNull of the values,
        because the value would contain the contracted @NotNull at a parameter after assignment.
         */
        boolean computeContextPropertiesOverAllMethods = analyserContext.getConfiguration().analyserConfiguration()
                .computeContextPropertiesOverAllMethods();

        // if one of the values is the constant null value (and we're not trying to boost @NotNull) then return NULLABLE immediately
        if (!computeContextPropertiesOverAllMethods &&
                fieldAnalysis.getValues().stream().anyMatch(proxy -> proxy.getValue() instanceof NullConstant)) {
            LOGGER.debug("Field {} cannot be @NotNull: one of its values is the null constant", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, MultiLevel.NULLABLE_DV);
            return DONE;
        }

        DV bestOverContext = allMethodsAndConstructors(true)
                .filter(m -> computeContextPropertiesOverAllMethods || m.getMethodInfo().inConstruction())
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                .map(vi -> vi.getProperty(Property.CONTEXT_NOT_NULL))
                .reduce(MultiLevel.NULLABLE_DV, DV::max);
        if (bestOverContext.isDelayed()) {
            LOGGER.debug("Delay @NotNull on {}, waiting for CNN", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, bestOverContext);
            return bestOverContext.causesOfDelay();
        }
        // the null constant places a hard limit on things, as does e.g. a string constant "abc"
        DV constantNotNull = constantNotNullOverValues();
        DV worstOverValues = computeWorstNotNullOverValues(computeContextPropertiesOverAllMethods, bestOverContext);

        DV finalNotNullValue = worstOverValues.max(bestOverContext).min(constantNotNull);
        fieldAnalysis.setProperty(Property.EXTERNAL_NOT_NULL, finalNotNullValue);
        return AnalysisStatus.of(finalNotNullValue.causesOfDelay());
    }

    private DV constantNotNullOverValues() {
        return fieldAnalysis.getValues().stream()
                .filter(ValueAndPropertyProxy::validValueProperties)
                .filter(proxy -> proxy.getValue() instanceof ConstantExpression)
                .map(proxy -> proxy.getProperty(Property.NOT_NULL_EXPRESSION))
                .reduce(DV.MAX_INT_DV, DV::min);
    }

    private DV computeWorstNotNullOverValues(boolean computeContextPropertiesOverAllMethods, DV bestOverContext) {
        DV worstOverValuesUnfiltered = fieldAnalysis.getValues().stream()
                .filter(ValueAndPropertyProxy::validValueProperties)
                .map(proxy -> proxy.getProperty(Property.NOT_NULL_EXPRESSION))
                .reduce(DV.MAX_INT_DV, DV::min);

        DV worstOverValues;
        if (computeContextPropertiesOverAllMethods) {
            worstOverValues = worstOverValuesFiltered(bestOverContext, worstOverValuesUnfiltered);
        } else {
            // no filtering, there must be at least one value
            worstOverValues = worstOverValuesUnfiltered;
            assert worstOverValues != DV.MAX_INT_DV;
        }
        return worstOverValues;
    }

    private DV worstOverValuesFiltered(DV bestOverContext, DV worstOverValuesUnfiltered) {
        DV worstOverValues;
        DV worst = fieldAnalysis.getValues().stream()
                .filter(ValueAndPropertyProxy::validValueProperties)
                .filter(proxy -> proxy.getOrigin() != ValueAndPropertyProxy.Origin.CONSTRUCTION)
                .map(proxy -> proxy.getProperty(Property.NOT_NULL_EXPRESSION))
                .reduce(DV.MAX_INT_DV, DV::min);
        if (worst != DV.MAX_INT_DV) {
            worstOverValues = worst;
            if (worstOverValues.lt(bestOverContext)) {
                // see Basics_2b; we need the setter to run before the add-method can be called
                // see Modified_11_2 for a different example which needs the filtering on CONSTRUCTION
                analyserResultBuilder.add(Message.newMessage(fieldAnalysis.location(),
                        Message.Label.FIELD_INITIALIZATION_NOT_NULL_CONFLICT));
            }
        } else {
            worstOverValues = worstOverValuesUnfiltered;
        }
        return worstOverValues;
    }

    private AnalysisStatus fieldErrors() {
        if (fieldInspection.getModifiers().contains(FieldModifier.PRIVATE)) {
            if (!fieldInfo.isStatic()) {
                boolean readInMethods = allMethodsAndConstructors(false)
                        .anyMatch(this::isReadInMethod);
                if (!readInMethods) {
                    analyserResultBuilder.add(Message.newMessage(fieldInfo.newLocation(),
                            Message.Label.PRIVATE_FIELD_NOT_READ, fieldInfo.name));
                }
                return DONE;
            }
        } else {
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            if (effectivelyFinal.valueIsFalse()) {
                // only react once we're certain the variable is not effectively final
                // error, unless we're in a record
                if (!fieldInfo.owner.isPrivateNested()) {
                    analyserResultBuilder.add(Message.newMessage(fieldInfo.newLocation(),
                            Message.Label.NON_PRIVATE_FIELD_NOT_FINAL, fieldInfo.name));
                } // else: nested private types can have fields the way they like it
                return DONE;
            } else if (effectivelyFinal.isDelayed()) {
                LOGGER.debug("Not yet ready to decide on non-private non-final");
                return effectivelyFinal.causesOfDelay();
            }
        }
        // not for me
        return DONE;
    }

    private boolean isReadInMethod(MethodAnalyser methodAnalyser) {
        return methodAnalyser.getFieldAsVariableStream(fieldInfo).anyMatch(VariableInfo::isRead);
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
            LOGGER.debug("Field {} set to @Independent: static type",
                    fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            return DONE;
        }

        DV immutable = fieldAnalysis.getPropertyFromMapDelayWhenAbsent(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed()) {
            LOGGER.debug("Field {} independent delayed: wait for immutable", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(Property.INDEPENDENT, immutable);
            return immutable.causesOfDelay();
        }
        int immutableLevel = MultiLevel.level(immutable);
        if (immutableLevel >= MultiLevel.Level.IMMUTABLE_2.level) {
            DV independent = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
            LOGGER.debug("Field {} set to {}, direct correspondence to (dynamically) immutable",
                    fieldInfo.fullyQualifiedName(), independent);
            fieldAnalysis.setProperty(Property.INDEPENDENT, independent);
            return DONE;
        }

        if (staticallyIndependent.isDelayed()) {
            LOGGER.debug("Field {} independent delayed: wait for type independence of {}",
                    fieldInfo.fullyQualifiedName(), fieldInfo.type);
            fieldAnalysis.setProperty(Property.INDEPENDENT, staticallyIndependent);
            return staticallyIndependent.causesOfDelay();
        }
        fieldAnalysis.setProperty(Property.INDEPENDENT, staticallyIndependent);
        return DONE;
    }

    /*
    method modelled to that of analyseNotNull.

    there is no need to wait for the field to be of transparent type or not... this delays everything with
    one iteration, but even if it is, it will not be used in any situation where it can cause a warning,
    because if that were the case, it would not be transparent!
     */
    private AnalysisStatus analyseImmutable(SharedState sharedState) {
        if (fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE).isDone()) return DONE;

        DV isFinal = fieldAnalysis.getProperty(Property.FINAL);
        if (isFinal.isDelayed()) {
            LOGGER.debug("Delaying @Immutable on {} until we know about @Final", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, isFinal);
            return isFinal.causesOfDelay();
        }
        if (isFinal.valueIsFalse() && fieldCanBeWrittenFromOutsideThisPrimaryType) {
            LOGGER.debug("Field {} cannot be immutable: it is not @Final," +
                    " and it can be assigned to from outside this primary type", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, MultiLevel.MUTABLE_DV);
            return DONE;
        }

        DV staticallyImmutable = analyserContext.defaultImmutable(fieldInfo.type, false);
        if (staticallyImmutable.equals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV)) {
            LOGGER.debug("Field {} is statically @ERImmutable", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, staticallyImmutable);
            return DONE; // cannot be improved
        }

        DV finalImmutable;

        CausesOfDelay valuesStatus = fieldAnalysis.valuesStatus();
        if (valuesStatus.isDelayed()) {
            LOGGER.debug("Delaying @Immutable of field {}, non-parameter values not yet known", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, valuesStatus);
            return valuesStatus;
        }
        CausesOfDelay allLinksStatus = fieldAnalysis.allLinksHaveBeenEstablished();
        if (allLinksStatus.isDelayed() && isFinal.valueIsFalse()) {
                /* if the field is effectively final, we don't need links established because all assignment
                 occurs in the constructor
                 */
            LOGGER.debug("Delaying @Immutable of field {}, not all links have been established", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, allLinksStatus);
            return allLinksStatus;
        }
        DV worstOverValuesPrep = fieldAnalysis.getValues().stream()
                .filter(ValueAndPropertyProxy::validValueProperties)
                .filter(proxy -> !(proxy.getValue() instanceof NullConstant))
                .map(this::immutableOfProxy)
                .reduce(DV.MAX_INT_DV, DV::min);
        DV worstOverValues = worstOverValuesPrep == DV.MAX_INT_DV ? MultiLevel.MUTABLE_DV : worstOverValuesPrep;
        if (worstOverValues.isDelayed()) {
            LOGGER.debug("Delay @Immutable on {}, waiting for values", fqn);
            fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, worstOverValues);
            return worstOverValues.causesOfDelay();
        }

        // if we have an assignment to an eventually immutable variable, but somehow the construction context enforces "after"
        // that should be taken into account (see EventuallyImmutableUtil_2 vs E2InContext_2)
        if (MultiLevel.isBefore(worstOverValues)) {
            DV bestOverContext = myMethodsAndConstructors.stream()
                    .filter(m -> m.getMethodInfo().inConstruction())
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                    .map(vi -> vi.getProperty(Property.CONTEXT_IMMUTABLE))
                    .reduce(MultiLevel.MUTABLE_DV, DV::max);
            if (bestOverContext.isDelayed()) {
                LOGGER.debug("Delay @Immutable on {}, waiting for context immutable", fqn);
                fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, bestOverContext);
                return bestOverContext.causesOfDelay();
            }
            finalImmutable = bestOverContext.max(worstOverValues);
        } else {
            finalImmutable = worstOverValues;
        }

        // in accordance with the code that changes a ConstructorCall with an Instance in analyseFinalValue,
        // we remove BEFORE in favor of the neutral EVENTUAL
        DV correctedImmutable1;
        MultiLevel.Effective effective = MultiLevel.effective(finalImmutable);
        if (effective == MultiLevel.Effective.EVENTUAL_BEFORE) {
            correctedImmutable1 = MultiLevel.eventuallyImmutable(MultiLevel.level(finalImmutable));
        } else {
            correctedImmutable1 = finalImmutable;
        }

        // See E2InContext_0,1 (field is not private, so if it's before, someone else can change it into after)
        DV correctedImmutable2 = correctForExposureBefore(correctedImmutable1);
        if (correctedImmutable2.isDelayed()) {
            LOGGER.debug("Delay @Immutable on {}, waiting for exposure to decide on @BeforeMark", fqn);
            // still, we're already marking
            fieldAnalysis.setProperty(Property.PARTIAL_EXTERNAL_IMMUTABLE, correctedImmutable2);
            return correctedImmutable2.causesOfDelay();
        }
        LOGGER.debug("Set immutable on field {} to value {}", fqn, correctedImmutable2);
        fieldAnalysis.setProperty(Property.EXTERNAL_IMMUTABLE, correctedImmutable2);

        return DONE;
    }

    private DV immutableOfProxy(ValueAndPropertyProxy proxy) {
        if (isMyOwnType(proxy.getValue().returnType())) {
            return myTypeAnalyser.getTypeAnalysis().getProperty(Property.IMMUTABLE);
        }
        return proxy.getProperty(Property.IMMUTABLE);
    }

    private boolean isMyOwnType(ParameterizedType returnType) {
        if (returnType.typeInfo == null) return false;
        return returnType.typeInfo == fieldInfo.owner;
    }

    private DV correctForExposureBefore(DV immutable) {
        if (immutable.isDelayed()) return immutable;
        MultiLevel.Effective effective = MultiLevel.effective(immutable);
        if (effective != MultiLevel.Effective.EVENTUAL_BEFORE) return immutable;

        DV corrected = MultiLevel.eventuallyImmutable(MultiLevel.level(immutable));
        if (fieldInfo.isAccessibleOutsideOfPrimaryType()) {
            return corrected;
        }
        // check exposed via return values of methods
        // FIXME ignoreMyConstructors is a delay breaking measure, needs re-implementing
        CausesOfDelay delayLinkedVariables = filterForExposure(myMethodsAndConstructors.stream())
                .map(ma -> ((ComputingMethodAnalyser) ma).methodLevelData().linksHaveNotYetBeenEstablished(ignoreMyConstructors))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delayLinkedVariables.isDelayed()) {
            LOGGER.debug("Exposure computation on {} delayed by links: {}", fqn, delayLinkedVariables);
            return delayLinkedVariables;
        }
        FieldReference me = new FieldReference(analyserContext, fieldInfo);
        boolean linkedToMe = filterForExposure(myMethodsAndConstructors.stream())
                .anyMatch(ma -> {
                    if (ma.getMethodInfo().hasReturnValue()) {
                        LinkedVariables linkedVariables = ((ComputingMethodAnalyser) ma).getReturnAsVariable().getLinkedVariables();
                        DV link = linkedVariables.value(me);
                        if (link != null && link.le(LinkedVariables.DEPENDENT_DV)) return true;
                    }
                    return ma.getMethodAnalysis().getLastStatement().variableStream()
                            .filter(vi -> vi.variable() instanceof ParameterInfo)
                            .anyMatch(vi -> vi.getLinkedVariables().contains(me));
                });
        return linkedToMe ? corrected : immutable;
    }

    private static Stream<MethodAnalyser> filterForExposure(Stream<MethodAnalyser> stream) {
        return stream
                .filter(ma -> !ma.getMethodInfo().isPrivate() && ma.getMethodInfo().hasReturnValue())
                .filter(ma -> ma instanceof ComputingMethodAnalyser cma && cma.methodLevelData() != null)
                .filter(ma -> !ma.getMethodAnalysis().getProperty(Property.FINALIZER).valueIsTrue());
    }

    record OccursAndDelay(boolean occurs, int occurrenceCountForError, CausesOfDelay delay) {
    }

    // NOTE: we're also considering non-private methods here, like setters: IS THIS WISE?

    private OccursAndDelay occursInAllConstructors(List<ValueAndPropertyProxy> values,
                                                   boolean ignorePrivateConstructors) {
        boolean occurs = true;
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        int occurrenceCountForError = 0;
        for (MethodAnalyser methodAnalyser : myMethodsAndConstructors) {
            DV finalizer = methodAnalyser.getMethodAnalysis().getProperty(Property.FINALIZER);
            assert finalizer.isDone();
            MethodInfo methodInfo = methodAnalyser.getMethodInfo();
            if (finalizer.valueIsFalse() && (!methodAnalyser.getMethodInspection().isPrivate() ||
                    methodInfo.isConstructor && !ignorePrivateConstructors)) {
                boolean added = false;
                for (VariableInfo vii : methodAnalyser.getMethodAnalysis().getFieldAsVariableAssigned(fieldInfo)) {
                    VariableInfo vi;
                    if (vii.getValue() instanceof DelayedWrappedExpression dwe) {
                        // the reason for providing a VI rather than an expression in DWE is that it contains properties as well.
                        // these properties are hard to compute here
                        // IMPROVE we should detect DWE inside any expression, and then do some fancy replacement
                        // SOLUTION: have the SAEvaluationOfMainExpression write a DWE around the final value when there is a DWE inside the expression
                        // after doing a translation for these inner DWEs to their enclosed value.
                        vi = dwe.getVariableInfo();
                    } else {
                        vi = vii;
                    }
                    Expression expression = vi.getValue();
                    CausesOfDelay causesOfDelay = expression.causesOfDelay();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodInfo.fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    ValueAndPropertyProxy.Origin origin = methodInfo.inConstruction()
                            ? ValueAndPropertyProxy.Origin.CONSTRUCTION : ValueAndPropertyProxy.Origin.METHOD;
                    ValueAndPropertyProxy proxy;

                    boolean viIsDelayed;
                    if (vi.getValue() instanceof DelayedVariableExpression dve && dve.variable instanceof FieldReference fr &&
                            methodInfo.isConstructor && fr.fieldInfo.owner == methodInfo.typeInfo && !fr.isDefaultScope && !fr.isStatic) {
                        // ExplicitConstructorInvocation_5, but be careful with the restrictions, e.g. ExternalNotNull_1 for the scope,
                        // as well as ExplicitConstructorInvocation_4
                        // captures: this.field = someParameterOfMySelf.field;
                        added = false; // we'll skip this!
                    } else {
                        if (causesOfDelay.causesStream().anyMatch(cause -> cause instanceof VariableCause vc
                                && vc.cause() == CauseOfDelay.Cause.BREAK_INIT_DELAY
                                && vc.variable() instanceof FieldReference fr && fr.fieldInfo == fieldInfo)) {
                            proxy = breakDelayProxy(origin);
                            viIsDelayed = false;
                        } else {
                            proxy = new ValueAndPropertyProxy.ValueAndPropertyProxyBasedOnVariableInfo(vi, origin);
                            viIsDelayed = causesOfDelay.isDelayed();
                        }
                        values.add(proxy);
                        if (!fieldInspection.isStatic() && methodInfo.isConstructor) {
                            // we'll warn for the combination of field initializer, and occurrence in at least one constructor
                            occurrenceCountForError++;
                        }
                        added = true;
                        if (viIsDelayed) {
                            LOGGER.debug("Delay consistent value for field {} because of {}", fqn, vi.getValue().toString());
                            delays = delays.merge(causesOfDelay);
                        }
                    }
                }
                if (!added && methodInfo.isConstructor) {
                    occurs = false;
                }
            }
        }
        return new OccursAndDelay(occurs, occurrenceCountForError, delays);
    }

    private ValueAndPropertyProxy breakDelayProxy(ValueAndPropertyProxy.Origin origin) {
        Expression value = new DelayedExpression("break initialization delay",
                fieldInfo.type, LinkedVariables.EMPTY, fieldInfo.delay(CauseOfDelay.Cause.BREAK_INIT_DELAY));
        return new ValueAndPropertyProxy() {
            @Override
            public Origin getOrigin() {
                return origin;
            }

            @Override
            public Expression getValue() {
                return value;
            }

            @Override
            public DV getProperty(Property property) {
                return fieldInfo.delay(CauseOfDelay.Cause.BREAK_INIT_DELAY);
            }

            @Override
            public LinkedVariables getLinkedVariables() {
                return null;
            }

            @Override
            public boolean validValueProperties() {
                return false;
            }

            @Override
            public String toString() {
                return "break-delay proxy";
            }
        };
    }

    private OccursAndDelay occursInStaticBlocks(List<MethodAnalyser> staticBlocks, List<ValueAndPropertyProxy> values) {
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        ValueAndPropertyProxy latestBlock = null;
        for (MethodAnalyser methodAnalyser : staticBlocks) {
            for (VariableInfo vi : methodAnalyser.getMethodAnalysis().getFieldAsVariable(fieldInfo)) {
                if (vi.isAssigned()) {
                    Expression expression = vi.getValue();
                    VariableExpression ve;
                    if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                            && ve.variable() instanceof LocalVariableReference) {
                        throw new UnsupportedOperationException("Method " + methodAnalyser.getMethodInfo().fullyQualifiedName + ": " +
                                fieldInfo.fullyQualifiedName() + " is local variable " + expression);
                    }
                    latestBlock = new ValueAndPropertyProxy.ValueAndPropertyProxyBasedOnVariableInfo
                            (vi, ValueAndPropertyProxy.Origin.STATIC_BLOCK);
                    delays = delays.merge(vi.getValue().causesOfDelay());
                }
            }
        }
        if (latestBlock != null) {
            values.add(latestBlock);
            if (delays.isDelayed()) {
                LOGGER.debug("Delaying initialization of field {} in static block", fieldInfo.fullyQualifiedName());
            }
            return new OccursAndDelay(true, 1, delays);
        }
        return new OccursAndDelay(false, 0, CausesOfDelay.EMPTY);
    }

    private AnalysisStatus analyseValues() {
        assert fieldAnalysis.valuesStatus().isDelayed();
        List<ValueAndPropertyProxy> values = new ArrayList<>();
        CausesOfDelay delays = CausesOfDelay.EMPTY;
        if (haveInitialiser) {
            delays = fieldAnalysis.getInitializerValue().causesOfDelay();
            EvaluationContext ec = new EvaluationContextImpl(0,
                    ConditionManager.initialConditionManager(analyserContext.getPrimitives()), null);
            values.add(new ValueAndPropertyProxy() {
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
                    if (fieldInfo.type.isFunctionalInterface() && properlyDefinedAnonymousType(fieldAnalysis.getInitializerValue())) {
                        return switch (property) {
                            case IMMUTABLE, EXTERNAL_IMMUTABLE -> MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
                            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                            case NOT_NULL_EXPRESSION, EXTERNAL_NOT_NULL -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
                            case IDENTITY -> DV.FALSE_DV;
                            case CONTAINER -> MultiLevel.CONTAINER_DV; // FIXME this should be diverted to the type
                            default -> throw new UnsupportedOperationException("? who wants to know " + property);
                        };
                    }
                    return ec.getProperty(getValue(), property, false, false);
                }

                @Override
                public Origin getOrigin() {
                    return Origin.INITIALISER;
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
            analyserResultBuilder.add(Message.newMessage(fieldInfo.newLocation(),
                    Message.Label.UNNECESSARY_FIELD_INITIALIZER));
        }
        if (!haveInitialiser && !fieldInfo.isExplicitlyFinal() && !occursInAllConstructorsOrOneStaticBlock) {
            Expression nullValue = ConstantExpression.nullValue(analyserContext.getPrimitives(),
                    fieldInfo.type.bestTypeInfo());
            values.add(0, new ValueAndPropertyProxy() {
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
                public Origin getOrigin() {
                    return Origin.EMPTY_INITIALISER;
                }

                @Override
                public String toString() {
                    return "NO_INIT:" + getValue().toString();
                }
            });
        }
        if (delays.isDelayed()) {
            // inject a cause of delay that we can intercept in the next iteration
            FieldReference fr = new FieldReference(analyserContext, fieldInfo);
            VariableCause vc = new VariableCause(fr, new LocationImpl(fieldInfo), CauseOfDelay.Cause.VALUES);
            delays = delays.merge(new SimpleSet(vc));
        }
        // order does not matter for this class, but is handy for testing
        values.sort(ValueAndPropertyProxy.COMPARATOR);
        fieldAnalysis.setValues(values, delays);
        return AnalysisStatus.of(delays);
    }

    private boolean properlyDefinedAnonymousType(Expression expression) {
        return expression instanceof InlinedMethod || expression instanceof Lambda
                || expression instanceof ConstructorCall cc && cc.anonymousClass() != null
                || expression instanceof MethodReference;
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
            fieldAnalysis.setValue(UnknownExpression.forVariableValue(fieldInfo.getIdentifier(), fieldInfo.type));
            return DONE;
        }
        CausesOfDelay valuesStatus = fieldAnalysis.valuesStatus();
        if (valuesStatus.isDelayed()) {
            LOGGER.debug("Delaying final value, have no values yet for field " + fqn);
            fieldAnalysis.setValue(DelayedExpression.forInitialFieldValue(fieldInfo,
                    fieldAnalysis.getLinkedVariables(),
                    fieldAnalysis.valuesStatus()));
            return valuesStatus;
        }
        List<ValueAndPropertyProxy> values = fieldAnalysis.getValues();


        // compute and set the combined value
        Expression effectivelyFinalValue;

        // suppose there are 2 constructors, and the field gets exactly the same value...
        List<Expression> expressions = values.stream().map(ValueAndPropertyProxy::getValue).toList();
        Set<Expression> set = new HashSet<>(expressions);

        if (set.size() == 1) {
            ValueAndPropertyProxy proxy = values.get(0);
            Expression expression = proxy.getValue();
            ConstructorCall constructorCall;
            if ((constructorCall = expression.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                // now the state of the new object may survive if there are no modifying methods called,
                // but that's too early to know now
                DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
                boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;

                if (immutable.isDelayed() && !fieldOfOwnType) {
                    LOGGER.debug("Waiting with effectively final value  until decision on immutable for {}", fqn);
                    return immutable.causesOfDelay();
                }
                // the fact that any level 2+ eventually immutable field's initialiser gets downgraded is maybe a little
                // too strong -- it may in fact never change its state. But what's the point in that?
                // NOTE: analyseImmutable reflects this decision!
                boolean downgradeFromNewInstanceWithConstructor = !fieldOfOwnType &&
                        (MultiLevel.level(immutable) == 0
                                || MultiLevel.effective(immutable) != MultiLevel.Effective.EFFECTIVE);
                if (downgradeFromNewInstanceWithConstructor) {
                    Properties valueProperties = Properties.of(Map.of(
                            Property.NOT_NULL_EXPRESSION, proxy.getProperty(Property.NOT_NULL_EXPRESSION),
                            Property.IMMUTABLE, proxy.getProperty(Property.IMMUTABLE),
                            Property.INDEPENDENT, proxy.getProperty(Property.INDEPENDENT),
                            Property.CONTAINER, proxy.getProperty(Property.CONTAINER),
                            Property.IDENTITY, proxy.getProperty(Property.IDENTITY)
                    ));
                    effectivelyFinalValue = constructorCall.removeConstructor(valueProperties, fieldAnalysis.primitives);
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
            LOGGER.debug("Delaying final value of field {}", fieldInfo.fullyQualifiedName());
            return effectivelyFinalValue.causesOfDelay();
        }

        // check constant, but before we set the effectively final value

        LOGGER.debug("Setting final value of effectively final field {} to {}", fqn, effectivelyFinalValue);
        fieldAnalysis.setValue(effectivelyFinalValue);
        return DONE;
    }

    private AnalysisStatus analyseConstant() {
        if (fieldAnalysis.getProperty(Property.CONSTANT).isDone()) return DONE;

        Expression value = fieldAnalysis.getValue();
        if (value.isDelayed()) {
            LOGGER.debug("Delaying @Constant, effectively final value not yet set");
            return value.causesOfDelay();
        }

        if (value.isUnknown()) {
            LOGGER.debug("@Constant of {} false, because not final", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setProperty(Property.CONSTANT, DV.FALSE_DV);
            return DONE;
        }

        boolean fieldOfOwnType = fieldInfo.type.typeInfo == fieldInfo.owner;
        DV immutable = fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
        if (immutable.isDelayed() && !fieldOfOwnType) {
            LOGGER.debug("Waiting with @Constant until decision on @E2Immutable for {}",
                    fqn);
            return immutable.causesOfDelay();
        }

        DV recursivelyConstant;
        if (!fieldOfOwnType && !MultiLevel.isAtLeastEffectivelyE2Immutable(immutable))
            recursivelyConstant = DV.FALSE_DV;
        else recursivelyConstant = recursivelyConstant(value);
        if (recursivelyConstant.isDelayed()) {
            LOGGER.debug("Delaying @Constant because of recursively constant computation on value {} of {}",
                    fqn, value);
            return recursivelyConstant.causesOfDelay();
        }

        if (recursivelyConstant.valueIsTrue()) {
            // directly adding the annotation; it will not be used for inspection
            E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
            AnnotationExpression constantAnnotation = checkConstant.createConstantAnnotation(e2, value);
            fieldAnalysis.annotations.put(constantAnnotation, true);
            LOGGER.debug("Added @Constant annotation on field {}", fqn);
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
            LOGGER.debug( "Setting linked variables to empty for field {}, @E2Immutable type");
            // finalizer check at assignment only
            return DONE;
        }
*/
        // we ONLY look at the linked variables of fields that have been assigned to
        CausesOfDelay causesOfDelay = allMethodsAndConstructors(true)
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo)
                        .filter(VariableInfo::isAssigned)
                        .map(vi -> vi.getLinkedVariables().causesOfDelay()))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (causesOfDelay.isDelayed()) {
            LOGGER.debug("LinkedVariables not yet set for {}", fieldInfo.fullyQualifiedName());
            fieldAnalysis.setLinkedVariables(LinkedVariables.delayedEmpty(causesOfDelay));
            return causesOfDelay.causesOfDelay();
        }

        Map<Variable, DV> map = allMethodsAndConstructors(true)
                .flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                .filter(VariableInfo::linkedVariablesIsSet)
                .flatMap(vi -> vi.getLinkedVariables().variables().entrySet().stream())
                .filter(e -> !(e.getKey() instanceof LocalVariableReference)
                        && !(e.getKey() instanceof ReturnVariable)
                        && !(e.getKey() instanceof FieldReference fr && fr.fieldInfo == fieldInfo)) // especially local variable copies of the field itself
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, DV::max));

        LinkedVariables linkedVariables = new LinkedVariables(map);
        fieldAnalysis.setLinkedVariables(linkedVariables);
        LOGGER.debug("FA: Set links of {} to [{}]", fqn, linkedVariables);

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
                    .flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                    .noneMatch(VariableInfo::isAssigned);
        }
        fieldAnalysis.setProperty(Property.FINAL, DV.fromBoolDv(isFinal));
        LOGGER.debug("Mark field {} as " + (isFinal ? "" : "not ") + "effectively final", fqn);

        if (!isFinal) {
            TypeInfo bestType = fieldInfo.type.bestTypeInfo();
            if (bestType != null) {
                TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
                if (typeAnalysis.getProperty(Property.FINALIZER).valueIsTrue()) {
                    analyserResultBuilder.add(Message.newMessage(fieldInfo.newLocation(),
                            Message.Label.TYPES_WITH_FINALIZER_ONLY_EFFECTIVELY_FINAL));
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
            LOGGER.debug("Field {} is modified? Contract: {}", fqn, contract);
            return DONE;
        }
        assert fieldAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD).isDelayed();

        boolean isPrimitive = fieldInfo.type.isPrimitiveExcludingVoid();
        // too dangerous to catch @E2Immutable because of down-casts
        if (isPrimitive) {
            LOGGER.debug("Field {} is @NotModified, since it is final and primitive", fqn);
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
            return DONE;
        }

        Stream<MethodAnalyser> stream = methodsForModification();
        boolean modified = fieldCanBeWrittenFromOutsideThisPrimaryType ||
                stream.flatMap(m -> m.getFieldAsVariableStream(fieldInfo))
                        .filter(VariableInfo::isRead)
                        .anyMatch(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).valueIsTrue());

        if (modified) {
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.TRUE_DV);
            LOGGER.debug("Mark field {} as @Modified", fqn);
            return DONE;
        }

        // we only consider methods, not constructors (unless the field is static)!
        Stream<MethodAnalyser> stream2 = methodsForModification();
        CausesOfDelay contextModifications = stream2.flatMap(m -> {
            List<VariableInfo> variableInfoList = m.getMethodAnalysis().getFieldAsVariable(fieldInfo);
            return variableInfoList.stream()
                    .filter(VariableInfo::isRead)
                    .map(vi -> vi.getProperty(Property.CONTEXT_MODIFIED).causesOfDelay());
        }).reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        if (contextModifications.isDone()) {
            fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
            LOGGER.debug("Mark field {} as @NotModified", fqn);
            return DONE;
        }
        fieldAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, contextModifications);
        LOGGER.debug("Field @Modified delayed because of {}", contextModifications);
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
            analyserResultBuilder.add(CheckFinalNotModified.check(fieldInfo, Final.class, e2.effectivelyFinal, fieldAnalysis,
                    myTypeAnalyser.getTypeAnalysis()));
            check(org.e2immu.annotation.Variable.class, e2.variableField);
        }
        if (analyserProgram.accepts(ALL)) {
            LOGGER.debug("Checking field {}", fqn);

            check(NotNull.class, e2.notNull);
            check(NotNull1.class, e2.notNull1);

            analyserResultBuilder.add(CheckFinalNotModified.check(fieldInfo, NotModified.class, e2.notModified, fieldAnalysis,
                    myTypeAnalyser.getTypeAnalysis()));

            // dynamic type annotations
            check(Container.class, e2.container);

            analyserResultBuilder.add(CheckImmutable.check(fieldInfo, E1Immutable.class, e2.e1Immutable, fieldAnalysis, false, false));
            analyserResultBuilder.add(CheckImmutable.check(fieldInfo, E1Container.class, e2.e1Container, fieldAnalysis, false, false));
            analyserResultBuilder.add(CheckImmutable.check(fieldInfo, E2Immutable.class, e2.e2Immutable, fieldAnalysis, true, true));
            analyserResultBuilder.add(CheckImmutable.check(fieldInfo, E2Container.class, e2.e2Container, fieldAnalysis, true, false));
            analyserResultBuilder.add(CheckImmutable.check(fieldInfo, ERContainer.class, e2.eRContainer, fieldAnalysis, false, false));

            check(Modified.class, e2.modified);
            check(Nullable.class, e2.nullable);

            analyserResultBuilder.add(checkLinks.checkLinksForFields(fieldInfo, fieldAnalysis));
            analyserResultBuilder.add(checkLinks.checkLink1sForFields(fieldInfo, fieldAnalysis));

            analyserResultBuilder.add(checkConstant.checkConstantForFields(fieldInfo, fieldAnalysis));
        }
    }

    private void check(Class<?> annotation, AnnotationExpression annotationExpression) {
        fieldInfo.error(fieldAnalysis, annotation, annotationExpression).ifPresent(mustBeAbsent ->
                analyserResultBuilder.add(Message.newMessage(fieldInfo.newLocation(),
                        mustBeAbsent ? Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT
                                : Message.Label.ANNOTATION_ABSENT, annotation.getSimpleName())));
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
            ConditionManager cm = conditionManager.newAtStartOfNewBlock(getPrimitives(), condition,
                    Precondition.empty(getPrimitives()));
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
            if (value instanceof InlinedMethod) {
                // an anonymous type, or even an explicit functional interface, has been replaced by an inlined method
                // we cannot make the mistake of computing a value property on the return value of the method
                // (i.e. the method may return a mutable object, but the method itself is not mutable)
                throw new UnsupportedOperationException();
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
            if (property == Property.CONTAINER) return EXTERNAL_CONTAINER;
            return property;
        }

        @Override
        public Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            if (variable instanceof FieldReference) {
                return FieldAnalyserImpl.this.getVariableValue(variable);
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
                return closure.currentValue(variable, forwardEvaluationInfo);
            }

            throw new UnsupportedOperationException("Variable of " + variable.getClass() + " not implemented here");
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            return LinkedVariables.EMPTY; // TODO make sure this is right
        }
    }
}
