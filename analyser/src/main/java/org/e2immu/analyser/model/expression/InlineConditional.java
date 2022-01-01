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
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * a ? b : c
 */
public class InlineConditional extends BaseExpression implements Expression {
    public final Expression condition;
    public final Expression ifTrue;
    public final Expression ifFalse;
    public final InspectionProvider inspectionProvider;

    public InlineConditional(Identifier identifier,
                             InspectionProvider inspectionProvider,
                             Expression condition,
                             Expression ifTrue,
                             Expression ifFalse) {
        super(identifier);
        this.condition = Objects.requireNonNull(condition);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
        this.inspectionProvider = inspectionProvider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlineConditional that = (InlineConditional) o;
        return condition.equals(that.condition) &&
                ifTrue.equals(that.ifTrue) &&
                ifFalse.equals(that.ifFalse);
    }

    @Override
    public int hashCode() {
        return Objects.hash(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InlineConditional(identifier, inspectionProvider,
                translationMap.translateExpression(condition),
                translationMap.translateExpression(ifTrue),
                translationMap.translateExpression(ifFalse));
    }


    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reCondition = condition.reEvaluate(evaluationContext, translation);
        EvaluationResult reTrue = ifTrue.reEvaluate(evaluationContext, translation);
        EvaluationResult reFalse = ifFalse.reEvaluate(evaluationContext, translation);
        EvaluationResult.Builder builder = new EvaluationResult.Builder().compose(reCondition, reTrue, reFalse);
        EvaluationResult res = EvaluateInlineConditional.conditionalValueConditionResolved(
                evaluationContext, reCondition.value(), reTrue.value(), reFalse.value());
        return builder.setExpression(res.value()).build();
    }


    @Override
    public int internalCompareTo(Expression v) {
        return condition.internalCompareTo(v);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), condition))
                .add(Symbol.QUESTION_MARK)
                .add(outputInParenthesis(qualification, precedence(), ifTrue))
                .add(Symbol.COLON)
                .add(outputInParenthesis(qualification, precedence(), ifFalse));
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION) {
            if (returnType().isPrimitiveExcludingVoid()) {
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            }
            Expression c = condition;
            EvaluationContext child = evaluationContext.child(c);
            DV nneIfTrue = child.getProperty(ifTrue, Property.NOT_NULL_EXPRESSION, true, false);
            if (nneIfTrue.le(MultiLevel.NULLABLE_DV)) {
                return nneIfTrue;
            }
            Expression notC = Negation.negate(evaluationContext, c);
            EvaluationContext notChild = evaluationContext.child(notC);
            DV nneIfFalse = notChild.getProperty(ifFalse, Property.NOT_NULL_EXPRESSION, true, false);
            return nneIfFalse.min(nneIfTrue);
        }
        if (EvaluationContext.VALUE_PROPERTIES.contains(property)) {
            return new MultiExpression(ifTrue, ifFalse).getProperty(evaluationContext, property, duringEvaluation);
        }
        return new MultiExpression(condition, ifTrue, ifFalse).getProperty(evaluationContext, property, duringEvaluation);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        LinkedVariables linkedVariablesTrue = ifTrue.linkedVariables(evaluationContext);
        LinkedVariables linkedVariablesFalse = ifFalse.linkedVariables(evaluationContext);
        return linkedVariablesTrue.merge(linkedVariablesFalse);
    }

    @Override
    public int order() {
        return condition.order();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            condition.visit(predicate);
            ifTrue.visit(predicate);
            ifFalse.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables() {
        return ListUtil.immutableConcat(condition.variables(), ifTrue.variables(), ifFalse.variables());
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult conditionResult = condition.evaluate(evaluationContext, forwardEvaluationInfo.copyNotNull());
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(conditionResult);

        boolean resultIsBoolean = returnType().equals(evaluationContext.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        EvaluationContext copyForThen = resultIsBoolean ? evaluationContext :
                evaluationContext.child(conditionResult.value());
        EvaluationResult ifTrueResult = ifTrue.evaluate(copyForThen, forwardEvaluationInfo);
        builder.compose(ifTrueResult);

        EvaluationContext copyForElse = resultIsBoolean ? evaluationContext :
                evaluationContext.child(Negation.negate(evaluationContext, conditionResult.value()));
        EvaluationResult ifFalseResult = ifFalse.evaluate(copyForElse, forwardEvaluationInfo);
        builder.compose(ifFalseResult);

        Expression c = conditionResult.value();
        Expression t = ifTrueResult.value();
        Expression f = ifFalseResult.value();

        if (c.isUnknown() || t.isUnknown() || f.isUnknown()) {
            throw new UnsupportedOperationException();
        }

        // TODO ObjectFlow
        EvaluationResult cv = EvaluateInlineConditional.conditionalValueCurrentState(evaluationContext,
                c, t, f);
        return builder.compose(cv).build();
    }

    public Expression optimise(EvaluationContext evaluationContext) {
        return optimise(evaluationContext, false);
    }

    private Expression optimise(EvaluationContext evaluationContext, boolean useState) {
        boolean resultIsBoolean = returnType().equals(evaluationContext.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        EvaluationContext copyForThen = resultIsBoolean ? evaluationContext : evaluationContext.child(condition);
        Expression t = ifTrue instanceof InlineConditional inlineTrue ? inlineTrue.optimise(copyForThen, true) : ifTrue;
        EvaluationContext copyForElse = resultIsBoolean ? evaluationContext : evaluationContext.child(Negation.negate(evaluationContext, condition));
        Expression f = ifFalse instanceof InlineConditional inlineFalse ? inlineFalse.optimise(copyForElse, true) : ifFalse;

        if (useState) {
            return EvaluateInlineConditional.conditionalValueCurrentState(evaluationContext, condition, t, f).getExpression();
        }
        return EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, condition, t, f).getExpression();

    }

    @Override
    public ParameterizedType returnType() {
        if (ifTrue.isNull() && ifFalse.isNull()) throw new UnsupportedOperationException();
        if (ifTrue.isNull()) {
            return box(ifFalse.returnType());
        }
        if (ifFalse.isNull()) return box(ifTrue.returnType());
        return ifTrue.returnType().commonType(inspectionProvider, ifFalse.returnType());
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        if (ifTrue.isNull() && ifFalse.isNull()) throw new UnsupportedOperationException();
        if (ifTrue.isNull()) {
            return ifFalse.erasureTypes(typeContext);
        }
        if (ifFalse.isNull()) return ifTrue.erasureTypes(typeContext);
        return SetUtil.immutableUnion(ifTrue.erasureTypes(typeContext), ifFalse.erasureTypes(typeContext));
    }

    private ParameterizedType box(ParameterizedType returnType) {
        if (returnType.isPrimitiveExcludingVoid()) {
            return returnType.toBoxed(inspectionProvider.getPrimitives()).asParameterizedType(inspectionProvider);
        }
        return returnType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }


    @Override
    public CausesOfDelay causesOfDelay() {
        return condition.causesOfDelay().merge(ifTrue.causesOfDelay()).merge(ifFalse.causesOfDelay());
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression removeAllReturnValueParts() {
        boolean removeTrue = ifTrue.isReturnValue();
        boolean removeFalse = ifFalse.isReturnValue();
        if (removeTrue && removeFalse) return ifTrue; // nothing we can do
        if (removeTrue) return ifFalse;
        if (removeFalse) return ifTrue;
        return this;
    }
}
