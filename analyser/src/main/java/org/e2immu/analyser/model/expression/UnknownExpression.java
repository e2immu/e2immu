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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.List;
import java.util.Objects;

/**
 * Contains "some value".
 */
public class UnknownExpression extends BaseExpression implements Expression {

    public static final String RETURN_VALUE = "return value";
    public static final String VARIABLE_VALUE = "variable value";
    public static final String NOT_YET_ASSIGNED = "not yet assigned";
    public static final String NO_RETURN_VALUE = "no return value";
    public static final String ARRAY_LENGTH = "array length";

    private final ParameterizedType parameterizedType;
    private final String msg;

    private UnknownExpression(Identifier identifier, ParameterizedType parameterizedType, String msg) {
        super(identifier);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.msg = Objects.requireNonNull(msg);
    }

    public static UnknownExpression forReturnVariable(Identifier identifier, ParameterizedType parameterizedType) {
        return new UnknownExpression(identifier, parameterizedType, RETURN_VALUE);
    }

    public static UnknownExpression forNotYetAssigned(Identifier identifier, ParameterizedType parameterizedType) {
        return new UnknownExpression(identifier, parameterizedType, NOT_YET_ASSIGNED);
    }

    public static UnknownExpression forArrayLength(Identifier identifier, ParameterizedType parameterizedType) {
        return new UnknownExpression(identifier, parameterizedType, ARRAY_LENGTH);
    }

    public static UnknownExpression forNoReturnValue(Identifier identifier, ParameterizedType parameterizedType) {
        return new UnknownExpression(identifier, parameterizedType, NO_RETURN_VALUE);
    }

    public static Expression forVariableValue(Identifier identifier, ParameterizedType parameterizedType) {
        return new UnknownExpression(identifier, parameterizedType, VARIABLE_VALUE);
    }

    public static Expression forHardcodedMethodReturnValue(Identifier identifier, ParameterizedType parameterizedType,
                                                           String customMessage) {
        return new UnknownExpression(identifier, parameterizedType, customMessage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnknownExpression that = (UnknownExpression) o;
        return msg.equals(that.msg) && identifier.equals(that.identifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg, identifier);
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        if (parameterizedType.isPrimitiveExcludingVoid()) {
            return primitiveGetProperty(property);
        }
        TypeAnalysis typeAnalysis = parameterizedType.typeInfo == null ? null
                : evaluationContext.getAnalyserContext().getTypeAnalysis(parameterizedType.typeInfo);
        switch (property) {
            case IMMUTABLE:
                return typeAnalysis == null ? MultiLevel.NOT_INVOLVED_DV : typeAnalysis.getProperty(Property.IMMUTABLE);
            case INDEPENDENT:
                return typeAnalysis == null ? MultiLevel.NOT_INVOLVED_DV : typeAnalysis.getProperty(Property.INDEPENDENT);
            case CONTAINER:
                return typeAnalysis == null ? MultiLevel.NOT_INVOLVED_DV : typeAnalysis.getProperty(Property.CONTAINER);
            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE_DV;
            case CONTEXT_MODIFIED:
            case IDENTITY:
                return DV.FALSE_DV;
        }
        throw new UnsupportedOperationException("No info about " + property + " for primitive");
    }

    public static DV primitiveGetProperty(Property property) {
        switch (property) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case CONTAINER:
                return MultiLevel.CONTAINER_DV;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case CONTEXT_MODIFIED:
            case IDENTITY:
                return DV.FALSE_DV;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT_DV;
        }
        throw new UnsupportedOperationException("No info about " + property + " for primitive");
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return translationMap.translateExpression(this);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("<" + msg + ">", "<" + msg + ":" + parameterizedType.output(qualification) + ">"));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder().setExpression(this).build();
    }

    @Override
    public int order() {
        return 0;
    }

    public String msg() {
        return msg;
    }

    @Override
    public boolean isComputeProperties() {
        return false;
    }
}
