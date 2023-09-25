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

import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.e2immu.analyser.model.MultiLevel.Level.IMMUTABLE_HC;

public interface AnalysisProvider {
    Logger LOGGER = LoggerFactory.getLogger(AnalysisProvider.class);

    @NotNull
    FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo);

    @NotNull
    ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo);

    TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo);

    @NotNull
    TypeAnalysis getTypeAnalysis(TypeInfo typeInfo);

    @NotNull
    MethodAnalysis getMethodAnalysis(MethodInfo methodInfo);

    default MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        throw new UnsupportedOperationException();
    }

    AnalysisProvider DEFAULT_PROVIDER = new AnalysisProvider() {

        @Override
        public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
            return fieldInfo.fieldAnalysis.get();
        }

        @Override
        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            return parameterInfo.parameterAnalysis.get();
        }

        @Override
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.get("Type analysis of " + typeInfo.fullyQualifiedName);
        }

        @Override
        public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.getOrDefaultNull();
        }

        @Override
        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            try {
                return methodInfo.methodAnalysis.get("Method analysis of " + methodInfo.fullyQualifiedName);
            } catch (RuntimeException re) {
                LOGGER.error("Caught exception trying to obtain default method analysis for " + methodInfo.fullyQualifiedName());
                throw re;
            }
        }
    };

    AnalysisProvider NULL_IF_NOT_SET = new AnalysisProvider() {
        @Override
        public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
            return fieldInfo.fieldAnalysis.getOrDefaultNull();
        }

        @Override
        public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
            return parameterInfo.parameterAnalysis.getOrDefaultNull();
        }

        @Override
        public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.getOrDefaultNull();
        }

        @Override
        public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
            return typeInfo.typeAnalysis.getOrDefaultNull();
        }

        @Override
        public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
            return methodInfo.methodAnalysis.getOrDefaultNull();
        }
    };

    // convenience method, but rather call defaultXXX immediately
    default DV getProperty(ParameterizedType parameterizedType, Property property) {
        return switch (property) {
            case IMMUTABLE -> typeImmutable(parameterizedType);
            case INDEPENDENT -> typeIndependent(parameterizedType);
            case CONTAINER -> typeContainer(parameterizedType);
            default -> property.falseDv;
        };
    }

    default DV cannotBeModifiedInThisClass(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) return DV.FALSE_DV;
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) return DV.FALSE_DV;
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        boolean cannotBeModified = MultiLevel.isAtLeastEffectivelyImmutableHC(immutable);
        return DV.fromBoolDv(cannotBeModified);
    }


    default DV typeIndependent(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            // because the "fields" of the array, i.e. the cells, can be mutated
            return MultiLevel.DEPENDENT_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return MultiLevel.INDEPENDENT_HC_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV baseValue = typeAnalysis.getProperty(Property.INDEPENDENT);
        if (baseValue.isDelayed()) return baseValue;
        if (MultiLevel.isAtLeastImmutableHC(baseValue) && !parameterizedType.parameters.isEmpty()) {
            DV useTypeParameters = typeAnalysis.immutableDeterminedByTypeParameters();
            if (useTypeParameters.valueIsTrue()) {
                DV paramValue = parameterizedType.parameters.stream()
                        .map(this::typeIndependent)
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return paramValue;
            }
            if (useTypeParameters.isDelayed()) {
                return useTypeParameters;
            }
        }
        return baseValue;
    }

    default DV typeImmutable(ParameterizedType parameterizedType) {
        return typeImmutable(parameterizedType, Map.of());
    }

    /*
    Why dynamic value? See MethodCall.dynamicImmutable().
    If the dynamic value is IMMUTABLE_HC and the computed value is MUTABLE, we still need to go through the
    immutableDeterminedByTypeParameters() code... therefore a simple remote 'max()' operation does not work.
     */

    default DV typeImmutable(ParameterizedType parameterizedType, Map<ParameterizedType, DV> dynamicValues) {
        if (parameterizedType.arrays > 0) {
            return MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV;
        }
        if (parameterizedType == ParameterizedType.NULL_CONSTANT) {
            return MultiLevel.NOT_INVOLVED_DV;
        }
        if (parameterizedType.isUnboundTypeParameter()) {
            return MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV;
        }
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV dynamicBaseValue;
        DV immutableOfCurrent = dynamicValues.get(parameterizedType);
        if (immutableOfCurrent != null) {
            dynamicBaseValue = immutableOfCurrent;
        } else {
            DV baseValue = typeAnalysis.getProperty(Property.IMMUTABLE);
            if (baseValue.isDelayed()) {
                return baseValue;
            }
            dynamicBaseValue = baseValue;
        }
        MultiLevel.Effective effective = MultiLevel.effective(dynamicBaseValue);
        if (MultiLevel.isAtLeastImmutableHC(dynamicBaseValue) && !parameterizedType.parameters.isEmpty()) {
            DV useTypeParameters = typeAnalysis.immutableDeterminedByTypeParameters();
            if (useTypeParameters.isDelayed()) {
                assert typeAnalysis.isNotContracted();
                return useTypeParameters;
            }
            if (useTypeParameters.valueIsTrue()) {
                DV paramValue = parameterizedType.parameters.stream()
                        .map(pt -> typeImmutable(pt, dynamicValues))
                        .map(v -> v.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS) ? MultiLevel.MUTABLE_DV : v)
                        .reduce(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                int paramLevel = MultiLevel.level(paramValue);
                // important not to lose the eventual characteristic!
                if (effective == MultiLevel.Effective.EFFECTIVE && paramLevel == MultiLevel.Level.MUTABLE.level) {
                    return MultiLevel.MUTABLE_DV; // instead of what would be FINAL_FIELDS
                }
                return MultiLevel.composeImmutable(effective, paramLevel);
            }
        }
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(dynamicBaseValue) && parameterizedType.isTypeParameter()) {
            return MultiLevel.composeImmutable(effective, IMMUTABLE_HC.level);
        }
        return dynamicBaseValue;
    }

    default DV typeContainer(ParameterizedType parameterizedType) {
        if (parameterizedType.arrays > 0) {
            return MultiLevel.CONTAINER_DV;
        }
        if (parameterizedType == ParameterizedType.NULL_CONSTANT) {
            return MultiLevel.NOT_CONTAINER_DV;
        }
        if (parameterizedType.isUnboundTypeParameter()) {
            return MultiLevel.CONTAINER_DV;
        }
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        return typeAnalysis.getProperty(Property.CONTAINER);
    }

    default DV safeContainer(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            return MultiLevel.CONTAINER_DV;
        }
        if (parameterizedType == ParameterizedType.NULL_CONSTANT) {
            return MultiLevel.NOT_CONTAINER_DV;
        }
        if (parameterizedType.isUnboundTypeParameter()) {
            return MultiLevel.CONTAINER_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return null;
        }
        DV dv = typeAnalysis.getProperty(Property.CONTAINER);
        if (dv.isDelayed()) {
            return dv;
        }
        if (bestType.isFinal(InspectionProvider.DEFAULT) || bestType.isInterface() && dv.equals(MultiLevel.CONTAINER_DV)) {
            return dv;
        }
        return null;
    }


    static DV defaultNotNull(ParameterizedType parameterizedType) {
        return parameterizedType.isPrimitiveExcludingVoid() ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV;
    }


    private static DV typeAnalysisNotAvailable(TypeInfo bestType) {
        return bestType.delay(CauseOfDelay.Cause.TYPE_ANALYSIS);
    }

    default DV defaultValueProperty(Property property, ParameterizedType formalType) {
        return switch (property) {
            case IMMUTABLE -> typeImmutable(formalType);
            case INDEPENDENT -> typeIndependent(formalType);
            case IDENTITY, IGNORE_MODIFICATIONS -> property.falseDv;
            case CONTAINER -> typeContainer(formalType);
            case NOT_NULL_EXPRESSION -> defaultNotNull(formalType);
            default -> throw new UnsupportedOperationException("property: " + property);
        };
    }

    default Properties defaultValueProperties(ParameterizedType parameterizedType) {
        return defaultValueProperties(parameterizedType, false);
    }

    default Properties defaultValueProperties(ParameterizedType parameterizedType, boolean writable) {
        return EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Properties.collect(p -> defaultValueProperty(p, parameterizedType), writable));
    }

    default Properties defaultValueProperties(ParameterizedType parameterizedType,
                                              DV valueForNotNullExpression) {
        return defaultValueProperties(parameterizedType, valueForNotNullExpression, false);
    }

    default Properties defaultValueProperties(ParameterizedType parameterizedType,
                                              DV valueForNotNullExpression,
                                              boolean writable) {
        return EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Properties.collect(p -> p == Property.NOT_NULL_EXPRESSION ? valueForNotNullExpression :
                        defaultValueProperty(p, parameterizedType), writable));
    }
}
