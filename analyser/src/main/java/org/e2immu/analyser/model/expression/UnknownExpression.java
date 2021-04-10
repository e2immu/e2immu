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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;

import java.util.Objects;

/**
 * Contains "some value".
 */
public record UnknownExpression(ParameterizedType parameterizedType, String msg) implements Expression {

    public static final String RETURN_VALUE = "return value";
    public static final String VARIABLE = "variable value";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnknownExpression that = (UnknownExpression) o;
        return msg.equals(that.msg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        if(Primitives.isPrimitiveExcludingVoid(parameterizedType)) {
            return primitiveGetProperty(variableProperty);
        }
        TypeAnalysis typeAnalysis = evaluationContext.getAnalyserContext().getTypeAnalysis(parameterizedType.typeInfo);
        switch (variableProperty) {
            case IMMUTABLE:
                return typeAnalysis.getProperty(VariableProperty.IMMUTABLE);
            case CONTAINER:
                return typeAnalysis.getProperty(VariableProperty.CONTAINER);
            case NOT_NULL_EXPRESSION:
                return MultiLevel.NULLABLE;
            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
    }

    public static int primitiveGetProperty(VariableProperty variableProperty) {
        switch (variableProperty) {
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case CONTAINER:
                return Level.TRUE;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case IDENTITY:
            case NOT_MODIFIED_1:
                return Level.FALSE;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for primitive");
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
}
