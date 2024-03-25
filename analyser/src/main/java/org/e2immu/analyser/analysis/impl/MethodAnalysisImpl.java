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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.util.CreatePreconditionCompanion;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.inspector.impl.FieldInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.analyser.util.ParSeq;
import org.e2immu.analyser.util2.ParameterParallelGroup;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;

public class MethodAnalysisImpl extends AnalysisImpl implements MethodAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodAnalysisImpl.class);

    public final StatementAnalysis firstStatement;
    public final StatementAnalysis lastStatement;
    public final List<ParameterAnalysis> parameterAnalyses;
    public final MethodInfo methodInfo;
    public final Precondition preconditionForEventual;
    public final Eventual eventual;
    public final Precondition precondition;
    public final Set<PostCondition> postConditions;
    public final Expression singleReturnValue;
    public final Map<CompanionMethodName, CompanionAnalysis> companionAnalyses;
    public final Map<CompanionMethodName, MethodInfo> computedCompanions;
    public final AnalysisMode analysisMode;
    public final ParSeq<ParameterInfo> parallelGroups;
    public final CommutableData commutableData; // methods in a type

    public final FieldInfo getSet;
    public final GetSetEquivalent getSetEquivalent;
    public final Set<String> indicesOfEscapesNotInPreOrPostConditions;
    public final LV.HiddenContentSelector hiddenContentSelector;

    private MethodAnalysisImpl(MethodInfo methodInfo,
                               StatementAnalysis firstStatement,
                               StatementAnalysis lastStatement,
                               List<ParameterAnalysis> parameterAnalyses,
                               Expression singleReturnValue,
                               Precondition preconditionForEventual,
                               Eventual eventual,
                               Precondition precondition,
                               Set<PostCondition> postConditions,
                               Set<String> indicesOfEscapesNotInPreOrPostConditions,
                               AnalysisMode analysisMode,
                               Map<Property, DV> properties,
                               Map<AnnotationExpression, AnnotationCheck> annotations,
                               Map<CompanionMethodName, CompanionAnalysis> companionAnalyses,
                               Map<CompanionMethodName, MethodInfo> computedCompanions,
                               ParSeq<ParameterInfo> parallelGroups,
                               FieldInfo getSet,
                               GetSetEquivalent getSetEquivalent,
                               CommutableData commutableData,
                               LV.HiddenContentSelector hiddenContentSelector) {
        super(properties, annotations);
        this.methodInfo = methodInfo;
        this.firstStatement = firstStatement;
        this.lastStatement = lastStatement;
        this.parameterAnalyses = parameterAnalyses;
        this.preconditionForEventual = preconditionForEventual;
        this.eventual = eventual;
        this.precondition = Objects.requireNonNull(precondition);
        this.postConditions = postConditions;
        this.singleReturnValue = Objects.requireNonNull(singleReturnValue);
        this.companionAnalyses = companionAnalyses;
        this.computedCompanions = computedCompanions;
        this.analysisMode = analysisMode;
        this.parallelGroups = parallelGroups;
        this.getSet = getSet;
        this.indicesOfEscapesNotInPreOrPostConditions = indicesOfEscapesNotInPreOrPostConditions;
        this.commutableData = commutableData;
        this.getSetEquivalent = getSetEquivalent;
        assert !(getSetEquivalent != null && getSet != null) : "GetSet and GetSetEquivalent cannot go together";
        this.hiddenContentSelector = hiddenContentSelector;
    }

    @Override
    public LV.HiddenContentSelector getHiddenContentSelector() {
        return hiddenContentSelector;
    }

    @Override
    public GetSetEquivalent getSetEquivalent() {
        return getSetEquivalent;
    }

    public CommutableData getCommutableData() {
        return commutableData;
    }

    @Override
    public Set<String> indicesOfEscapesNotInPreOrPostConditions() {
        return indicesOfEscapesNotInPreOrPostConditions;
    }

    @Override
    public FieldInfo getSetField() {
        return getSet;
    }

    @Override
    public boolean hasBeenAnalysedUpToIteration0() {
        return true;
    }

    @Override
    public AnalysisMode analysisMode() {
        return analysisMode;
    }

    @Override
    public Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
        return computedCompanions;
    }

    @Override
    public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
        return companionAnalyses;
    }

    @Override
    public Expression getSingleReturnValue() {
        return singleReturnValue;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider, boolean complainIfNotAnalyzed) {
        return overrides(analysisProvider, methodInfo, this, true);
    }

    @Override
    public StatementAnalysis getFirstStatement() {
        return firstStatement;
    }

    @Override
    public List<ParameterAnalysis> getParameterAnalyses() {
        return parameterAnalyses;
    }

    @Override
    public StatementAnalysis getLastStatement() {
        return lastStatement;
    }

    @Override
    public Precondition getPreconditionForEventual() {
        return preconditionForEventual;
    }

    @Override
    public Eventual getEventual() {
        return eventual;
    }

    @Override
    public boolean eventualIsSet() {
        return eventual != null;
    }

    @Override
    public CausesOfDelay eventualStatus() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public CausesOfDelay preconditionStatus() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public void markFirstIteration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Precondition getPrecondition() {
        return precondition;
    }

    @Override
    public Set<PostCondition> getPostConditions() {
        return postConditions;
    }

    @Override
    public DV getProperty(Property property) {
        return getMethodProperty(property);
    }

    @Override
    public Location location(Stage stage) {
        return methodInfo.newLocation();
    }

    @Override
    public ParSeq<ParameterInfo> getParallelGroups() {
        return parallelGroups;
    }

    @Override
    public <X extends Comparable<? super X>> List<X> sortAccordingToParallelGroupsAndNaturalOrder(List<X> xs) {
        if (parallelGroups == null) throw new NullPointerException("No parallel groups available");
        return parallelGroups.sortParallels(xs, Comparator.naturalOrder());
    }

    @Override
    public DV valueFromOverrides(AnalysisProvider analysisProvider, Property property) {
        return valueFromOverrides(this, analysisProvider, property);
    }

    public static DV valueFromOverrides(MethodAnalysis methodAnalysis, AnalysisProvider analysisProvider, Property property) {
        Set<MethodAnalysis> overrides = methodAnalysis.getOverrides(analysisProvider, true);
        return overrides.stream()
                .map(ma -> ma.getPropertyFromMapDelayWhenAbsent(property))
                .reduce(DelayFactory.initialDelay(), DV::max);
    }

    public static class Builder extends AbstractAnalysisBuilder implements MethodAnalysis {
        private final FlipSwitch firstIteration = new FlipSwitch();
        public final ParameterizedType returnType;
        public final MethodInfo methodInfo;
        private final SetOnce<StatementAnalysis> firstStatement = new SetOnce<>();
        public final List<ParameterAnalysis> parameterAnalyses;
        public final TypeAnalysis typeAnalysisOfOwner;
        private final AnalysisProvider analysisProvider;
        private final InspectionProvider inspectionProvider;

        private final EventuallyFinal<Precondition> preconditionForEventual = new EventuallyFinal<>();
        private final EventuallyFinal<Eventual> eventual = new EventuallyFinal<>();
        private final EventuallyFinal<Expression> singleReturnValue = new EventuallyFinal<>();
        private final SetOnce<ParSeq<ParameterInfo>> parallelGroups = new SetOnce<>();
        private final SetOnceMap<ParameterInfo, CommutableData> parallelGroupBuilder = new SetOnceMap<>();
        private final SetOnce<FieldInfo> getSet = new SetOnce<>();

        private final EventuallyFinal<Precondition> precondition = new EventuallyFinal<>();
        private final SetOnce<Set<PostCondition>> postConditions = new SetOnce<>();
        private CausesOfDelay postConditionDelays;
        private final SetOnce<Set<String>> indicesOfEscapesNotInPreOrPostConditions = new SetOnce<>();

        public final SetOnceMap<CompanionMethodName, CompanionAnalysis> companionAnalyses = new SetOnceMap<>();
        public final SetOnceMap<CompanionMethodName, MethodInfo> computedCompanions = new SetOnceMap<>();

        public final AnalysisMode analysisMode;

        private final SetOnce<CommutableData> commutableData = new SetOnce<>();

        private final SetOnce<GetSetEquivalent> getSetEquivalent = new SetOnce<>();
        private final SetOnce<LV.HiddenContentSelector> hiddenContentSelector = new SetOnce<>();

        @Override
        public void internalAllDoneCheck() {
            super.internalAllDoneCheck();
            assert preconditionForEventual.isFinal();
            assert eventual.isFinal();
            assert singleReturnValue.isFinal();
            assert precondition.isFinal();

        }

        @Override
        public String markLabelFromType() {
            // value1 should be present when the property value is EVENTUAL or EVENTUAL_BEFORE, but not with _AFTER (see e.g.
            // test EventuallyImmutableUtil_13)
            DV immutable = getProperty(Property.IMMUTABLE);
            boolean isAfter = immutable.isDone() && MultiLevel.effective(immutable) == MultiLevel.Effective.EVENTUAL_AFTER;
            if (isAfter) return "";
            TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(methodInfo.typeInfo);
            return typeAnalysis.markLabel();
        }

        @Override
        protected void writeTypeEventualFields(String after) {
            // do nothing here -- see EventuallyImmutable_6, where we write the after="frozen" for the result of
            //org.e2immu.support.AddOnceSet.toImmutableSet()
            // IMPROVE should we write something?
        }

        @Override
        public AnalysisMode analysisMode() {
            return analysisMode;
        }

        @Override
        public MethodInfo getMethodInfo() {
            return methodInfo;
        }

        @Override
        public Precondition getPrecondition() {
            return precondition.get();
        }

        @Override
        public Set<PostCondition> getPostConditions() {
            return postConditions.getOrDefaultNull();
        }

        public void setFinalPostConditions(Set<PostCondition> postConditions) {
            this.postConditions.set(postConditions);
        }

        public CausesOfDelay getPostConditionDelays() {
            return postConditionDelays;
        }

        public void setPostConditionDelays(CausesOfDelay postConditionDelays) {
            this.postConditionDelays = postConditionDelays;
        }

        @Override
        public DV valueFromOverrides(AnalysisProvider analysisProvider, Property property) {
            return MethodAnalysisImpl.valueFromOverrides(this, analysisProvider, property);
        }

        @Override
        public boolean eventualIsSet() {
            return eventual.isFinal();
        }

        public void setPreconditionForEventual(Precondition precondition) {
            assert precondition != null;
            if (precondition.isDelayed()) {
                preconditionForEventual.setVariable(precondition);
            } else if (!precondition.equals(preconditionForEventual.get())) {
                preconditionForEventual.setFinal(precondition);
            }
        }

        @Override
        public CausesOfDelay eventualStatus() {
            return eventual.get().causesOfDelay();
        }

        @Override
        public CausesOfDelay preconditionStatus() {
            Precondition pc = precondition.get();
            assert pc != null;
            CausesOfDelay causes = pc.expression().causesOfDelay();
            assert causes.isDone() || this.precondition.isVariable();
            return causes;
        }

        @Override
        public ParSeq<ParameterInfo> getParallelGroups() {
            return parallelGroups.isSet() ? parallelGroups.get()
                    : parallelGroupBuilder.isEmpty() ? null : buildParallelGroups();
        }

        @Override
        public <X extends Comparable<? super X>> List<X> sortAccordingToParallelGroupsAndNaturalOrder(List<X> parameterExpressions) {
            return parallelGroups.get().sortParallels(parameterExpressions, Comparator.naturalOrder());
        }

        public Builder(AnalysisMode analysisMode,
                       Primitives primitives,
                       AnalysisProvider analysisProvider,
                       InspectionProvider inspectionProvider,
                       MethodInfo methodInfo,
                       TypeAnalysis typeAnalysisOfOwner,
                       List<ParameterAnalysis> parameterAnalyses) {
            super(primitives, methodInfo.name);
            this.inspectionProvider = inspectionProvider;
            this.analysisMode = analysisMode;
            this.typeAnalysisOfOwner = typeAnalysisOfOwner; // can be null in special situations
            this.parameterAnalyses = parameterAnalyses;
            this.methodInfo = methodInfo;
            this.returnType = methodInfo.returnType();
            this.analysisProvider = analysisProvider;
            precondition.setVariable(Precondition.noInformationYet(methodInfo.newLocation(), primitives));
            Expression delayedPreconditionForEventual = DelayedExpression.forPrecondition(methodInfo.identifier,
                    primitives, EmptyExpression.EMPTY_EXPRESSION, initialDelay(methodInfo));
            preconditionForEventual.setVariable(Precondition.forDelayed(delayedPreconditionForEventual));
            eventual.setVariable(MethodAnalysis.delayedEventual(initialDelay(methodInfo)));
            if (!methodInfo.hasReturnValue()) {
                UnknownExpression u = UnknownExpression.forNoReturnValue(methodInfo.identifier,
                        primitives.voidParameterizedType());
                singleReturnValue.setFinal(u);
            } else {
                // same as in MethodAnalyserImpl, which we don't have access to here
                DelayedExpression de = DelayedExpression.forMethod(methodInfo.identifier, methodInfo,
                        methodInfo.returnType(),
                        EmptyExpression.EMPTY_EXPRESSION,
                        methodInfo.delay(CauseOfDelay.Cause.SINGLE_RETURN_VALUE), Map.of());
                singleReturnValue.setVariable(de);
            }
        }

        private static CausesOfDelay initialDelay(MethodInfo methodInfo) {
            return methodInfo.delay(CauseOfDelay.Cause.INITIAL_VALUE);
        }

        @Override
        public MethodAnalysis build() {
            return new MethodAnalysisImpl(methodInfo,
                    firstStatement.getOrDefaultNull(),
                    getLastStatement(),
                    List.copyOf(parameterAnalyses.stream()
                            .map(parameterAnalysis -> parameterAnalysis instanceof ParameterAnalysisImpl.Builder builder ?
                                    (ParameterAnalysis) builder.build() : parameterAnalysis).collect(Collectors.toList())),
                    getSingleReturnValue(),
                    preconditionForEventual.get(),
                    eventual.get(),
                    precondition.isFinal() ? precondition.get() : Precondition.empty(primitives),
                    postConditions.getOrDefault(Set.of()),
                    indicesOfEscapesNotInPreOrPostConditions.getOrDefault(Set.of()),
                    analysisMode(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(),
                    getCompanionAnalyses(),
                    getComputedCompanions(),
                    getParallelGroups(),
                    getSetField(),
                    getSetEquivalent(),
                    getCommutableData(),
                    getHiddenContentSelector());
        }

        /*
        Currently accepts only the all-parallel situation.
         */
        private ParSeq<ParameterInfo> buildParallelGroups() {
            boolean allInSameParallelGroup = parameterAnalyses.stream()
                    .map(ParameterAnalysis::getParameterInfo)
                    .allMatch(pi -> parallelGroupBuilder.isSet(pi) && parallelGroupBuilder.get(pi).isDefault());
            if (allInSameParallelGroup) {
                return new ParameterParallelGroup();
            }
            throw new UnsupportedOperationException("NYI");
        }

        @Override
        protected void addCommutable(CommutableData commutableData) {
            this.commutableData.set(commutableData);
        }

        @Override
        public CommutableData getCommutableData() {
            return commutableData.getOrDefaultNull();
        }

        public void addParameterCommutable(ParameterInfo parameterInfo, CommutableData commutableData) {
            parallelGroupBuilder.put(parameterInfo, commutableData);
        }

        @Override
        protected void getSet(String fieldName) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
            assert !getSetEquivalent.isSet();
            assert !getSet.isSet();
            if (methodInfo.isConstructor() || methodInspection.isFactoryMethod()) {
                /*
                 @GetSet on a constructor or factory method has a well-defined meaning.
                 We search for the smallest constructor or factory method with the same name which
                 is compatible with a subset of the parameters. Those not in the smallest, get marked as @GetSet
                 replacements.
                 */
                TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
                Stream<MethodInfo> candidateStream;
                if (methodInfo.isConstructor()) {
                    candidateStream = typeInspection.constructorStream(TypeInspection.Methods.THIS_TYPE_ONLY);
                } else {
                    candidateStream = typeInspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                            .filter(m -> m.name.equals(methodInfo.name));
                }
                GetSetEquivalent gse = findBestCompatibleMethod(candidateStream, methodInspection);
                if (gse != null) {
                    this.getSetEquivalent.set(gse);
                }
                return;
            }
            FieldInfo fieldInfo = fieldName == null ? extractFieldFromMethod() : findOrCreateField(fieldName);
            getSet.set(fieldInfo);
        }

        /*
        Return the smallest compatible method, or null. Compatible means that any parameter in the smaller method
        must exist in the larger one. Empty parameter lists always win.
         */
        private GetSetEquivalent findBestCompatibleMethod(Stream<MethodInfo> candidateStream, MethodInspection target) {
            return candidateStream
                    .map(mi -> createGetSetEquivalent(mi, target))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt(gse -> gse.convertToGetSet().size()))
                    .orElse(null);
        }

        /*
        return null when not compatible.
         */
        private GetSetEquivalent createGetSetEquivalent(MethodInfo candidate, MethodInspection target) {
            MethodInspection mi = inspectionProvider.getMethodInspection(candidate);
            if (mi.getParameters().size() >= target.getParameters().size()) return null;
            Set<ParameterInfo> params = new HashSet<>(target.getParameters());
            for (ParameterInfo pi : mi.getParameters()) {
                ParameterInfo inSet = params.stream()
                        .filter(p -> p.name.equals(pi.name) && p.parameterizedType.equals(pi.parameterizedType))
                        .findFirst().orElse(null);
                if (inSet == null) return null;
                params.remove(inSet);
            }
            return new GetSetEquivalent(candidate, params);
        }

        // SEE ALSO CheckGetSet.class
        private FieldInfo extractFieldFromMethod() {
            String extractedName;
            String methodName = methodInfo.name;
            int length = methodName.length();
            boolean set = methodName.startsWith("set");
            boolean has = methodName.startsWith("has");
            boolean get = methodName.startsWith("get");
            boolean is = methodName.startsWith("is");
            if (length >= 4 && (set || has || get) && Character.isUpperCase(methodName.charAt(3))) {
                extractedName = methodName.substring(3);
            } else if (length >= 3 && is && Character.isUpperCase(methodName.charAt(2))) {
                extractedName = methodName.substring(2);
            } else {
                extractedName = methodName;
            }
            String decapitalized = Character.toLowerCase(extractedName.charAt(0)) + extractedName.substring(1);
            FieldInfo fieldInfo = findOrCreateField(decapitalized);
            MethodInspection mi = inspectionProvider.getMethodInspection(methodInfo);
            assert !is || fieldInfo.type.isBoolean();
            assert !has || fieldInfo.type.isBoolean();
            assert set || mi.getParameters().isEmpty();
            assert !set || mi.getParameters().size() == 1
                           && (mi.getReturnType().isVoidOrJavaLangVoid() || mi.getReturnType().typeInfo == methodInfo.typeInfo);
            return fieldInfo;
        }

        private FieldInfo findOrCreateField(String fieldName) {
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(methodInfo.typeInfo);
            Optional<FieldInfo> fieldInfo = typeInspection.fields().stream()
                    .filter(f -> f.name.equals(fieldName)).findFirst(); // TODO we're not going up in the hierarchy
            fieldInfo.ifPresent(f -> {
                // depending on the order, the field can be created for a getter or a setter.
                if (inspectionProvider.getFieldInspection(f).isSynthetic()) {
                    getSet.set(f);
                }
            });
            return fieldInfo.orElseGet(() -> createSyntheticGetSetField(fieldName));
        }

        private FieldInfo createSyntheticGetSetField(String fieldName) {
            MethodInspection methodInspection = inspectionProvider.getMethodInspection(methodInfo);
            ParameterizedType type = methodInspection.getSetType();
            FieldInfo newField = new FieldInfo(methodInfo.identifier, type, fieldName, methodInfo.typeInfo);
            FieldInspectionImpl.Builder b = new FieldInspectionImpl.Builder(newField);
            b.addModifier(FieldModifier.PRIVATE); // even though we'll access it
            b.setSynthetic(true);
            newField.fieldInspection.set(b.build(inspectionProvider));
            LOGGER.info("Added synthetic getter-setter field {}", newField.fullyQualifiedName);
            return newField;
        }

        // used by CM
        @SuppressWarnings("unused")
        public void setParallelGroups(ParSeq<ParameterInfo> parallelGroups) {
            this.parallelGroups.set(parallelGroups);
        }

        @Override
        public Map<CompanionMethodName, MethodInfo> getComputedCompanions() {
            return computedCompanions.toImmutableMap();
        }

        @Override
        public Map<CompanionMethodName, CompanionAnalysis> getCompanionAnalyses() {
            return companionAnalyses.toImmutableMap();
        }

        @Override
        public Expression getSingleReturnValue() {
            return singleReturnValue.get();
        }

        public void setSingleReturnValue(Expression expression) {
            if (expression.isDelayed()) {
                singleReturnValue.setVariable(expression);
            } else if (!expression.equals(singleReturnValue.get())) {
                singleReturnValue.setFinal(expression);
            }
        }

        @Override
        public Location location(Stage stage) {
            return methodInfo.newLocation();
        }

        @Override
        public DV getProperty(Property property) {
            return getMethodProperty(property);
        }

        public void transferPropertiesToAnnotations(AnalyserContext analyserContext, E2ImmuAnnotationExpressions e2) {
            DV modified = getProperty(Property.MODIFIED_METHOD);

            // @Precondition
            if (precondition.isFinal()) {
                Precondition pc = precondition.get();
                if (!(pc.expression() instanceof BooleanConstant)) {
                    // generate a companion method, but only when the precondition is non-trivial
                    new CreatePreconditionCompanion(analyserContext, analyserContext)
                            .addPreconditionCompanion(methodInfo, this, pc.expression());
                }
            }

            if (getProperty(Property.STATIC_SIDE_EFFECTS).valueIsTrue()) {
                addAnnotation(e2.staticSideEffects);
            }

            if (methodInfo.isConstructor()) return;

            // @NotModified, @Modified
            DV ownerImmutable = typeAnalysisOfOwner == null ? MultiLevel.MUTABLE_INCONCLUSIVE
                    : typeAnalysisOfOwner.getProperty(Property.IMMUTABLE);
            boolean implied = MultiLevel.isEffectivelyImmutable(ownerImmutable);
            AnnotationExpression ae;
            if (modified.valueIsTrue()) {
                if (methodInfo.inConstruction()) {
                    ae = E2ImmuAnnotationExpressions.create(primitives, Modified.class, CONSTRUCTION, true);
                } else if (implied) {
                    ae = E2ImmuAnnotationExpressions.create(primitives, Modified.class, IMPLIED, true);
                } else {
                    ae = e2.modified;
                }
            } else {
                if (implied) {
                    ae = E2ImmuAnnotationExpressions.create(primitives, NotModified.class, IMPLIED, true);
                } else {
                    ae = e2.notModified;
                }
            }
            addAnnotation(ae);

            DV formallyImmutable = analysisProvider.typeImmutable(returnType);
            DV dynamicallyImmutable = getProperty(Property.IMMUTABLE);
            DV formallyContainer = analysisProvider.typeContainer(returnType);
            DV dynamicallyContainer = getProperty(Property.CONTAINER);
            boolean immutableBetterThanFormal = dynamicallyImmutable.gt(formallyImmutable);
            boolean containerBetterThanFormal = dynamicallyContainer.gt(formallyContainer);
            DV constant = getProperty(Property.CONSTANT);
            String constantValue = constant.valueIsTrue() ? getSingleReturnValue().unQuotedString() : null;
            boolean constantImplied = methodInfo.singleStatementReturnConstant();

            doImmutableContainer(e2, dynamicallyImmutable, dynamicallyContainer, immutableBetterThanFormal,
                    containerBetterThanFormal, constantValue, constantImplied);

            // @GetSet
            if (getSet.isSet() && !inspectionProvider.getMethodInspection(methodInfo).isSynthetic()) {
                if (fieldNameAgreesWithGetSet(getSet.get().name, methodInfo.name, getSet.get().type.isBoolean())) {
                    addAnnotation(e2.getSet);
                } else {
                    AnnotationExpression getSetWithParam = new AnnotationExpressionImpl(e2.getSet.typeInfo(),
                            List.of(new MemberValuePair(VALUE, new StringConstant(primitives, getSet.get().name))));
                    addAnnotation(getSetWithParam);
                }
            }

            if (returnType.isVoidOrJavaLangVoid()) return;

            // @Identity
            if (getProperty(Property.IDENTITY).valueIsTrue()) {
                addAnnotation(e2.identity);
            }

            // all other annotations cannot be added to primitives
            if (returnType.isPrimitiveExcludingVoid()) return;

            // @Fluent
            if (getProperty(Property.FLUENT).valueIsTrue()) {
                addAnnotation(e2.fluent);
            }

            // @NotNull
            doNotNull(e2, getProperty(Property.NOT_NULL_EXPRESSION), methodInfo.returnType().isPrimitiveExcludingVoid());

            // @Dependent @Independent
            DV independent = getProperty(Property.INDEPENDENT);
            DV formallyIndependent = analyserContext.typeIndependent(methodInfo.returnType());
            doIndependent(e2, independent, formallyIndependent, dynamicallyImmutable);
        }

        private boolean fieldNameAgreesWithGetSet(String fieldName, String getSet, boolean isBooleanType) {
            if (fieldName.equals(getSet)) return true; // accessor
            String capitalized = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            if (isBooleanType && (getSet.equals("is" + capitalized) || getSet.equals("has" + capitalized))) return true;
            return !isBooleanType && (getSet.equals("get" + capitalized) || getSet.equals("set" + capitalized));
        }

        protected void writeEventual(Eventual eventual) {
            ContractMark contractMark = new ContractMark(eventual.fields());
            preconditionForEventual.setFinal(new Precondition(contractMark, List.of()));
            this.eventual.setFinal(eventual);
        }

        @Override
        public Set<MethodAnalysis> getOverrides(AnalysisProvider analysisProvider, boolean complainIfNotAnalyzed) {
            return overrides(analysisProvider, methodInfo, this, complainIfNotAnalyzed);
        }

        @Override
        public StatementAnalysis getFirstStatement() {
            return firstStatement.getOrDefaultNull();
        }

        @Override
        public List<ParameterAnalysis> getParameterAnalyses() {
            return parameterAnalyses;
        }

        @Override
        public StatementAnalysis getLastStatement(boolean excludeThrows) {
            // we're not "caching" it during analysis; it may change (?) over iterations
            StatementAnalysis first = firstStatement.getOrDefaultNull();
            return first == null ? null : first.lastStatement(excludeThrows);
        }

        @Override
        public Precondition getPreconditionForEventual() {
            return preconditionForEventual.get();
        }

        @Override
        public Eventual getEventual() {
            return eventual.get();
        }

        public void setFirstStatement(StatementAnalysis firstStatement) {
            this.firstStatement.set(firstStatement);
        }

        @Override
        public void markFirstIteration() {
            firstIteration.set();
        }

        @Override
        public boolean hasBeenAnalysedUpToIteration0() {
            return firstIteration.isSet();
        }

        public void addCompanion(CompanionMethodName companionMethodName, MethodInfo companion) {
            computedCompanions.put(companionMethodName, companion);
        }

        public void setEventualDelay(Eventual eventual) {
            assert eventual.causesOfDelay().isDelayed();
            this.eventual.setVariable(eventual);
        }

        public void setEventual(Eventual eventual) {
            this.eventual.setFinal(eventual);
        }

        @Override
        protected void writeEventual(String markValue, boolean mark, Boolean isAfter, Boolean test) {
            Set<FieldInfo> fields = methodInfo.typeInfo.findFields(inspectionProvider, markValue);
            writeEventual(new MethodAnalysis.Eventual(fields, mark, isAfter, test));
        }

        public void setPrecondition(Precondition pc) {
            if (pc.expression().isDelayed()) {
                precondition.setVariable(pc);
            } else {
                precondition.setFinal(pc);
            }
        }

        public void ensureIsNotEventualUnlessOtherwiseAnnotated() {
            if (!precondition.isFinal()) setPrecondition(Precondition.empty(primitives));
            if (!preconditionForEventual.isFinal()) setPreconditionForEventual(Precondition.empty(primitives));
            if (!eventual.isFinal()) setEventual(MethodAnalysis.NOT_EVENTUAL);
        }

        public boolean singleReturnValueIsVariable() {
            return !singleReturnValue.isFinal();
        }

        @Override
        public FieldInfo getSetField() {
            return getSet.getOrDefaultNull();
        }

        public void setGetSetField(FieldInfo fieldInfo) {
            getSet.set(fieldInfo);
        }

        public boolean getSetFieldIsNotYetSet() {
            return !getSet.isSet();
        }

        @Override
        public Set<String> indicesOfEscapesNotInPreOrPostConditions() {
            return indicesOfEscapesNotInPreOrPostConditions.get();
        }

        public void setIndicesOfEscapeNotInPreOrPostCondition(Set<String> indices) {
            indicesOfEscapesNotInPreOrPostConditions.set(Set.copyOf(indices));
        }

        @Override
        public GetSetEquivalent getSetEquivalent() {
            return this.getSetEquivalent.getOrDefaultNull();
        }

        public boolean preconditionIsVariable() {
            return precondition.isVariable();
        }

        public void preconditionSetVariable(Precondition pc) {
            precondition.setVariable(pc);
        }

        public void preconditionSetFinal(Precondition pc) {
            precondition.setFinal(pc);
        }

        public Precondition preconditionGet() {
            return precondition.get();
        }

        @Override
        public LV.HiddenContentSelector getHiddenContentSelector() {
            return hiddenContentSelector.get(methodInfo.fullyQualifiedName);
        }

        public void setHiddenContentSelector(LV.HiddenContentSelector hiddenContentSelector) {
            this.hiddenContentSelector.set(hiddenContentSelector);
        }
    }

    private static Set<MethodAnalysis> overrides(AnalysisProvider analysisProvider,
                                                 MethodInfo methodInfo,
                                                 MethodAnalysis methodAnalysis,
                                                 boolean complainIfNotAnalyzed) {
        try {
            return methodInfo.methodResolution.get().overrides().stream()
                    .filter(mi -> mi.analysisAccessible(InspectionProvider.DEFAULT))
                    .map(mi -> mi == methodInfo ? methodAnalysis :
                            complainIfNotAnalyzed ?
                                    analysisProvider.getMethodAnalysis(mi) :
                                    analysisProvider.getMethodAnalysisNullWhenAbsent(mi))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (RuntimeException rte) {
            LOGGER.error("Cannot compute method analysis of {}", methodInfo.distinguishingName());
            throw rte;
        }
    }
}
