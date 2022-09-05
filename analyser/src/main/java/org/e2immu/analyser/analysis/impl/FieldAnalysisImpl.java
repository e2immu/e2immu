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
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Final;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.VariableFirstThen;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.IMPLIED;

public class FieldAnalysisImpl extends AnalysisImpl implements FieldAnalysis {

    private final FieldInfo fieldInfo;
    public final LinkedVariables variablesLinkedToMe;
    public final Expression value;
    public final Expression initialValue;  // value from the initialiser

    private FieldAnalysisImpl(FieldInfo fieldInfo,
                              LinkedVariables variablesLinkedToMe,
                              Expression value,
                              Expression initialValue,
                              Map<Property, DV> properties,
                              Map<AnnotationExpression, AnnotationCheck> annotations) {
        super(properties, annotations);
        this.fieldInfo = fieldInfo;
        this.variablesLinkedToMe = variablesLinkedToMe;
        this.value = value;
        this.initialValue = initialValue;
    }

    @Override
    public Expression getValue() {
        return value;
    }

    @Override
    public CausesOfDelay valuesDelayed() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public ParameterizedType concreteTypeNullWhenDelayed() {
        if (fieldInfo.type.isUnboundTypeParameter()) return fieldInfo.type;
        if (value != null) {
            return value.returnType();
        }
        return fieldInfo.type;
    }

    @Override
    public LinkedVariables getLinkedVariables() {
        return variablesLinkedToMe;
    }

    @Override
    public DV getProperty(Property property) {
        return getFieldProperty(AnalysisProvider.DEFAULT_PROVIDER, fieldInfo, fieldInfo.type.bestTypeInfo(), property);
    }

    @Override
    public Location location(Stage stage) {
        return fieldInfo.newLocation();
    }

    @Override
    public Expression getInitializerValue() {
        return initialValue;
    }

