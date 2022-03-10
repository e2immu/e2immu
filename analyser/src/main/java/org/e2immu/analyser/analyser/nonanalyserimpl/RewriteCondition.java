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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.LocalVariableReference;

public class RewriteCondition {
    private RewriteCondition() {
        // nothing here
    }

    /**
     * See Loops_15: a condition on a loop variable being equal to a parameter travels from the wider condition on
     * the loop variable to the parameter.
     *
     * @param condition     i==p
     * @param absoluteState i==p & i>=0 && i<= 10
     * @return p>=0 && p <=10
     */
    public static Expression rewriteConditionFromLoopVariableToParameter(EvaluationResult evaluationContext,
                                                                         Expression condition,
                                                                         Expression absoluteState) {
        IsVariableExpression veLhs;
        IsVariableExpression veRhs;
        if (condition instanceof Equals eq
                && !absoluteState.equals(condition)
                && (veLhs = eq.lhs.asInstanceOf(IsVariableExpression.class)) != null
                && (veRhs = eq.rhs.asInstanceOf(IsVariableExpression.class)) != null) {
            IsVariableExpression loopVariable;
            IsVariableExpression parameter;
            if (veLhs.variable() instanceof ParameterInfo && veRhs.variable() instanceof LocalVariableReference) {
                loopVariable = veRhs;
                parameter = veLhs;
            } else if (veLhs.variable() instanceof LocalVariableReference && veRhs.variable() instanceof ParameterInfo) {
                loopVariable = veLhs;
                parameter = veRhs;
            } else return condition;
            if (absoluteState instanceof And and) {
                // remove from absolute state any component without "i" TODO
                Expression componentsWithLvr = and.removePartsNotReferringTo(evaluationContext, loopVariable.variable());
                // replace, in absolute state, the "i==p" with true and in the rest, "i" with "p"
                TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();
                builder.put(condition, new BooleanConstant(evaluationContext.getPrimitives(), true));
                builder.put(loopVariable, parameter);
                Expression translated = componentsWithLvr.translate(evaluationContext.getAnalyserContext(), builder.build());
                // add the condition again, so that when we merge up, other conditions and states can collapse
                return And.and(evaluationContext, translated, condition);
            }
        }
        return condition;
    }

}
