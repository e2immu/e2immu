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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // at the moment, all calls have unboundIsMutable == false
    default DV getProperty(ParameterizedType parameterizedType, Property property, boolean unboundIsMutable) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType != null) {
            TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
            return typeAnalysis == null ? property.falseDv : typeAnalysis.getProperty(property);
        }
        if (!unboundIsMutable) {
            if (property == Property.IMMUTABLE) return MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV;
            if (property == Property.INDEPENDENT) return MultiLevel.INDEPENDENT_1_DV;
        }
        return property.falseDv;
    }

    default DV isTransparentOrAtLeastEventuallyE2Immutable(ParameterizedType parameterizedType, TypeInfo typeBeingAnalysed) {
        if (parameterizedType.arrays > 0) return DV.FALSE_DV;
        DV atLeastEventuallyE2Immutable = isAtLeastEventuallyE2Immutable(parameterizedType);
        if (atLeastEventuallyE2Immutable.valueIsTrue()) return DV.TRUE_DV;
        DV transparent = isTransparent(parameterizedType, typeBeingAnalysed);
        if (transparent.valueIsTrue()) return DV.TRUE_DV;
        if (transparent.isDelayed() || atLeastEventuallyE2Immutable.isDelayed()) {
            return transparent.min(atLeastEventuallyE2Immutable);
        }
        return DV.FALSE_DV;
    }

    private DV isAtLeastEventuallyE2Immutable(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) return DV.FALSE_DV;
        DV immutable = getTypeAnalysis(bestType).getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        return DV.fromBoolDv(MultiLevel.isAtLeastEventuallyE2Immutable(immutable));
    }

    default DV isTransparent(ParameterizedType parameterizedType, TypeInfo typeBeingAnalysed) {
        TypeAnalysis typeAnalysis = getTypeAnalysis(typeBeingAnalysed);
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes();
        if (hiddenContentTypes == null) return DelayFactory.createDelay(typeBeingAnalysed.newLocation(),
                CauseOfDelay.Cause.HIDDEN_CONTENT);
        return DV.fromBoolDv(hiddenContentTypes.contains(parameterizedType));
    }

    default DV cannotBeModifiedInThisClass(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) return DV.FALSE_DV;
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) return DV.FALSE_DV;
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (immutable.isDelayed()) return immutable;
        boolean cannotBeModified = MultiLevel.isAtLeastEffectivelyE2Immutable(immutable);
        return DV.fromBoolDv(cannotBeModified);
    }


    default DV defaultIndependent(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            // because the "fields" of the array, i.e. the cells, can be mutated
            return MultiLevel.DEPENDENT_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return MultiLevel.INDEPENDENT_1_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV baseValue = typeAnalysis.getProperty(Property.INDEPENDENT);
        if (baseValue.isDelayed()) return baseValue;
        if (MultiLevel.isAtLeastE2Immutable(baseValue) && !parameterizedType.parameters.isEmpty()) {
            DV doSum = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
            if (doSum.valueIsTrue()) {
                DV paramValue = parameterizedType.parameters.stream()
                        .map(this::defaultIndependent)
                        .reduce(MultiLevel.INDEPENDENT_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return MultiLevel.sumImmutableLevels(baseValue, paramValue);
            }
            if (doSum.isDelayed()) {
                return doSum;
            }
        }
        return baseValue;
    }

    default DV immutableOfHiddenContent(ParameterizedType parameterizedType, boolean returnValueOfMethod) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            ParameterizedType withoutArrays = parameterizedType.copyWithoutArrays();
            return defaultImmutable(withoutArrays, returnValueOfMethod);
        }
        if (bestType == null) {
            return returnValueOfMethod ? MultiLevel.NOT_INVOLVED_DV : MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        SetOfTypes hiddenContentTypes = typeAnalysis.getTransparentTypes(parameterizedType);
        if (hiddenContentTypes == null) {
            return bestType.delay(CauseOfDelay.Cause.HIDDEN_CONTENT);
        }
        return hiddenContentTypes.types().stream()
                .map(pt -> defaultImmutable(pt, returnValueOfMethod))
                .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
    }

    default DV defaultImmutable(ParameterizedType parameterizedType, boolean unboundIsMutable) {
        return defaultImmutable(parameterizedType, unboundIsMutable, MultiLevel.NOT_INVOLVED_DV);
    }

    /*
    Why dynamic value? firstEntry() returns a Map.Entry, which formally is MUTABLE, but has E2IMMUTABLE assigned
    Once a type is E2IMMUTABLE, we have to look at the immutability of the hidden content, to potentially upgrade
    to a higher version. See e.g., E2Immutable_11,12
     */
    default DV defaultImmutable(ParameterizedType parameterizedType, boolean unboundIsMutable, DV dynamicValue) {
        assert dynamicValue.isDone();
        if (parameterizedType.arrays > 0) {
            return MultiLevel.EFFECTIVELY_E1IMMUTABLE_DV;
        }
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) {
            // unbound type parameter, null constant
            return dynamicValue.max(unboundIsMutable ? MultiLevel.NOT_INVOLVED_DV : MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV);
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        DV baseValue = typeAnalysis.getProperty(Property.IMMUTABLE);
        if (baseValue.isDelayed()) {
            return baseValue;
        }
        DV dynamicBaseValue = dynamicValue.max(baseValue);
        if (MultiLevel.isAtLeastE2Immutable(dynamicBaseValue) && !parameterizedType.parameters.isEmpty()) {
            DV doSum = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
            if (doSum.isDelayed()) {
                assert typeAnalysis.isNotContracted();
                return doSum;
            }
            if (doSum.valueIsTrue()) {
                DV paramValue = parameterizedType.parameters.stream()
                        .map(pt -> defaultImmutable(pt, true))
                        .map(v -> v.containsCauseOfDelay(CauseOfDelay.Cause.TYPE_ANALYSIS) ? MultiLevel.MUTABLE_DV : v)
                        .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return MultiLevel.sumImmutableLevels(dynamicBaseValue, paramValue);
            }
        }
        return dynamicBaseValue;
    }

    default DV defaultContainer(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            return MultiLevel.CONTAINER_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return MultiLevel.NOT_CONTAINER_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return typeAnalysisNotAvailable(bestType);
        }
        return typeAnalysis.getProperty(Property.CONTAINER);
    }

    static DV defaultNotNull(ParameterizedType parameterizedType) {
        return parameterizedType.isPrimitiveExcludingVoid() ? MultiLevel.EFFECTIVELY_NOT_NULL_DV : MultiLevel.NULLABLE_DV;
    }


    private static DV typeAnalysisNotAvailable(TypeInfo bestType) {
        return bestType.delay(CauseOfDelay.Cause.TYPE_ANALYSIS);
    }

    default DV defaultValueProperty(Property property, ParameterizedType formalType) {
        return switch (property) {
            case IMMUTABLE -> defaultImmutable(formalType, false);
            case INDEPENDENT -> defaultIndependent(formalType);
            case IDENTITY, IGNORE_MODIFICATIONS -> property.falseDv;
            case CONTAINER -> defaultContainer(formalType);
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

    default Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression) {
        return defaultValueProperties(parameterizedType, valueForNotNullExpression, false);
    }

    default Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression, boolean writable) {
        return EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Properties.collect(p -> p == Property.NOT_NULL_EXPRESSION ? valueForNotNullExpression :
                        defaultValueProperty(p, parameterizedType), writable));
    }
}
