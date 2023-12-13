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

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterAnalysisImpl.class);

    private final ParameterInfo parameterInfo;
    public final Map<FieldInfo, DV> assignedToField;
    private final LinkedVariables linksToOtherParameters;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<Property, DV> properties,
                                  Map<AnnotationExpression, AnnotationCheck> annotations,
                                  Map<FieldInfo, DV> assignedToField,
                                  LinkedVariables linksToOtherParameters) {
        super(properties, annotations);
        this.parameterInfo = parameterInfo;
        this.assignedToField = assignedToField;
        this.linksToOtherParameters = linksToOtherParameters;
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
    public Map<FieldInfo, DV> getAssignedToField() {
        return assignedToField;
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {
        private final ParameterInfo parameterInfo;
        private final SetOnceMap<FieldInfo, DV> assignedToField = new SetOnceMap<>();
        private final EventuallyFinal<CausesOfDelay> causesOfAssignedToFieldDelays = new EventuallyFinal<>();
        public final Location location;
        private final AnalysisProvider analysisProvider;
        private final SetOnce<MethodAnalysisImpl.Builder> methodAnalysis = new SetOnce<>();
        private final SetOnce<LinkedVariables> linksToParameters = new SetOnce<>();

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
        public Map<FieldInfo, DV> getAssignedToField() {
            return assignedToField.toImmutableMap();
        }

        @Override
        protected void writeLinkParameters(DV linkLevel, int[] linkParameters) {
            assert linkLevel.isDone();
            Map<Variable, DV> map = new HashMap<>();
            List<ParameterInfo> parameters = parameterInfo.getMethod().methodInspection.get().getParameters();
            for (int parameterIndex : linkParameters) {
                if (parameterIndex < 0 || parameterIndex >= parameters.size()) {
                    LOGGER.error("Illegal parameter index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else if (parameterIndex == parameterInfo.index) {
                    LOGGER.error("Ignoring link to myself: index {} for method {}", parameterIndex, parameterInfo.getMethod());
                } else {
                    ParameterInfo pi = parameters.get(parameterIndex);
                    // TODO:IS_HC
                    map.put(pi, linkLevel);
                }
            }
            LinkedVariables lv = LinkedVariables.of(map);
            if (!linksToParameters.isSet() || !linksToParameters.get().equals(lv)) {
                linksToParameters.set(lv);
            }
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo, properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(), getAssignedToField(),
                    linksToParameters.getOrDefault(LinkedVariables.EMPTY));
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

        public boolean addAssignedToField(FieldInfo fieldInfo, DV assignedOrLinked) {
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
