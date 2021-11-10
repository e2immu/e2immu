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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.VariableFirstThen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldAnalysisImpl extends AnalysisImpl implements FieldAnalysis {

    private final FieldInfo fieldInfo;
    public final boolean isOfTransparentType;
    public final LinkedVariables variablesLinkedToMe;
    public final Expression value;
    public final Expression initialValue;  // value from the initialiser

    private FieldAnalysisImpl(FieldInfo fieldInfo,
                              boolean isOfTransparentType,
                              LinkedVariables variablesLinkedToMe,
                              Expression value,
                              Expression initialValue,
                              Map<VariableProperty, DV> properties,
                              Map<AnnotationExpression, AnnotationCheck> annotations) {
        super(properties, annotations);
        this.fieldInfo = fieldInfo;
        this.isOfTransparentType = isOfTransparentType;
        this.variablesLinkedToMe = variablesLinkedToMe;
        this.value = value;
        this.initialValue = initialValue;
    }

    @Override
    public Expression getValue() {
        return value;
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
    public DV isTransparentType() {
        return isOfTransparentType ? Level.TRUE_DV : Level.FALSE_DV;
    }

    @Override
    public DV getProperty(VariableProperty variableProperty) {
        return getFieldProperty(AnalysisProvider.DEFAULT_PROVIDER, fieldInfo, fieldInfo.type.bestTypeInfo(), variableProperty);
    }

    @Override
    public Location location() {
        return new Location(fieldInfo);
    }

    @Override
    public Expression getInitializerValue() {
        return initialValue;
    }

    @Override
    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public interface ValueAndPropertyProxy {
        Expression getValue();

        DV getProperty(VariableProperty variableProperty);

        LinkedVariables getLinkedVariables();

        Comparator<ValueAndPropertyProxy> COMPARATOR = (p1, p2) -> ExpressionComparator.SINGLETON.compare(p1.getValue(), p2.getValue());
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
        private final VariableFirstThen<CausesOfDelay, List<ValueAndPropertyProxy>> values = new VariableFirstThen<>(CausesOfDelay.EMPTY);
        private final EventuallyFinal<Expression> value = new EventuallyFinal<>();

        // end product of the dependency analysis of linkage between the variables in a method
        // if A links to B, and A is modified, then B must be too.
        // In other words, if A->B, then B cannot be @NotModified unless A is too

        // here, the key of the map are fields; the local variables and parameters are stored in method analysis
        // the values are either other fields (in which case these other fields are not linked to parameters)
        // or parameters
        public final EventuallyFinal<LinkedVariables> linkedVariables = new EventuallyFinal<>();

        private final EventuallyFinal<DV> isOfTransparentType = new EventuallyFinal<>();

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
            LinkedVariables delayedLinkedVariables = LinkedVariables.delayedEmpty(initialDelay(fieldInfo));
            setValue(DelayedExpression.forInitialFieldValue(fieldInfo, delayedLinkedVariables, initialDelay(fieldInfo)));
            linkedVariables.setVariable(delayedLinkedVariables);
        }

        private static CausesOfDelay initialDelay(FieldInfo fieldInfo) {
            return new CausesOfDelay.SimpleSet(new CauseOfDelay.SimpleCause(fieldInfo, CauseOfDelay.Cause.INITIAL_VALUE));
        }

        @Override
        public ParameterizedType concreteTypeNullWhenDelayed() {
            if (fieldInfo.type.isUnboundTypeParameter()) return fieldInfo.type;
            if (value.isFinal()) {
                Expression efv = value.get();
                if (!efv.isUnknown()) {
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
        public Location location() {
            return new Location(fieldInfo);
        }


        public void setTransparentType(DV value) {
            if (value.isDelayed()) {
                isOfTransparentType.setVariable(value);
            } else {
                isOfTransparentType.setFinal(value);
            }
        }

        @Override
        public DV getProperty(VariableProperty variableProperty) {
            return getFieldProperty(analysisProvider, fieldInfo, bestType, variableProperty);
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
        public DV isTransparentType() {
            return isOfTransparentType.get();
        }

        @Override
        public Analysis build() {
            return new FieldAnalysisImpl(fieldInfo,
                    !isOfTransparentType.isVariable() && isOfTransparentType.get().valueIsTrue(),
                    linkedVariables.isVariable() ? LinkedVariables.EMPTY : linkedVariables.get(),
                    getValue(),
                    getInitializerValue(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap());
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            DV effectivelyFinal = getProperty(VariableProperty.FINAL);
            DV ownerImmutable = typeAnalysisOfOwner.getProperty(VariableProperty.IMMUTABLE);
            DV modified = getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);

            // @Final(after=), @Final, @Variable
            if (effectivelyFinal.valueIsFalse() && MultiLevel.effective(ownerImmutable) == MultiLevel.EVENTUAL) {
                String labels = typeAnalysisOfOwner.markLabel();
                annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal.copyWith(primitives, "after", labels), true);
            } else {
                if (effectivelyFinal.valueIsTrue() && !isExplicitlyFinal) {
                    annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal, true);
                }
                if (effectivelyFinal.valueIsFalse()) {
                    annotations.put(e2ImmuAnnotationExpressions.variableField, true);
                }
            }

            // all other annotations cannot be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(type)) return;

            // @NotModified(after=), @NotModified, @Modified
            if (modified.valueIsTrue() && MultiLevel.isEventuallyE2Immutable(ownerImmutable.value())) {
                String labels = typeAnalysisOfOwner.markLabel();
                annotations.put(e2ImmuAnnotationExpressions.notModified.copyWith(primitives, "after", labels), true);
            } else {
                AnnotationExpression ae = modified.valueIsFalse() ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.EXTERNAL_NOT_NULL));

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            DV typeImmutable = typeImmutable();
            DV fieldImmutable = getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
            if (MultiLevel.isBetterImmutable(fieldImmutable.value(), typeImmutable.value())) {
                doImmutableContainer(e2ImmuAnnotationExpressions, fieldImmutable, true);
            }
        }

        private DV typeImmutable() {
            return fieldInfo.owner == bestType || bestType == null ? MultiLevel.FALSE_DV :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        }

        public boolean isDeclaredFunctionalInterface() {
            return false; // TODO
        }

        public void setValues(List<ValueAndPropertyProxy> values, CausesOfDelay delayed) {
            if (delayed.isDelayed()) {
                this.values.setFirst(delayed);
            } else {
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
            return values.get().stream().map(p -> p.getValue().toString()).sorted().collect(Collectors.joining(","));
        }

        public CausesOfDelay allLinksHaveBeenEstablished() {
            return linkedVariables.isFinal() ? CausesOfDelay.EMPTY : linkedVariables.get().causesOfDelay();
        }

        public void setInitialiserValue(Expression value) {
            if (value.isDelayed()) {
                initializerValue.setVariable(value);
            } else {
                initializerValue.setFinal(value);
            }
        }

        public void setLinkedVariables(LinkedVariables linkedVariables) {
            if(linkedVariables.isDelayed()) {
                this.linkedVariables.setVariable(linkedVariables);
            } else {
                this.linkedVariables.setFinal(linkedVariables);
            }
        }
    }
}