    @Override
    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public static class Builder extends AbstractAnalysisBuilder implements FieldAnalysis {
        public final TypeInfo bestType;
        public final boolean isExplicitlyFinal;
        public final ParameterizedType type;
        public final FieldInfo fieldInfo;
        public final MethodInfo sam;
        private final TypeAnalysis typeAnalysisOfOwner;
        private final AnalysisProvider analysisProvider;
        private final EventuallyFinal<Expression> initializerValue = new EventuallyFinal<>();
        private final VariableFirstThen<CausesOfDelay, List<ValueAndPropertyProxy>> values;
        private final EventuallyFinal<Expression> value = new EventuallyFinal<>();

        // end product of the dependency analysis of linkage between the variables in a method
        // if A links to B, and A is modified, then B must be too.
        // In other words, if A->B, then B cannot be @NotModified unless A is too

        // here, the key of the map are fields; the local variables and parameters are stored in method analysis
        // the values are either other fields (in which case these other fields are not linked to parameters)
        // or parameters
        public final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();

        @Override
        public void internalAllDoneCheck() {
            super.internalAllDoneCheck();
            assert initializerValue.isFinal();
            assert values.isSet();
            assert value.isFinal();
            assert linkedVariables.isFinal();
        }

        public Builder(Primitives primitives, AnalysisProvider analysisProvider, @NotModified FieldInfo fieldInfo, TypeAnalysis typeAnalysisOfOwner) {
            super(primitives, fieldInfo.name);
            this.typeAnalysisOfOwner = typeAnalysisOfOwner;
            this.bestType = fieldInfo.type.bestTypeInfo();
            isExplicitlyFinal = fieldInfo.isExplicitlyFinal();
            this.analysisProvider = analysisProvider;
            type = fieldInfo.type;
            this.sam = !fieldInfo.fieldInspection.get().fieldInitialiserIsSet() ? null :
                    fieldInfo.fieldInspection.get().getFieldInitialiser().implementationOfSingleAbstractMethod();
            this.fieldInfo = fieldInfo;
            CausesOfDelay initialDelay = initialDelay(fieldInfo);
            FieldReference fr = new FieldReference(InspectionProvider.DEFAULT, fieldInfo);
            DelayedVariableExpression dve = DelayedVariableExpression.forField(fr, 0, initialDelay);
            setValue(dve);
            linkedVariables.setVariable(dve.linkedVariables(null));
            this.values = new VariableFirstThen<>(initialDelay);
        }

        @Override
        public String markLabelFromType() {
            return typeAnalysisOfOwner.markLabel();
        }

        private static CausesOfDelay initialDelay(FieldInfo fieldInfo) {
            return fieldInfo.delay(CauseOfDelay.Cause.INITIAL_VALUE);
        }

        @Override
        public CausesOfDelay valuesDelayed() {
            if (values.isSet()) return CausesOfDelay.EMPTY;
            return values.getFirst().causesOfDelay();
        }

        @Override
        public ParameterizedType concreteTypeNullWhenDelayed() {
            if (fieldInfo.type.isUnboundTypeParameter()) return fieldInfo.type;
            if (value.isFinal()) {
                Expression efv = value.get();
                if (!efv.isEmpty()) {
                    return efv.returnType();
                }
                return fieldInfo.type;
            }
            return null;
        }

        @Override
        public FieldInfo getFieldInfo() {
            return fieldInfo;
        }

        @Override
        public Expression getInitializerValue() {
            return initializerValue.get();
        }

        @Override
        public Location location(Stage stage) {
            return fieldInfo.newLocation();
        }

        @Override
        public DV getProperty(Property property) {
            return getFieldProperty(analysisProvider, fieldInfo, bestType, property);
        }

        @Override
        public Expression getValue() {
            return value.get();
        }

        @Override
        public LinkedVariables getLinkedVariables() {
            return linkedVariables.get();
        }

        @Override
        public Analysis build() {
            return new FieldAnalysisImpl(fieldInfo,
                    linkedVariables.isVariable() ? LinkedVariables.EMPTY : linkedVariables.get(),
                    getValue(),
                    getInitializerValue(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap());
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2) {
            DV effectivelyFinal = getProperty(Property.FINAL);
            DV ownerImmutable = typeAnalysisOfOwner.getProperty(Property.IMMUTABLE);
            DV modified = getProperty(Property.MODIFIED_OUTSIDE_METHOD);

            // @Final(after=), @Final, @Variable
            if (effectivelyFinal.valueIsFalse()
                    && !ownerImmutable.isDelayed()
                    && MultiLevel.effective(ownerImmutable) == MultiLevel.Effective.EVENTUAL) {
                String labels = typeAnalysisOfOwner.markLabel();
                addAnnotation(e2.effectivelyFinal.copyWith(primitives, "after", labels));
            } else if (effectivelyFinal.valueIsTrue()) {
                if (isExplicitlyFinal) {
                    addAnnotation(E2ImmuAnnotationExpressions.create(primitives, Final.class, IMPLIED, true));
                } else {
                    addAnnotation(e2.effectivelyFinal);
                }
            }
            DV formallyImmutable = analysisProvider.typeImmutable(fieldInfo.type);
            DV dynamicallyImmutable = getProperty(Property.EXTERNAL_IMMUTABLE);
            DV formallyContainer = analysisProvider.typeContainer(fieldInfo.type);
            DV dynamicallyContainer = getProperty(Property.EXTERNAL_CONTAINER);

            // @NotModified(after=), @NotModified, @Modified
            if (modified.valueIsTrue() && !ownerImmutable.isDelayed()
                    && MultiLevel.effective(ownerImmutable) == MultiLevel.Effective.EVENTUAL) {
                if (MultiLevel.level(dynamicallyImmutable) <= MultiLevel.Level.MUTABLE.level) {
                    String labels = typeAnalysisOfOwner.markLabel();
                    addAnnotation(e2.notModified.copyWith(primitives, "after", labels));
                }// else don't bother, we'll have an immutable annotation (see e.g. eventuallyFinal in EventuallyImmutableUtil_14)
            } else {
                boolean implied = MultiLevel.isEffectivelyImmutable(ownerImmutable);
                AnnotationExpression ae;
                if (implied) {
                    Class<?> clazz = modified.valueIsFalse() ? NotModified.class : Modified.class;
                    ae = E2ImmuAnnotationExpressions.create(primitives, clazz, IMPLIED, true);
                } else {
                    ae = modified.valueIsFalse() ? e2.notModified : e2.modified;
                }
                addAnnotation(ae);
            }
            // @NotNull
            doNotNull(e2, getProperty(Property.EXTERNAL_NOT_NULL), fieldInfo.type.isPrimitiveExcludingVoid());

            // dynamic type annotations: @Immutable, @Container
            boolean immutableBetterThanFormal = dynamicallyImmutable.gt(formallyImmutable);
            boolean containerBetterThanFormal = dynamicallyContainer.gt(formallyContainer);
            DV constant = getProperty(Property.CONSTANT);
            String constantValue = MultiLevel.isAtLeastEventuallyRecursivelyImmutable(ownerImmutable) && constant.valueIsTrue()
                    ? getValue().unQuotedString() : null;

            // final boolean x = true; does NOT need an annotation
            boolean constantImplied = isExplicitlyFinal && fieldInfo.fieldInspection.get().hasFieldInitializer();
            doImmutableContainer(e2, dynamicallyImmutable, dynamicallyContainer,
                    immutableBetterThanFormal, containerBetterThanFormal, constantValue, constantImplied);

            // @Independent
            DV formallyIndependent = analysisProvider.typeIndependent(fieldInfo.type);
            DV dynamicallyIndependent = getProperty(Property.INDEPENDENT);
            doIndependent(e2, dynamicallyIndependent, formallyIndependent, dynamicallyImmutable);

            DV beforeMark = getProperty(Property.BEFORE_MARK);
            if (beforeMark.valueIsTrue()) {
                addAnnotation(e2.beforeMark);
            }
        }

        private DV typeImmutable() {
            if (bestType == null) return MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
            return// fieldInfo.owner == bestType || bestType == null ? MultiLevel.MUTABLE_DV :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(Property.IMMUTABLE);
        }

        private DV typeIndependent() {
            return// fieldInfo.owner == bestType || bestType == null ? MultiLevel.DEPENDENT_DV :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(Property.INDEPENDENT);
        }

        private DV typeContainer() {
            return //fieldInfo.owner == bestType || bestType == null ? MultiLevel.NOT_CONTAINER_DV :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(Property.CONTAINER);
        }

        public void setValues(List<ValueAndPropertyProxy> values, CausesOfDelay delayed) {
            if (delayed.isDelayed()) {
                this.values.setFirst(delayed);
            } else {
                assert values.size() > 0 : "Empty values?";
                this.values.set(values);
            }
        }

        public void setValue(Expression value) {
            if (value.isDelayed()) {
                this.value.setVariable(value);
            } else {
                this.value.setFinal(value);
            }
        }

        public List<ValueAndPropertyProxy> getValues() {
            return values.get();
        }

        /*
        why are we waiting for the initial set of values?
         */
        public CausesOfDelay valuesStatus() {
            return values.isFirst() ? values.getFirst() : CausesOfDelay.EMPTY;
        }

        public String sortedValuesString() {
            if (values.isFirst()) return values.getFirst().toString();
            return values.get().stream().map(p -> p.getValue().toString()).sorted().collect(Collectors.joining(","));
        }

        public CausesOfDelay allLinksHaveBeenEstablished() {
            return linkedVariables.isFinal() ? CausesOfDelay.EMPTY : linkedVariables.get().causesOfDelay();
        }

        public void setInitialiserValue(Expression value) {
            if (value.isDelayed()) {
                initializerValue.setVariable(value);
            } else if (!value.equals(initializerValue.get())) {
                initializerValue.setFinal(value);
            }
        }

        public void setLinkedVariables(LinkedVariables linkedVariables) {
            if (linkedVariables.isDelayed()) {
                this.linkedVariables.setVariable(linkedVariables);
            } else {
                this.linkedVariables.setFinal(linkedVariables);
            }
        }

        public boolean valuesAreLinkedToParameters(DV maxLinkLevel) {
            List<ValueAndPropertyProxy> values = getValues();
            return values.stream().allMatch(proxy -> proxy.isLinkedToParameter(maxLinkLevel));
        }
    }
}
