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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.CommutableData;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterAnalysisImpl.class);

    private final ParameterInfo parameterInfo;
    public final Map<FieldInfo, LV> assignedToField;
    private final LinkedVariables linksToOtherParameters;
    // TODO at some point private final LinkedVariables linkToReturnValueOfMethod;
    private final LV.HiddenContentSelector hiddenContentSelector;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<Property, DV> properties,
                                  Map<AnnotationExpression, AnnotationCheck> annotations,
                                  Map<FieldInfo, LV> assignedToField,
                                  LinkedVariables linksToOtherParameters,
                                  LV.HiddenContentSelector hiddenContentSelector) {
        super(properties, annotations);
        this.parameterInfo = parameterInfo;
        this.assignedToField = assignedToField;
        this.linksToOtherParameters = linksToOtherParameters;
        this.hiddenContentSelector = hiddenContentSelector;
    }

    @Override
    public String toString() {
        return parameterInfo.toString();
    }

    @Override
    public DV getProperty(Property property) {
        return getParameterProperty(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, property);
    }

    @Override
    public ParameterInfo getParameterInfo() {
        return parameterInfo;
    }

    @Override
    public Location location(Stage stage) {
        return parameterInfo.newLocation();
    }

    @Override
    public LinkedVariables getLinksToOtherParameters() {
        return linksToOtherParameters;
    }

    @Override
    public LV.HiddenContentSelector getHiddenContentSelector() {
        return hiddenContentSelector;
    }

    @Override
    public Map<FieldInfo, LV> getAssignedToField() {
        return assignedToField;
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {
        private final ParameterInfo parameterInfo;
        private final SetOnceMap<FieldInfo, LV> assignedToField = new SetOnceMap<>();
        private final EventuallyFinal<CausesOfDelay> causesOfAssignedToFieldDelays = new EventuallyFinal<>();
        public final Location location;
        private final AnalysisProvider analysisProvider;
        private final SetOnce<MethodAnalysisImpl.Builder> methodAnalysis = new SetOnce<>();
        private final SetOnce<LinkedVariables> linksToParameters = new SetOnce<>();
        private final SetOnce<LV.HiddenContentSelector> hiddenContentSelector = new SetOnce<>();

        @Override
        public void internalAllDoneCheck() {
            super.internalAllDoneCheck();
            assert causesOfAssignedToFieldDelays.isFinal();
        }

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, ParameterInfo parameterInfo) {
            super(primitives, parameterInfo.simpleName());
            this.parameterInfo = parameterInfo;
            this.location = parameterInfo.newLocation();
            this.analysisProvider = analysisProvider;
            causesOfAssignedToFieldDelays.setVariable(DelayFactory.initialDelay());
        }

        public void setMethodAnalysis(MethodAnalysisImpl.Builder methodAnalysis) {
            this.methodAnalysis.set(methodAnalysis);
        }

        @Override
        protected void addCommutable(CommutableData commutableData) {
            assert methodAnalysis.isSet() : "Set methodAnalysis immediately after its creation";
            methodAnalysis.get().addParameterCommutable(parameterInfo, commutableData);
        }

        @Override
        public String markLabelFromType() {
            return analysisProvider.getTypeAnalysis(parameterInfo.getTypeInfo()).markLabel();
        }

        public ParameterInfo getParameterInfo() {
            return parameterInfo;
        }

        @Override
        public CausesOfDelay assignedToFieldDelays() {
            return causesOfAssignedToFieldDelays.get();
        }

        public void setCausesOfAssignedToFieldDelays(CausesOfDelay causesOfDelay) {
            assert causesOfDelay.isDelayed();
            causesOfAssignedToFieldDelays.setVariable(causesOfDelay);
        }

        public void resolveFieldDelays() {
            if (!causesOfAssignedToFieldDelays.isFinal()) causesOfAssignedToFieldDelays.setFinal(CausesOfDelay.EMPTY);
        }

        @Override
        public DV getProperty(Property property) {
            return getParameterProperty(analysisProvider, parameterInfo, property);
        }

        @Override
        public Location location(Stage stage) {
            return location;
        }

        @Override
        public Map<FieldInfo, LV> getAssignedToField() {
            return assignedToField.toImmutableMap();
        }

        @Override
        public void writeHiddenContentLink(int[] hcLinkParameters, int[] dependentLinkToParameters) {
            LinkedVariables hcLv = hcLinkParameters == null ? LinkedVariables.EMPTY
                    : hcLinkToParameters(hcLinkParameters);
            LinkedVariables depLv = dependentLinkToParameters == null ? LinkedVariables.EMPTY
                    : dependentLinkToParameters(dependentLinkToParameters);
            LinkedVariables lv = hcLv.merge(depLv);
            if (!linksToParameters.isSet() || !linksToParameters.get().equals(lv)) {
                linksToParameters.set(lv);
            }
        }

        private LinkedVariables dependentLinkToParameters(int[] linkParameters) {
            Map<Variable, LV> map = new HashMap<>();
            List<ParameterInfo> parameters = parameterInfo.getMethod().methodInspection.get().getParameters();
            for (int parameterIndex : linkParameters) {
                if (parameterIndex < 0 || parameterIndex >= parameters.size()) {
                    LOGGER.error("Illegal parameter index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else if (parameterIndex == parameterInfo.index) {
                    LOGGER.error("Ignoring link to myself: index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else {
                    ParameterInfo pi = parameters.get(parameterIndex);
                    map.put(pi, LV.LINK_DEPENDENT);
                }
            }
            return LinkedVariables.of(map);
        }

        private LinkedVariables hcLinkToParameters(int[] linkParameters) {
            Map<Variable, LV> map = new HashMap<>();
            int n = (int) parameterInfo.getMethodInfo().typeInfo.typeInspection.get().typeParameters().stream()
                    .filter(TypeParameter::isUnbound).count();
            List<ParameterInfo> parameters = parameterInfo.getMethod().methodInspection.get().getParameters();
            LV.HiddenContentSelector mine = bestHiddenContentSelector(n, false, parameterInfo.parameterizedType);
            for (int parameterIndex : linkParameters) {
                if (parameterIndex < 0 || parameterIndex >= parameters.size()) {
                    LOGGER.error("Illegal parameter index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else if (parameterIndex == parameterInfo.index) {
                    LOGGER.error("Ignoring link to myself: index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else {
                    ParameterInfo pi = parameters.get(parameterIndex);
                    LV.HiddenContentSelector theirs = bestHiddenContentSelector(n,
                            pi.parameterInspection.get().isVarArgs(), pi.parameterizedType);
                    map.put(pi, LV.createHC(mine, theirs));
                }
            }
            return LinkedVariables.of(map);
        }

        /*
        situations that we want to address...
        Type parameters of the method vs type parameters of the type
         */
        private LV.HiddenContentSelector bestHiddenContentSelector(int numHiddenContentTypesOfType,
                                                                   boolean isVarargs,
                                                                   ParameterizedType parameterType) {
            ParameterizedType pt;
            if (isVarargs) {
                assert parameterType.arrays > 0;
                pt = parameterType.copyWithOneFewerArrays();
            } else {
                pt = parameterType;
            }
            if (pt.isTypeParameter() && pt.typeParameter.isUnbound()) return LV.CS_ALL;
            // TODO: what to do with arrays?
            Set<Integer> set = pt.extractTypeParameters().stream()
                    .map(tp -> tp.isMethodTypeParameter() ? numHiddenContentTypesOfType + tp.getIndex()
                            : tp.getIndex()).collect(Collectors.toUnmodifiableSet());
            if (set.isEmpty()) return LV.CS_NONE;
            return new LV.HiddenContentSelectorImpl(set);
        }


        @Override
        public LV.HiddenContentSelector getHiddenContentSelector() {
            return hiddenContentSelector.get(parameterInfo.fullyQualifiedName);
        }

        public Builder setHiddenContentSelector(LV.HiddenContentSelector hiddenContentSelector) {
            this.hiddenContentSelector.set(hiddenContentSelector);
            return this;
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo, properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(), getAssignedToField(),
                    linksToParameters.getOrDefault(LinkedVariables.EMPTY),
                    getHiddenContentSelector());
        }

        public void transferPropertiesToAnnotations(AnalyserContext analysisProvider, E2ImmuAnnotationExpressions e2) {

            // no annotations can be added to primitives
            if (parameterInfo.parameterizedType.isPrimitiveExcludingVoid()) return;

            // @NotModified, @Modified
            // implicitly @NotModified when E2Immutable
            DV modified = getProperty(Property.MODIFIED_VARIABLE);
            DV ignoreModifications = getProperty(Property.IGNORE_MODIFICATIONS);
            if (!analysisProvider.cannotBeModifiedInThisClass(parameterInfo.parameterizedType).valueIsTrue() &&
                !ignoreModifications.equals(MultiLevel.IGNORE_MODS_DV)) {
                // the explicit annotation
                AnnotationExpression ae = modified.valueIsFalse() ? e2.notModified :
                        e2.modified;
                addAnnotation(ae);
                // the negation, absent and implied, see e.g. Container_4
                AnnotationExpression negated = new AnnotationExpressionImpl(modified.valueIsFalse()
                        ? e2.modified.typeInfo() : e2.notModified.typeInfo(),
                        List.of(new MemberValuePair(E2ImmuAnnotationExpressions.IMPLIED, new BooleanConstant(primitives, true)),
                                new MemberValuePair(E2ImmuAnnotationExpressions.ABSENT, new BooleanConstant(primitives, true))));
                addAnnotation(negated);
            } else {
                AnnotationExpression implied = E2ImmuAnnotationExpressions.create(primitives, NotModified.class, E2ImmuAnnotationExpressions.IMPLIED, true);
                addAnnotation(implied);
            }

            // @NotNull
            doNotNull(e2, getProperty(Property.NOT_NULL_PARAMETER),
                    parameterInfo.parameterizedType.isPrimitiveExcludingVoid());


            DV formallyImmutable = analysisProvider.typeImmutable(parameterInfo.parameterizedType);
            DV dynamicallyImmutable = getProperty(Property.IMMUTABLE);
            DV formallyContainer = analysisProvider.typeContainer(parameterInfo.parameterizedType);
            DV dynamicallyContainer = getProperty(Property.CONTAINER);
            boolean immutableBetterThanFormal = dynamicallyImmutable.gt(formallyImmutable);
            boolean containerBetterThanFormal = dynamicallyContainer.gt(formallyContainer);
            doImmutableContainer(e2, dynamicallyImmutable, dynamicallyContainer,
                    immutableBetterThanFormal, containerBetterThanFormal, null, false);

            DV independentType = analysisProvider.typeIndependent(parameterInfo.parameterizedType);
            DV independent = getProperty(Property.INDEPENDENT);
            doIndependent(e2, independent, independentType, dynamicallyImmutable);
        }

        public boolean addAssignedToField(FieldInfo fieldInfo, LV assignedOrLinked) {
            if (!assignedToField.isSet(fieldInfo)) {
                assignedToField.put(fieldInfo, assignedOrLinked);
                return true;
            }
            return false;
        }

        public void freezeAssignedToField() {
            assignedToField.freeze();
        }

        public boolean assignedToFieldIsFrozen() {
            return assignedToField.isFrozen();
        }

        @Override
        public LinkedVariables getLinksToOtherParameters() {
            return linksToParameters.getOrDefault(LinkedVariables.EMPTY);
        }

        @Override
        public String toString() {
            return parameterInfo.toString();
        }
    }

}
