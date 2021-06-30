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

package org.e2immu.analyser.analyser.util;

// assignment in if and else block

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.ReturnVariable;

import java.util.List;

/*
Different situations but they need to be dealt with in more or less the same way.
Each time we have two triples of (value, state on assignment, assignment id): (s1, v1, a1), (s2, v2, a2)

Using a1, a2 we can compute if the assignment of 1 comes either before or at the same time as 2.

If 1 comes before 2, then 1 already is the summary of all previous assignments to the variable: there is
no third to look at.
If 1 is at the same time as 2, we are in an if(s) {1} else {2} situation, with an assignment in both cases.
There are no other situations!

The state on assignment reflects both situations. In the latter, we expect to find s and !s in the state,
and potentially already in the value as well. In the former, the state of 1 should be contained in the state of 2.



 */
public record MergeHelper(EvaluationContext evaluationContext, VariableInfo vi) {

    /*
    NON-RETURN variables

    x = 1;
    statement { --> implies condition b (synchronized(this) -> true, if(b) -> b)
      if(a) return; // some interrupts
      x = 3; --> state is !a,
      if(c) return;
      x = 5; --> end state is !a && !c
    }
    result is simply b?3:1, in other words, we do not care about interrupts, nor about the state
    at the moment of assignment. The simple truth is that we cannot get to this point (the end of the statement)
    if there is an interrupt.

    x = 1;
    while(true) {
       if(a) break;
       x = 2;
       if(b) break;
       x = 3;
       ... some loop stuff ...
    }
    now it depends if a and b are dependent on the loop modification or not.
    if not, then the interrupts act as a return variable, and state becomes important.
    if they are dependent on modifications, replacement of the loop variable by some anonymous instance
    implies that we cannot compute a decent value for x anyway, so the state won't matter.

    RETURN VARIABLE
    the return variable starts with an unknown return value in each block!
    Only merge accumulates; assignment plays a role.

    x = 3; --> ret var = <unknown return value>
    if(c) {
      return x;  --> ret var = 3
    } --> ret var = c?3:<unknown return value>


    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) return zz;
      --> state is !a, ret var = a?zz:<unknown>
      return x;  --> ret var = a?zz:3 [here the state plays a role, in the level 3 statement analyser/assignment!]
    } --> ret var = c?a?zz:3:<unknown return value>

    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) return zz;
      --> state is !a, ret var = a?zz:<unknown>
      if(b) {
        return x;
      } else {
        return y;
      } --> ret var = a?zz:b?x:y
    } --> ret v

    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) break;
      --> state is !a, ret var is still unknown
      if(b) {
        return x; --> return x
      } else {
        return y; --> return y
      } --> ret var = a?<unknown>:b?x:y
    } --> ret v
    */

    public Expression one(VariableInfo vi1, Expression stateOfParent, Expression condition) {
        if (condition.isBoolValueTrue()) {

            // this if-statement replays the code in level 3 return statement:
            // it is identical to do if(x) return a; return b or if(x) return a; if(!x) return b;
            if (vi.variable() instanceof ReturnVariable) {
                if (stateOfParent.isBoolValueTrue()) return vi1.getValue();
                if (vi.variable().parameterizedType().equals(evaluationContext.getPrimitives().booleanParameterizedType)) {
                    return and(stateOfParent, vi1.getValue());
                }
                return inlineConditional(stateOfParent, vi1.getValue(), vi.getValue());
            }
            return vi1.getValue(); // so we by-pass the "safe" in inlineConditional
        }
        return inlineConditional(condition, vi1.getValue(), vi.getValue());
    }

    public Expression twoComplementary(VariableInfo vi1, Expression stateOfParent, Expression firstCondition, VariableInfo vi2) {
        Expression two;
        if (firstCondition.isBoolValueTrue()) two = vi1.getValue(); // to bypass the error check on "safe"
        else if (firstCondition.isBoolValueFalse()) two = vi2.getValue();
        else two = inlineConditional(firstCondition, vi1.getValue(), vi2.getValue());

        if (vi.variable() instanceof ReturnVariable) {
            if (stateOfParent.isBoolValueTrue()) return two;
            if (stateOfParent.isBoolValueFalse()) throw new UnsupportedOperationException(); // unreachable statement
            return inlineConditional(stateOfParent, two, vi.getValue());
        }
        return two;
    }

    private Expression and(Expression... expressions) {
        return new And(evaluationContext.getPrimitives()).append(evaluationContext, expressions);
    }

    private Expression inlineConditional(Expression condition, Expression ifTrue, Expression ifFalse) {
        return safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext,
                condition, ifTrue, ifFalse));
    }

    private Expression safe(EvaluationResult result) {
        if (result.getMessageStream().anyMatch(m -> true)) {
            // something gone wrong, retreat
            return noConclusion();
        }
        return result.value();
    }

    public Expression noConclusion() {
        return NewObject.genericMergeResult(evaluationContext.newObjectIdentifier() + "-" +
                vi.variable().fullyQualifiedName(), evaluationContext.getPrimitives(), vi);
    }

    public Expression noConclusion(int notNull) {
        return NewObject.genericMergeResult(evaluationContext.newObjectIdentifier() + "-" +
                vi.variable().fullyQualifiedName(), evaluationContext.getPrimitives(), vi, notNull);
    }

    public Expression delayedConclusion() {
        return DelayedExpression.forMerge(vi.variable().parameterizedType(), List.copyOf(vi.getLinkedVariables().variables()));
    }
}
