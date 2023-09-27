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

package org.e2immu.analyser.analysis.range;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.*;

import java.util.Arrays;
import java.util.List;

public record ConstantRange(ArrayInitializer initializer, VariableExpression variableExpression) implements Range {

    @Override
    public Expression conditions(EvaluationContext evaluationContext) {
        TypeInfo typeInfo = initializer.returnType().typeInfo;
        assert typeInfo != null : "Cannot find typeInfo in " + initializer;
        MethodInfo equalsMethodInfo = typeInfo.findUniqueMethod("equals", 1);
        assert equalsMethodInfo != null : "Cannot find equals method in type " + typeInfo;
        return Or.or(EvaluationResultImpl.from(evaluationContext), Arrays.stream(initializer.multiExpression.expressions())
                .map(e -> equals(equalsMethodInfo, variableExpression, e)).toList());
    }

    private Expression equals(MethodInfo equalsMethodInfo, VariableExpression variableExpression, Expression e) {
        return new MethodCall(Identifier.generate("equals in constant range"), variableExpression, equalsMethodInfo, List.of(e));
    }

    @Override
    public Expression exitState(EvaluationContext evaluationContext) {
        return new BooleanConstant(evaluationContext.getPrimitives(), true);
    }

    @Override
    public int loopCount() {
        return initializer.multiExpression.size();
    }
}
