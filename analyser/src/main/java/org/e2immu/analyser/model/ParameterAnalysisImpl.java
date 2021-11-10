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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnceMap;

import java.util.Map;

public class ParameterAnalysisImpl extends AnalysisImpl implements ParameterAnalysis {

    private final ParameterInfo parameterInfo;
    public final Map<FieldInfo, Integer> assignedToField;

    private ParameterAnalysisImpl(ParameterInfo parameterInfo,
                                  Map<VariableProperty, Integer> properties,
                                  Map<AnnotationExpression, AnnotationCheck> annotations,
                                  Map<FieldInfo, Integer> assignedToField) {
        super(properties, annotations);
        this.parameterInfo = parameterInfo;
        this.assignedToField = assignedToField;
    }

    @Override
    public DV getProperty(VariableProperty variableProperty) {
        return getParameterProperty(AnalysisProvider.DEFAULT_PROVIDER, parameterInfo, variableProperty);
    }

    @Override
    public Location location() {
        return new Location(parameterInfo);
    }

    public static class Builder extends AbstractAnalysisBuilder implements ParameterAnalysis {
        private final ParameterInfo parameterInfo;
        private final SetOnceMap<FieldInfo, Integer> assignedToField = new SetOnceMap<>();
        private final FlipSwitch delaysOnFieldsResolved = new FlipSwitch();
        public final Location location;
        private final AnalysisProvider analysisProvider;

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, ParameterInfo parameterInfo) {
            super(primitives, parameterInfo.simpleName());
            this.parameterInfo = parameterInfo;
            this.location = new Location(parameterInfo);
            this.analysisProvider = analysisProvider;
        }

        public ParameterInfo getParameterInfo() {
            return parameterInfo;
        }

        @Override
        public boolean isAssignedToFieldDelaysResolved() {
            return delaysOnFieldsResolved.isSet();
        }

        public void resolveFieldDelays() {
            if (!delaysOnFieldsResolved.isSet()) delaysOnFieldsResolved.set();
        }

        @Override
        public DV getProperty(VariableProperty variableProperty) {
            return getParameterProperty(analysisProvider, parameterInfo, variableProperty);
        }

        @Override
        public Location location() {
            return location;
        }

        @Override
        public Map<FieldInfo, DV> getAssignedToField() {
            return assignedToField.toImmutableMap();
        }

        @Override
        public Analysis build() {
            return new ParameterAnalysisImpl(parameterInfo, properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(), getAssignedToField());
        }

        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // no annotations can be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(parameterInfo.parameterizedType)) return;

            // @NotModified, @Modified
            // implicitly @NotModified when E2Immutable
            DV modified = getProperty(VariableProperty.MODIFIED_VARIABLE);
            if (parameterInfo.parameterizedType.canBeModifiedInThisClass(analysisProvider) != Boolean.TRUE) {
                AnnotationExpression ae = modified.value() == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.NOT_NULL_PARAMETER));

            // @Independent1; @Independent, @Dependent not shown
            DV independentType = parameterInfo.parameterizedType.defaultIndependent(analysisProvider);
            DV independent = getProperty(VariableProperty.INDEPENDENT);
            if (independent.value() == MultiLevel.INDEPENDENT && independentType.value() < MultiLevel.INDEPENDENT) {
                annotations.put(e2ImmuAnnotationExpressions.independent, true);
            } else if (independent.value() == MultiLevel.INDEPENDENT_1 && independentType.value() < MultiLevel.INDEPENDENT_1) {
                annotations.put(e2ImmuAnnotationExpressions.independent1, true);
            }
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
    }

}
