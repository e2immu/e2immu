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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldAnalysisImpl extends AnalysisImpl implements FieldAnalysis {

    private final FieldInfo fieldInfo;
    public final boolean isOfTransparentType;
    public final LinkedVariables variablesLinkedToMe;
    public final LinkedVariables variablesLinked1ToMe;
    public final Expression effectivelyFinalValue;
    public final Expression initialValue;  // value from the initialiser

    private FieldAnalysisImpl(FieldInfo fieldInfo,
                              boolean isOfTransparentType,
                              LinkedVariables variablesLinkedToMe,
                              LinkedVariables variablesLinked1ToMe,
                              Expression effectivelyFinalValue,
                              Expression initialValue,
                              Map<VariableProperty, Integer> properties,
                              Map<AnnotationExpression, AnnotationCheck> annotations) {
        super(properties, annotations);
        this.fieldInfo = fieldInfo;
        this.isOfTransparentType = isOfTransparentType;
        this.variablesLinkedToMe = variablesLinkedToMe;
        this.effectivelyFinalValue = effectivelyFinalValue;
        this.initialValue = initialValue;
        this.variablesLinked1ToMe = variablesLinked1ToMe;
    }

    @Override
    public Expression getEffectivelyFinalValue() {
        return effectivelyFinalValue;
    }

    @Override
    public ParameterizedType concreteTypeNullWhenDelayed() {
        if (fieldInfo.type.isUnboundParameterType()) return fieldInfo.type;
        if (effectivelyFinalValue != null) {
            return effectivelyFinalValue.returnType();
        }
        return fieldInfo.type;
    }

    @Override
    public LinkedVariables getLinkedVariables() {
        return variablesLinkedToMe;
    }

    @Override
    public LinkedVariables getLinked1Variables() {
        return variablesLinked1ToMe;
    }

    @Override
    public Boolean isTransparentType() {
        return isOfTransparentType;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getFieldProperty(AnalysisProvider.DEFAULT_PROVIDER, fieldInfo, fieldInfo.type.bestTypeInfo(), variableProperty);
    }

    @Override
    public Location location() {
        return new Location(fieldInfo);
    }

    @Override
    public Expression getInitialValue() {
        return initialValue;
    }

    @Override
    public FieldInfo getFieldInfo() {
        return fieldInfo;
    }

    public interface ValueAndPropertyProxy {
        Expression getValue();

        boolean isDelayedValue();

        int getProperty(VariableProperty variableProperty);

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
        public final EventuallyFinal<Expression> initialValue = new EventuallyFinal<>();

        private final EventuallyFinal<List<ValueAndPropertyProxy>> values = new EventuallyFinal<>();

        public final FlipSwitch allLinksHaveBeenEstablished = new FlipSwitch();

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
        }

        @Override
        public ParameterizedType concreteTypeNullWhenDelayed() {
            if (fieldInfo.type.isUnboundParameterType()) return fieldInfo.type;
            if (effectivelyFinalValue.isSet()) {
                Expression efv = effectivelyFinalValue.get();
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
        public Expression getInitialValue() {
            return initialValue.get();
        }

        @Override
        public Location location() {
            return new Location(fieldInfo);
        }

        // if the field turns out to be effectively final, it can have a value
        public final SetOnce<Expression> effectivelyFinalValue = new SetOnce<>();

        // end product of the dependency analysis of linkage between the variables in a method
        // if A links to B, and A is modified, then B must be too.
        // In other words, if A->B, then B cannot be @NotModified unless A is too

        // here, the key of the map are fields; the local variables and parameters are stored in method analysis
        // the values are either other fields (in which case these other fields are not linked to parameters)
        // or parameters
        public final SetOnce<LinkedVariables> linkedVariables = new SetOnce<>();
        public final SetOnce<LinkedVariables> linked1Variables = new SetOnce<>();

        private final SetOnce<Boolean> isOfTransparentType = new SetOnce<>();

        public void setTransparentType(boolean value) {
            isOfTransparentType.set(value);
        }

        public boolean isOfTransparentTypeIsSet() {
            return isOfTransparentType.isSet();
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getFieldProperty(analysisProvider, fieldInfo, bestType, variableProperty);
        }

        @Override
        public Expression getEffectivelyFinalValue() {
            Expression v = effectivelyFinalValue.getOrDefaultNull();
            if (v != null && v.isUnknown()) return null;
            return v;
        }

        @Override
        public LinkedVariables getLinkedVariables() {
            return linkedVariables.getOrDefault(LinkedVariables.DELAYED_EMPTY);
        }

        @Override
        public LinkedVariables getLinked1Variables() {
            return linked1Variables.getOrDefault(LinkedVariables.DELAYED_EMPTY);
        }

        @Override
        public Boolean isTransparentType() {
            return isOfTransparentType.getOrDefaultNull();
        }

        @Override
        public Analysis build() {
            return new FieldAnalysisImpl(fieldInfo,
                    isOfTransparentType.getOrDefault(false),
                    linkedVariables.getOrDefault(LinkedVariables.EMPTY),
                    linked1Variables.getOrDefault(LinkedVariables.EMPTY),
                    getEffectivelyFinalValue(),
                    getInitialValue(),
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap());
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
            int effectivelyFinal = getProperty(VariableProperty.FINAL);
            int ownerImmutable = typeAnalysisOfOwner.getProperty(VariableProperty.IMMUTABLE);
            int modified = getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD);

            // @Final(after=), @Final, @Variable
            if (effectivelyFinal == Level.FALSE && MultiLevel.isEventuallyE1Immutable(ownerImmutable)) {
                String labels = typeAnalysisOfOwner.markLabel();
                annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal.copyWith(primitives, "after", labels), true);
            } else {
                if (effectivelyFinal == Level.TRUE && !isExplicitlyFinal) {
                    annotations.put(e2ImmuAnnotationExpressions.effectivelyFinal, true);
                }
                if (effectivelyFinal == Level.FALSE) {
                    annotations.put(e2ImmuAnnotationExpressions.variableField, true);
                }
            }

            // all other annotations cannot be added to primitives
            if (Primitives.isPrimitiveExcludingVoid(type)) return;

            // @NotModified(after=), @NotModified, @Modified
            if (modified == Level.TRUE && MultiLevel.isEventuallyE2Immutable(ownerImmutable)) {
                String labels = typeAnalysisOfOwner.markLabel();
                annotations.put(e2ImmuAnnotationExpressions.notModified.copyWith(primitives, "after", labels), true);
            } else {
                AnnotationExpression ae = modified == Level.FALSE ? e2ImmuAnnotationExpressions.notModified :
                        e2ImmuAnnotationExpressions.modified;
                annotations.put(ae, true);
            }

            // @NotNull
            doNotNull(e2ImmuAnnotationExpressions, getProperty(VariableProperty.EXTERNAL_NOT_NULL));

            // dynamic type annotations: @E1Immutable, @E1Container, @E2Immutable, @E2Container
            int typeImmutable = typeImmutable();
            int fieldImmutable = getProperty(VariableProperty.EXTERNAL_IMMUTABLE);
            if (MultiLevel.isBetterImmutable(fieldImmutable, typeImmutable)) {
                doImmutableContainer(e2ImmuAnnotationExpressions, fieldImmutable, true);
            }
        }

        private int typeImmutable() {
            return fieldInfo.owner == bestType || bestType == null ? MultiLevel.FALSE :
                    analysisProvider.getTypeAnalysis(bestType).getProperty(VariableProperty.IMMUTABLE);
        }

        public boolean isDeclaredFunctionalInterface() {
            return false; // TODO
        }

        public void setValues(List<ValueAndPropertyProxy> values, boolean delayed) {
            if (delayed) {
                this.values.setVariable(values);
            } else {
                this.values.setFinal(values);
            }
        }

        public List<ValueAndPropertyProxy> getValues() {
            return values.get();
        }

        public boolean valuesIsNotSet() {
            return !values.isFinal();
        }

        public String sortedValuesString() {
            return values.get().stream().map(p -> p.getValue().toString()).sorted().collect(Collectors.joining(","));
        }
    }
}
