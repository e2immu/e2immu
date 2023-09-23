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
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analyser.statementanalyser.StatementAnalyserImpl;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analyser.util.VariableAccessReport;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.visitor.MethodAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analysis.Analysis.AnalysisMode.CONTRACTED;

// field and types have been done already!
public class ShallowMethodAnalyser extends MethodAnalyserImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShallowMethodAnalyser.class);

    private static final Set<String> EXCEPTIONS_TO_CONTAINER = Set.of("java.util.Collection.toArray(T[])");
    private final boolean enableVisitors;
    private final Function<SharedState, AnalyserResult> analyser;

    public ShallowMethodAnalyser(MethodInfo methodInfo,
                                 MethodAnalysisImpl.Builder methodAnalysis,
                                 List<ParameterAnalysis> parameterAnalyses,
                                 AnalyserContext analyserContext,
                                 boolean enableVisitors) {
        super(methodInfo, methodAnalysis, List.of(), parameterAnalyses, Map.of(), false, analyserContext);
        this.enableVisitors = enableVisitors;
        if (TypeInfo.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName)) {
            analyser = this::analyseIsFact;
        } else if (TypeInfo.IS_KNOWN_FQN.equals(methodInfo.fullyQualifiedName)) {
            analyser = this::analyseIsKnown;
        } else {
            analyser = this::internalAnalyse;
        }
    }

    @Override
    public void initialize() {
        // no-op
    }

    @Override
    public String fullyQualifiedAnalyserName() {
        return "SMA " + methodInfo.fullyQualifiedName;
    }

    @Override
    public AnalyserResult analyse(SharedState sharedState) {
        return analyser.apply(sharedState);
    }

    private AnalyserResult internalAnalyse(SharedState sharedState) {
        try {
            AnalysisStatus combined = AnalysisStatus.DONE;
            for (Map.Entry<CompanionMethodName, MethodInfo> e : methodInfo.methodInspection.get().getCompanionMethods().entrySet()) {
                CompanionMethodName cmn = e.getKey();
                if (!methodAnalysis.companionAnalyses.isSet(cmn)) {
                    LOGGER.debug("Starting companion analyser for {}", cmn);

                    CompanionAnalyser companionAnalyser = new CompanionAnalyser(analyserContext,
                            analyserContext.getTypeAnalysis(methodInfo.typeInfo), cmn, e.getValue(),
                            methodInfo, AnnotationParameters.CONTRACT);
                    AnalysisStatus analysisStatus = companionAnalyser.analyse(sharedState.iteration());
                    if (analysisStatus.isDone()) {
                        CompanionAnalysis companionAnalysis = companionAnalyser.companionAnalysis.build();
                        methodAnalysis.companionAnalyses.put(cmn, companionAnalysis);
                    } else {
                        assert analysisStatus.isDelayed();
                        LOGGER.debug("Delaying analysis of {} in {}", cmn, methodInfo.fullyQualifiedName());
                        combined = combined.combine(analysisStatus);
                    }
                }
            }
            return internalAnalyse(sharedState.iteration()).with(combined);
        } catch (RuntimeException re) {
            LOGGER.error("Error while analysing method {}", methodInfo.fullyQualifiedName);
            throw re;
        }
    }


    // dedicated method exactly for this "isFact" method
    private AnalyserResult analyseIsFact(SharedState sharedState) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                analyserContext.getPrimitives(), analyserContext, parameterInfo);
        parameterAnalysis.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        parameterAnalysis.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        parameterAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.CONTAINER_RESTRICTION, MultiLevel.NOT_CONTAINER_DV);

        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(methodInfo.typeInfo);
        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, analyserContext.getPrimitives(),
                analyserContext, analyserContext, methodInfo, typeAnalysis, parameterAnalyses);
        builder.ensureIsNotEventualUnlessOtherwiseAnnotated();
        builder.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        builder.setProperty(Property.STATIC_SIDE_EFFECTS, Property.STATIC_SIDE_EFFECTS.falseDv);
        builder.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
        builder.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
        builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
        builder.companionAnalyses.freeze();
        VariableExpression ve = new VariableExpression(parameterInfo.identifier, parameterInfo);
        builder.setSingleReturnValue(new InlinedMethod(Identifier.generate("isFact"), methodInfo, ve, Set.of(ve),
                false));
        LOGGER.debug("Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        return AnalyserResult.EMPTY;
    }


    // dedicated method exactly for this "isKnown" method
    private AnalyserResult analyseIsKnown(SharedState sharedState) {
        ParameterInfo parameterInfo = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder parameterAnalysis = new ParameterAnalysisImpl.Builder(
                analyserContext.getPrimitives(), analyserContext, parameterInfo);
        parameterAnalysis.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        parameterAnalysis.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        parameterAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        parameterAnalysis.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.MODIFIED_OUTSIDE_METHOD, DV.FALSE_DV);
        parameterAnalysis.setProperty(Property.CONTAINER_RESTRICTION, MultiLevel.NOT_CONTAINER_DV);

        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(methodInfo.typeInfo);
        List<ParameterAnalysis> parameterAnalyses = List.of((ParameterAnalysis) parameterAnalysis.build());
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(CONTRACTED, analyserContext.getPrimitives(),
                analyserContext, analyserContext, methodInfo, typeAnalysis, parameterAnalyses);
        builder.ensureIsNotEventualUnlessOtherwiseAnnotated();
        builder.setProperty(Property.IDENTITY, Property.IDENTITY.falseDv);
        builder.setProperty(Property.STATIC_SIDE_EFFECTS, Property.STATIC_SIDE_EFFECTS.falseDv);
        builder.setProperty(Property.IGNORE_MODIFICATIONS, Property.IGNORE_MODIFICATIONS.falseDv);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.MODIFIED_METHOD, DV.FALSE_DV);
        builder.setProperty(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setProperty(Property.CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
        builder.setProperty(Property.IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV);
        builder.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);

        builder.companionAnalyses.freeze();
        builder.setSingleReturnValue(UnknownExpression.forHardcodedMethodReturnValue(methodInfo.identifier,
                analyserContext.getPrimitives().booleanParameterizedType(), "isKnown return value"));
        LOGGER.debug("Provided analysis of dedicated method {}", methodInfo.fullyQualifiedName());
        return AnalyserResult.EMPTY;
    }

    private AnalyserResult internalAnalyse(int iteration) {
        E2ImmuAnnotationExpressions e2 = analyserContext.getE2ImmuAnnotationExpressions();
        boolean explicitlyEmpty = methodInfo.explicitlyEmptyMethod();

        parameterAnalyses.forEach(parameterAnalysis -> {
            ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
            List<AnnotationExpression> annotations = builder.getParameterInfo().parameterInspection.get().getAnnotations();
            analyserResultBuilder.addMessages(builder.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.PARAMETER, true,
                    annotations, e2));
            if (explicitlyEmpty) {
                DV modified = builder.getProperty(Property.MODIFIED_VARIABLE);
                if (modified.valueIsTrue()) {
                    analyserResultBuilder.add(Message.newMessage(builder.location, Message.Label.CONTRADICTING_ANNOTATIONS,
                            "Empty method cannot modify its parameters"));
                } else {
                    builder.setProperty(Property.MODIFIED_VARIABLE, DV.FALSE_DV);
                }
                builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            }
        });

        List<AnnotationExpression> annotations = methodInfo.methodInspection.get().getAnnotations();
        analyserResultBuilder.addMessages(methodAnalysis.fromAnnotationsIntoProperties(Analyser.AnalyserIdentification.METHOD,
                true, annotations, e2));

        CausesOfDelay causes;
        if (explicitlyEmpty) {
            DV modified = methodInfo.isConstructor ? DV.TRUE_DV : DV.FALSE_DV;
            methodAnalysis.setProperty(Property.MODIFIED_METHOD, modified);
            methodAnalysis.setProperty(Property.FLUENT, DV.FALSE_DV);  // no return statement...
            methodAnalysis.setProperty(Property.STATIC_SIDE_EFFECTS, DV.FALSE_DV);
            methodAnalysis.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
            methodAnalysis.setProperty(Property.CONTAINER, MultiLevel.CONTAINER_DV);
            // the method cannot return a value
            methodAnalysis.setProperty(Property.NOT_NULL_EXPRESSION, MultiLevel.NOT_INVOLVED_DV);

            CausesOfDelay c1 = computeMethodPropertiesAfterParameters();
            CausesOfDelay c2 = parameterAnalyses.stream()
                    .map(pa -> computeParameterProperties((ParameterAnalysisImpl.Builder) pa))
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
            causes = c1.merge(c2);
        } else {
            CausesOfDelay c1 = computeMethodPropertyIfNecessary(Property.FLUENT, () -> bestOfOverridesOrWorstValue(Property.FLUENT));
            CausesOfDelay c2 = computeMethodPropertyIfNecessary(Property.MODIFIED_METHOD, this::computeMethodModified);

            CausesOfDelay c3 = parameterAnalyses.stream()
                    .map(pa -> computeParameterProperties((ParameterAnalysisImpl.Builder) pa))
                    .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

            CausesOfDelay c4 = computeMethodPropertiesAfterParameters();

            CausesOfDelay c5 = computeMethodPropertyIfNecessary(Property.INDEPENDENT, this::computeMethodIndependent);
            checkMethodIndependent();
            CausesOfDelay c6 = computeMethodPropertyIfNecessary(Property.STATIC_SIDE_EFFECTS,
                    () -> bestOfOverridesOrWorstValue(Property.STATIC_SIDE_EFFECTS));
            causes = c1.merge(c2).merge(c3).merge(c4).merge(c5).merge(c6);
        }

        CompanionMethodName pre = new CompanionMethodName(methodInfo.name, CompanionMethodName.Action.PRECONDITION, null);
        MethodInfo precondition = methodInspection.getCompanionMethods().get(pre);
        if (precondition != null) {
            handlePrecondition(precondition);
        }
        methodAnalysis.ensureIsNotEventualUnlessOtherwiseAnnotated();

        if (methodInfo.hasReturnValue()) {
            Expression srv = UnknownExpression.forShallowReturnValue(methodInfo.identifier, methodInfo.returnType());
            methodAnalysis.setSingleReturnValue(srv);
        }

        if (enableVisitors) {
            List<MethodAnalyserVisitor> visitors = analyserContext.getConfiguration()
                    .debugConfiguration().afterMethodAnalyserVisitors();
            if (!visitors.isEmpty()) {
                for (MethodAnalyserVisitor methodAnalyserVisitor : visitors) {
                    methodAnalyserVisitor.visit(new MethodAnalyserVisitor.Data(iteration, BreakDelayLevel.NONE,
                            null, methodInfo, methodAnalysis,
                            parameterAnalyses, Map.of(),
                            this::getMessageStream));
                }
            }
        }

        if (causes.isDelayed()) {
            assert causes.causesStream().noneMatch(c -> c.cause() == CauseOfDelay.Cause.MIN_INT);
            // we cannot really measure progress, because we're dependent on the analysis of other types
            // add progress here to ensure that we can wait sufficiently long
            AnalysisStatus status = AnalysisStatus.of(causes);
            return new AnalyserResult(status, Messages.EMPTY, VariableAccessReport.EMPTY, List.of());
        }
        return AnalyserResult.EMPTY;
    }

    /*
    for now, preconditions can only be expressed in terms of other non-modifying boolean methods (@TestMark)
    the code is tailored towards Freezable.ensure(Not)Frozen.

     */
    private void handlePrecondition(MethodInfo precondition) {
        LOGGER.debug("Handle precondition {}", precondition.fullyQualifiedName);
        Expression expression = precondition.extractSingleReturnExpression();
        Precondition.CompanionCause companionCause = new Precondition.CompanionCause(precondition);
        Precondition pc = new Precondition(expression, List.of(companionCause));
        methodAnalysis.setPrecondition(pc);

        TranslationMap allKnownTestMarks = testMarkTranslationMap();
        Expression translated = expression.translate(analyserContext, allKnownTestMarks);
        if (translated != expression) {
            Precondition pce = new Precondition(translated, List.of(companionCause));
            methodAnalysis.setPreconditionForEventual(pce);

            // TODO this is very hardcoded, and corresponds to code in Precondition.expressionIsPossiblyNegatedMethodCall
            boolean negation = pce.expression() instanceof Negation || pce.expression() instanceof UnaryOperator uo && uo.isNegation();
            Set<FieldInfo> fields = translated.variableStream()
                    .filter(v -> v instanceof FieldReference)
                    .map(v -> ((FieldReference) v).fieldInfo)
                    .collect(Collectors.toUnmodifiableSet());
            assert !fields.isEmpty();
            MethodAnalysis.Eventual eventual = new MethodAnalysis.Eventual(fields, false, !negation, null);
            methodAnalysis.setEventual(eventual);
        }
    }

    // currently, only accepts normal test marks
    // translates every normal @TestMark's method call into the corresponding field
    // TODO this code is very hardcoded, tailored to ensure(Not)Frozen, see Trie, DependencyGraph
    private TranslationMap testMarkTranslationMap() {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
        for (MethodInfo m : methodInfo.typeInfo.typeInspection.get().methods(TypeInspection.Methods.THIS_TYPE_ONLY)) {
            if (m.methodAnalysis.isSet()) {
                MethodAnalysis.Eventual eventual = m.methodAnalysis.get().getEventual();
                if (eventual != null && eventual.test() == Boolean.TRUE) {
                    FieldInfo fieldInfo = eventual.fields().stream().findFirst().orElseThrow();
                    FieldReference fieldReference = new FieldReference(analyserContext, fieldInfo);
                    VariableExpression ve = new VariableExpression(fieldInfo.getIdentifier(), fieldReference);
                    This thisVar = new This(analyserContext, m.typeInfo);
                    Expression thisExpression = new VariableExpression(m.identifier, thisVar);
                    Identifier identifier = Identifier.joined("methodCall", List.of(thisExpression.getIdentifier()));
                    MethodCall mc = new MethodCall(identifier, thisExpression, m, List.of());
                    builder.put(mc, ve);
                }
            }
        }
        return builder.build();
    }

    private CausesOfDelay computeParameterProperties(ParameterAnalysisImpl.Builder builder) {
        CausesOfDelay c1 = computeParameterModified(builder);
        CausesOfDelay c2 = computeParameterPropertyIfNecessary(builder, Property.IMMUTABLE, this::computeParameterImmutable);
        CausesOfDelay c3 = computeParameterPropertyIfNecessary(builder, Property.INDEPENDENT, this::computeParameterIndependent);
        CausesOfDelay c4 = computeParameterPropertyIfNecessary(builder, Property.NOT_NULL_PARAMETER, this::computeParameterNotNull);
        CausesOfDelay c5 = computeParameterPropertyIfNecessary(builder, Property.CONTAINER_RESTRICTION, this::computeParameterContainerRestriction);
        computeParameterPropertyIfNecessary(builder, Property.CONTAINER, this::computeParameterContainer);
        CausesOfDelay c6 = computeParameterPropertyIfNecessary(builder, Property.IGNORE_MODIFICATIONS,
                this::computeParameterIgnoreModification);
        return c1.merge(c2).merge(c3).merge(c4).merge(c5).merge(c6);
    }

    private DV computeParameterIgnoreModification(ParameterAnalysisImpl.Builder builder) {
        return builder.getParameterInfo().parameterizedType.isAbstractInJavaUtilFunction(analyserContext) ?
                MultiLevel.IGNORE_MODS_DV : MultiLevel.NOT_IGNORE_MODS_DV;
    }

    private DV computeParameterNotNull(ParameterAnalysisImpl.Builder builder) {
        ParameterizedType pt = builder.getParameterInfo().parameterizedType;
        if (pt.isPrimitiveExcludingVoid()) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.NOT_NULL_PARAMETER);
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(override);
    }

    /*
    @Container on parameters needs to be contracted; but it does inherit; it goes into CONTAINER_RESTRICTION
     */
    private DV computeParameterContainerRestriction(ParameterAnalysisImpl.Builder builder) {
        return MultiLevel.NOT_CONTAINER_DV.maxIgnoreDelay(bestOfParameterOverrides(builder.getParameterInfo(),
                Property.CONTAINER_RESTRICTION));
    }

    /*
    CONTAINER is the value property, also for fields!!
     */
    private DV computeParameterContainer(ParameterAnalysisImpl.Builder builder) {
        return analyserContext.typeContainer(builder.getParameterInfo().parameterizedType);
    }

    private CausesOfDelay computeMethodPropertiesAfterParameters() {
        CausesOfDelay c1 = computeMethodPropertyIfNecessary(Property.IMMUTABLE, this::computeMethodImmutable);
        CausesOfDelay c2 = computeMethodPropertyIfNecessary(Property.NOT_NULL_EXPRESSION, this::computeMethodNotNull);
        CausesOfDelay c3 = computeMethodPropertyIfNecessary(Property.IDENTITY, () -> bestOfOverridesOrWorstValue(Property.IDENTITY));
        CausesOfDelay c4 = computeMethodPropertyIfNecessary(Property.IGNORE_MODIFICATIONS, () -> bestOfOverridesOrWorstValue(Property.IGNORE_MODIFICATIONS));
        CausesOfDelay c5 = computeMethodPropertyIfNecessary(Property.FINALIZER, () -> bestOfOverridesOrWorstValue(Property.FINALIZER));
        CausesOfDelay c6 = computeMethodPropertyIfNecessary(Property.CONSTANT, () -> bestOfOverridesOrWorstValue(Property.CONSTANT));
        // @Identity must come before @Container
        CausesOfDelay c7 = computeMethodPropertyIfNecessary(Property.CONTAINER, this::computeMethodContainer);
        return c1.merge(c2).merge(c3).merge(c4).merge(c5).merge(c6).merge(c7);
    }

    private CausesOfDelay computeMethodPropertyIfNecessary(Property property, Supplier<DV> computer) {
        DV inMap = methodAnalysis.getPropertyFromMapDelayWhenAbsent(property);
        if (inMap.isDelayed()) {
            DV computed = computer.get();
            //   if (computed.isDone()) {
            methodAnalysis.setProperty(property, computed);
            //    }
            return computed.causesOfDelay();
        }
        return CausesOfDelay.EMPTY;
    }

    private static CausesOfDelay computeParameterPropertyIfNecessary(ParameterAnalysisImpl.Builder builder,
                                                                     Property property,
                                                                     Function<ParameterAnalysisImpl.Builder, DV> computer) {
        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(property);
        if (inMap.isDelayed()) {
            DV computed = computer.apply(builder);
            builder.setProperty(property, computed);
            return computed.causesOfDelay();
        }
        return inMap.causesOfDelay();
    }

    private DV bestOfOverridesOrWorstValue(Property property) {
        DV best = bestOfOverrides(property);
        return property.falseDv.maxIgnoreDelay(best);
    }

    /**
     * Container on methods ONLY when the return value is
     * - one of the parameters which has been contracted with @Container.
     * - of a "final" type (so that it cannot be extended) and decorated with @Container (like java.lang.String)
     * - an array type (which cannot be extended)
     * However, if a @Container type is returned, we will assume unless annotated otherwise, that the result is
     * a container.
     * <p>
     * Unbound type parameters are definitely NOT @Container, since you can substitute them for any non-@Container type.
     */
    private DV computeMethodContainer() {
        ParameterizedType returnType = methodInfo.returnType();
        if (returnType.arrays > 0 || returnType.isPrimitiveExcludingVoid()) {
            return MultiLevel.CONTAINER_DV;
        }
        if (returnType == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR)
            return MultiLevel.NOT_INVOLVED_DV; // no decision
        TypeInfo bestType = returnType.bestTypeInfo();
        if (bestType == null) return MultiLevel.NOT_CONTAINER_DV; // unbound type parameter

        // check formal return type
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysisNullWhenAbsent(bestType);
        DV fromReturnType = typeAnalysis == null ? DV.MIN_INT_DV : typeAnalysis.getPropertyFromMapNeverDelay(Property.CONTAINER);
        DV bestOfOverrides = bestOfOverrides(Property.CONTAINER);
        DV formal = MultiLevel.NOT_CONTAINER_DV.maxIgnoreDelay(bestOfOverrides.maxIgnoreDelay(fromReturnType));
        if (MultiLevel.CONTAINER_DV.equals(formal)) return MultiLevel.CONTAINER_DV;

        // check identity and parameter contract
        if (methodAnalysis.properties.getOrDefault(Property.IDENTITY, DV.FALSE_DV).equals(DV.TRUE_DV)) {
            ParameterAnalysis p0 = parameterAnalyses.get(0);
            return p0.getProperty(Property.CONTAINER);
        }
        return MultiLevel.NOT_CONTAINER_DV;
    }

    // in a @Container type, @Fluent or void ==> @Modified, unless otherwise specified
    private DV computeMethodModified() {
        if (methodInfo.isConstructor) return DV.TRUE_DV;
        DV fluent = methodAnalysis.getProperty(Property.FLUENT);
        DV typeContainer = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getProperty(Property.CONTAINER);
        boolean voidMethod = methodInfo.noReturnValue();
        DV addToModified = DV.fromBoolDv(typeContainer.equals(MultiLevel.CONTAINER_DV) && (fluent.valueIsTrue() || voidMethod));
        return DV.FALSE_DV.maxIgnoreDelay(bestOfOverrides(Property.MODIFIED_METHOD)).max(addToModified);
    }

    private DV computeMethodImmutable() {
        ParameterizedType returnType = methodInspection.getReturnType();
        DV immutable = analyserContext.typeImmutable(returnType);
        if (immutable.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS)) {
            analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(), Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE,
                    returnType.typeInfo == null ? "Return type of " + methodInfo.fullyQualifiedName :
                            returnType.typeInfo.fullyQualifiedName));
            return MultiLevel.MUTABLE_DV;
        }
        return immutable;
    }


    private CausesOfDelay computeParameterModified(ParameterAnalysisImpl.Builder builder) {
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.MODIFIED_VARIABLE);
        TypeAnalysis ownerAnalysis = analyserContext.getTypeAnalysis(builder.getParameterInfo().owner.typeInfo);
        DV typeContainer = ownerAnalysis.getPropertyFromMapNeverDelay(Property.CONTAINER);

        DV inMap = builder.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_VARIABLE);
        if (inMap.isDelayed()) {
            DV value;
            if (typeContainer.equals(MultiLevel.CONTAINER_DV)) {
                value = DV.FALSE_DV;
            } else if (override.isDone()) {
                value = override;
            } else {
                ParameterizedType type = builder.getParameterInfo().parameterizedType;
                if (type.isPrimitiveStringClass()) {
                    value = DV.FALSE_DV;
                } else {
                    DV typeIndependent = analyserContext.typeIndependent(type);
                    value = DV.fromBoolDv(!typeIndependent.equals(MultiLevel.INDEPENDENT_DV));
                }
            }
            builder.setProperty(Property.MODIFIED_VARIABLE, value);
            return typeContainer.causesOfDelay();
        }
        if (override.valueIsFalse() && inMap.valueIsTrue()) {
            analyserResultBuilder.add(Message.newMessage(builder.getParameterInfo().newLocation(),
                    Message.Label.WORSE_THAN_OVERRIDDEN_METHOD_PARAMETER,
                    "Override was non-modifying, while this parameter is modifying"));
        } else if (typeContainer.equals(MultiLevel.CONTAINER_DV) && inMap.valueIsTrue()) {
            if (!EXCEPTIONS_TO_CONTAINER.contains(methodInfo.fullyQualifiedName)) {
                analyserResultBuilder.add(Message.newMessage(builder.getParameterInfo().newLocation(),
                        Message.Label.CONTRADICTING_ANNOTATIONS, "Type is @Container, parameter is @Modified"));
            }
        }
        return CausesOfDelay.EMPTY;
    }

    private DV computeParameterImmutable(ParameterAnalysisImpl.Builder builder) {
        return analyserContext.typeImmutable(builder.getParameterInfo().parameterizedType);
    }

    private DV computeParameterIndependent(ParameterAnalysisImpl.Builder builder) {
        DV value;
        ParameterizedType type = builder.getParameterInfo().parameterizedType;
        DV immutable = builder.getProperty(Property.IMMUTABLE);

        if (type.isPrimitiveExcludingVoid() || MultiLevel.EFFECTIVELY_IMMUTABLE_DV.equals(immutable)) {
            value = MultiLevel.INDEPENDENT_DV;
        } else {
            // @Modified needs to be marked explicitly
            DV modifiedMethod = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
            if (modifiedMethod.valueIsTrue() || methodInspection.isStatic() && methodInspection.isFactoryMethod()) {
                // note that an unbound type parameter is by default @Dependent, not @Independent1!!
                if (immutable.isDelayed()) return immutable;
                int immutableLevel = MultiLevel.level(immutable);
                DV maxImmutable = MultiLevel.independentCorrespondingToImmutableLevelDv(immutableLevel);
                TypeAnalysis ownerAnalysis = analyserContext.getTypeAnalysis(builder.getParameterInfo().owner.typeInfo);
                DV independentType = ownerAnalysis.getProperty(Property.INDEPENDENT);
                if (independentType.isDelayed()) return independentType;
                value = independentType.max(maxImmutable);
            } else {
                value = MultiLevel.INDEPENDENT_DV;
            }
        }
        DV override = bestOfParameterOverrides(builder.getParameterInfo(), Property.INDEPENDENT);
        if (override == DV.MIN_INT_DV) return value;
        return override.maxIgnoreDelay(value);
    }

    private void checkMethodIndependent() {
        DV finalValue = methodAnalysis.getProperty(Property.INDEPENDENT);
        DV overloads = methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.methodInspection.get().isPubliclyAccessible())
                .map(analyserContext::getMethodAnalysis)
                .map(ma -> ma.getProperty(Property.INDEPENDENT))
                .reduce(DV.MAX_INT_DV, DV::min);
        if (overloads != DV.MAX_INT_DV && finalValue.lt(overloads)) {
            analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                    Message.Label.METHOD_HAS_LOWER_VALUE_FOR_INDEPENDENT,
                    finalValue.label() + " instead of " + overloads.label()));
        }
    }

    private DV computeMethodIndependent() {
        DV returnValueIndependent = computeMethodIndependentReturnValue();

        // typeIndependent is set by hand in AnnotatedAPI files
        DV typeIndependent = analyserContext.getTypeAnalysis(methodInfo.typeInfo).getPropertyFromMapNeverDelay(Property.INDEPENDENT);
        DV bestOfOverrides = bestOfOverrides(Property.INDEPENDENT);
        DV result = returnValueIndependent.max(bestOfOverrides).max(typeIndependent);

        if (MultiLevel.INDEPENDENT_HC_DV.equals(result) && methodInfo.methodInspection.get().isFactoryMethod()) {
            // at least one of the parameters must be independent HC!!
            boolean hcParam = parameterAnalyses.stream()
                    .anyMatch(pa -> MultiLevel.INDEPENDENT_HC_DV.equals(pa.getProperty(Property.INDEPENDENT)));
            if (!hcParam) {
                analyserResultBuilder.add(Message.newMessage(methodInfo.newLocation(),
                        Message.Label.FACTORY_METHOD_INDEPENDENT_HC));
            }
        }
        return result;
    }

    private DV computeMethodIndependentReturnValue() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) {
            return MultiLevel.INDEPENDENT_DV;
        }
        if (methodInfo.methodInspection.get().isStatic() && !methodInspection.isFactoryMethod()) {
            // if factory method, we link return value to parameters, otherwise independent by default
            return MultiLevel.INDEPENDENT_DV;
        }
        DV identity = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.IDENTITY);
        DV modified = methodAnalysis.getPropertyFromMapDelayWhenAbsent(Property.MODIFIED_METHOD);
        if (identity.valueIsTrue() && modified.valueIsFalse()) {
            return MultiLevel.INDEPENDENT_DV; // @Identity + @NotModified -> must be @Independent
        }
        // from here on we're assuming the result is linked to the fields.

        ParameterizedType pt = methodInfo.returnType();
        if (pt.arrays > 0) {
            // array type, like int[]
            return MultiLevel.DEPENDENT_DV;
        }
        TypeInfo bestType = pt.bestTypeInfo();
        if (ParameterizedType.isUnboundTypeParameterOrJLO(bestType)) {
            // unbound type parameter T, or unbound with array T[], T[][]
            return MultiLevel.INDEPENDENT_HC_DV;
        }
        if (bestType.isPrimitiveExcludingVoid()) {
            return MultiLevel.INDEPENDENT_DV;
        }
        DV immutable = methodAnalysis.getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) {
            return immutable.causesOfDelay();
        }
        if (MultiLevel.isAtLeastEffectivelyImmutableHC(immutable)) {
            int level = MultiLevel.level(immutable);
            return MultiLevel.independentCorrespondingToImmutableLevelDv(level);
        }
        return MultiLevel.DEPENDENT_DV;
    }

    private DV computeMethodNotNull() {
        if (methodInfo.isConstructor || methodInfo.isVoid()) return MultiLevel.NOT_INVOLVED_DV; // no decision!
        if (methodInfo.returnType().isPrimitiveExcludingVoid()) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        DV fluent = methodAnalysis.getProperty(Property.FLUENT);
        if (fluent.valueIsTrue()) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        return MultiLevel.NULLABLE_DV.maxIgnoreDelay(bestOfOverrides(Property.NOT_NULL_EXPRESSION));
    }

    private DV bestOfOverrides(Property property) {
        DV bestOfOverrides = DV.MIN_INT_DV;
        for (MethodAnalysis override : methodAnalysis.getOverrides(analyserContext)) {
            DV overrideAsIs = override.getPropertyFromMapDelayWhenAbsent(property);
            if (bestOfOverrides == DV.MIN_INT_DV) {
                bestOfOverrides = overrideAsIs;
            } else {
                bestOfOverrides = bestOfOverrides.maxIgnoreDelay(overrideAsIs);
            }
        }
        return bestOfOverrides;
    }

    private DV bestOfParameterOverrides(ParameterInfo parameterInfo, Property property) {
        return methodInfo.methodResolution.get().overrides().stream()
                .filter(mi -> mi.analysisAccessible(analyserContext))
                .map(mi -> {
                    ParameterInfo p = mi.methodInspection.get().getParameters().get(parameterInfo.index);
                    ParameterAnalysis pa = analyserContext.getParameterAnalysis(p);
                    return pa.getPropertyFromMapNeverDelay(property);
                }).reduce(DV.MIN_INT_DV, DV::maxIgnoreDelay);
    }

    @Override
    public void write() {
        // everything contracted, nothing to write
    }

    @Override
    public void check() {
        // everything contracted, nothing to check
    }

    @Override
    public Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers() {
        return Stream.empty();
    }

    @Override
    public Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo) {
        return Stream.empty();
    }

    @Override
    public StatementAnalyserImpl findStatementAnalyser(String index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void logAnalysisStatuses() {
        // nothing here
    }

    @Override
    public AnalyserComponents<String, ?> getAnalyserComponents() {
        return null;
    }

    @Override
    public void makeImmutable() {
        // nothing here
    }
}
