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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;

import java.util.Map;

public interface FieldAnalysis extends Analysis {

    /*
     if final, equal to getEffectivelyFinalValue
     if variable, set when the value properties are present
     otherwise, delayed
     */
    @NotNull
    Expression getValue(); // final, or variable (in terms of an instance); null if not determined

    @NotNull
    CausesOfDelay valuesDelayed();

    // end product of the dependency analysis of linkage between the variables in a method
    // if A links to B, and A is modified, then B must be too.
    // In other words, if A->B, then B cannot be @NotModified unless A is too

    // here, the key of the map are fields; the local variables and parameters are stored in method analysis
    // the values are either other fields (in which case these other fields are not linked to parameters)
    // or parameters
    @NotNull
    LinkedVariables getLinkedVariables();

    @NotNull
    FieldInfo getFieldInfo();

    ParameterizedType concreteTypeNullWhenDelayed();

    default DV getFieldProperty(AnalysisProvider analysisProvider,
                                FieldInfo fieldInfo,
                                TypeInfo bestType,
                                Property property) {
        DV propertyFromType = ImplicitProperties.fromType(fieldInfo.type, property, false);
        if (propertyFromType != DV.MIN_INT_DV) return propertyFromType;

        switch (property) {
            case IMMUTABLE:
                DV fieldImmutable = getPropertyFromMapDelayWhenAbsent(property);
                if (fieldImmutable.isDelayed() && !fieldInfo.owner.shallowAnalysis()) {
                    return fieldImmutable;
                }
                DV typeImmutable = fieldInfo.owner == bestType || bestType == null ? MultiLevel.MUTABLE_DV :
                        analysisProvider.getTypeAnalysis(bestType).getProperty(Property.IMMUTABLE);
                // ignore delay OK because we're in shallow analysis
                return typeImmutable.maxIgnoreDelay(fieldImmutable);

            case BEFORE_MARK:
            case CONSTANT:
            case CONTAINER_RESTRICTION:
            case CONTAINER:
            case EXTERNAL_IMMUTABLE:
            case PARTIAL_EXTERNAL_IMMUTABLE:
            case EXTERNAL_NOT_NULL:
            case FINAL:
            case IDENTITY:
            case EXTERNAL_IGNORE_MODIFICATIONS:
            case INDEPENDENT:
            case MODIFIED_OUTSIDE_METHOD:
            case MODIFIED_VARIABLE:
                break;

            default:
                throw new PropertyException(Analyser.AnalyserIdentification.FIELD, property);
        }
        if (fieldInfo.owner.shallowAnalysis()) {
            return getPropertyFromMapNeverDelay(property);
        }
        return getPropertyFromMapDelayWhenAbsent(property);
    }

    Expression getInitializerValue();

    default Expression getValueForStatementAnalyser(TypeInfo primaryType, FieldReference fieldReference, int statementTime) {
        Expression value = getValue();
        // IMPORTANT: do not return Instance object here (i.e. do not add "|| value.isInstanceOf(Instance.class)")
        // because the instance does not have the eventual information that the field analyser holds.
        if (value.isConstant()) return value;
        if (value.isDelayed()) {
            if (fieldReference.scopeIsThis()) return value;
            return DelayedVariableExpression.forField(fieldReference, statementTime, value.causesOfDelay());
        }

        Properties properties = getValueProperties();
        CausesOfDelay delay = properties.delays();

        if (delay.isDelayed()) {
            return DelayedVariableExpression.forDelayedValueProperties(fieldReference, statementTime, properties, delay);
        }
        ParameterizedType mostSpecific;
        if (value.returnType().typeInfo != null && value.returnType().typeInfo.equals(fieldReference.fieldInfo.type.typeInfo)) {
            // same typeInfo, but maybe different type parameters; see e.g. NotNull_AAPI_3
            mostSpecific = fieldReference.parameterizedType;
        } else {
            // instance type List<...> in fieldReference vs instance type ArrayList<...> in value; see e.g. Basics_20
            mostSpecific = fieldReference.parameterizedType.mostSpecific(InspectionProvider.DEFAULT,
                    primaryType, value.returnType());
        }
        Instance instance = Instance.forField(getFieldInfo(), mostSpecific, properties);
        if (value instanceof ConstructorCall) {
            return PropertyWrapper.addState(instance, value);
        }
        return instance;
    }

    default Properties getValueProperties() {
        return Properties.of(Map.of(
                Property.NOT_NULL_EXPRESSION, getProperty(Property.EXTERNAL_NOT_NULL),
                Property.IMMUTABLE, getProperty(Property.EXTERNAL_IMMUTABLE),
                Property.CONTAINER, getProperty(Property.CONTAINER),
                Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV,
                Property.IGNORE_MODIFICATIONS, getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS),
                Property.IDENTITY, Property.IDENTITY.falseDv));
    }
}
