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
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;

/**
 * a ? b : c
 */
public class InlineConditional extends BaseExpression implements Expression {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlineConditional.class);

    public final Expression condition;
    public final Expression ifTrue;
    public final Expression ifFalse;
    public final InspectionProvider inspectionProvider;
    public static final int COMPLEXITY = 10;
    // cached
    private final CausesOfDelay causesOfDelay;

    public InlineConditional(InspectionProvider inspectionProvider,
                             Expression condition,
                             Expression ifTrue,
                             Expression ifFalse) {
        this(Identifier.joined("inline", List.of(condition.getIdentifier(), ifTrue.getIdentifier(), ifFalse.getIdentifier())),
                inspectionProvider, condition, ifTrue, ifFalse);
    }

    public InlineConditional(Identifier identifier,
                             InspectionProvider inspectionProvider,
                             Expression condition,
                             Expression ifTrue,
                             Expression ifFalse) {
        super(identifier, COMPLEXITY + condition.getComplexity() + ifFalse.getComplexity() + ifTrue.getComplexity());
        this.condition = Objects.requireNonNull(condition);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
        this.inspectionProvider = inspectionProvider;
        this.causesOfDelay = condition.causesOfDelay().merge(ifTrue.causesOfDelay()).merge(ifFalse.causesOfDelay());
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression tc = condition.translate(inspectionProvider, translationMap);
        Expression tt = ifTrue.translate(inspectionProvider, translationMap);
        Expression tf = ifFalse.translate(inspectionProvider, translationMap);
        if (tc == condition && tt == ifTrue && tf == ifFalse) return this;
        return new InlineConditional(identifier, this.inspectionProvider, tc, tt, tf);
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
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        // there is little we can say with certainty until we know that the condition is not trivial, and
        // one of ifTrue, ifFalse is chosen. See Precondition_3
        if (condition.isDelayed()) return condition.causesOfDelay();

        // this code is not in a return switch(property) { ... } expression because JavaParser 3.24.1-SNAPSHOT crashes  while parsing
        if (property == NOT_NULL_EXPRESSION) {
            Set<Variable> conditionVariables = condition.variables(true).stream().collect(Collectors.toUnmodifiableSet());
            EvaluationResult child = context.child(condition, conditionVariables);
            DV nneIfTrue = child.evaluationContext().getProperty(ifTrue, NOT_NULL_EXPRESSION, duringEvaluation, false);
            if (nneIfTrue.le(MultiLevel.NULLABLE_DV)) {
                return nneIfTrue;
            }
            Expression notC = Negation.negate(context, condition);
            EvaluationResult notChild = context.child(notC, conditionVariables);
            DV nneIfFalse = notChild.evaluationContext().getProperty(ifFalse, NOT_NULL_EXPRESSION, duringEvaluation, false);
            return nneIfFalse.min(nneIfTrue);
        }
        if (property == EXTERNAL_NOT_NULL) {
            throw new UnsupportedOperationException();
        }
        if (property == IDENTITY || property == IGNORE_MODIFICATIONS) {
            return new MultiExpression(ifTrue, ifFalse).getProperty(context, property, duringEvaluation);
        }
        if (property == IMMUTABLE || property == INDEPENDENT || property == CONTAINER) {
            if (ifTrue.isInstanceOf(NullConstant.class)) {
                return context.evaluationContext().getProperty(ifFalse, property, duringEvaluation, false);
            }
            if (ifFalse.isInstanceOf(NullConstant.class)) {
                return context.evaluationContext().getProperty(ifTrue, property, duringEvaluation, false);
            }
            return new MultiExpression(ifTrue, ifFalse).getProperty(context, property, duringEvaluation);
        }
        return new MultiExpression(condition, ifTrue, ifFalse).getProperty(context, property, duringEvaluation);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        LinkedVariables linkedVariablesTrue = ifTrue.linkedVariables(context);
        LinkedVariables linkedVariablesFalse = ifFalse.linkedVariables(context);
        return linkedVariablesTrue.merge(linkedVariablesFalse);
    }

    @Override
    public int order() {
        return condition.order();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            condition.visit(predicate);
            ifTrue.visit(predicate);
            ifFalse.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return ListUtil.immutableConcat(condition.variables(descendIntoFieldReferences),
                ifTrue.variables(descendIntoFieldReferences),
                ifFalse.variables(descendIntoFieldReferences));
    }

    @Override
    public List<Variable> variablesWithoutCondition() {
        return ListUtil.immutableConcat(ifTrue.variablesWithoutCondition(), ifFalse.variablesWithoutCondition());
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        ForwardEvaluationInfo fwd = forwardEvaluationInfo.copy().notNullNotAssignment().build();
        EvaluationResult conditionResult = condition.evaluate(context, fwd);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context).compose(conditionResult);

        boolean resultIsBoolean = returnType().equals(context.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        Expression condition = conditionResult.value();
        Set<Variable> conditionVariables = Stream.concat(this.condition.variables(true).stream(),
                condition.variables(true).stream()).collect(Collectors.toUnmodifiableSet());
        if (condition.isInstanceOf(NullConstant.class) && forwardEvaluationInfo.isComplainInlineConditional()) {
            builder.raiseError(getIdentifier(), Message.Label.NULL_POINTER_EXCEPTION);
            condition = Instance.forUnspecifiedCondition(getIdentifier(), context.getPrimitives());
        }
        Expression conditionAfterState = context.evaluationContext().getConditionManager()
                .evaluate(context, condition, false);

        EvaluationResult copyForThen = resultIsBoolean ? context : context.child(condition, conditionVariables);
        EvaluationResult ifTrueResult = ifTrue.evaluate(copyForThen, forwardEvaluationInfo);
        builder.compose(ifTrueResult);

        EvaluationResult copyForElse = resultIsBoolean ? context :
                context.child(Negation.negate(context, condition), conditionVariables);
        EvaluationResult ifFalseResult = ifFalse.evaluate(copyForElse, forwardEvaluationInfo);
        builder.compose(ifFalseResult);

        Expression t = ifTrueResult.value();
        Expression f = ifFalseResult.value();

        if (condition.isEmpty() || t.isEmpty() || f.isEmpty()) {
            throw new UnsupportedOperationException();
        }
        EvaluationResult cv = EvaluateInlineConditional.conditionalValueConditionResolved(context,
                conditionAfterState, t, f, forwardEvaluationInfo.isComplainInlineConditional(), null);
        return builder.compose(cv).build();
    }

    public Expression optimise(EvaluationResult evaluationContext, Variable myself) {
        return optimise(evaluationContext, false, myself);
    }

    private Expression optimise(EvaluationResult evaluationContext, boolean useState, Variable myself) {
        boolean resultIsBoolean = returnType().equals(evaluationContext.getPrimitives().booleanParameterizedType());

        // we'll want to evaluate in a different context, but pass on forward evaluation info to both
        // UNLESS the result is of boolean type. There is sufficient logic in EvaluateInlineConditional to deal
        // with the boolean case.
        EvaluationResult copyForThen = resultIsBoolean ? evaluationContext : evaluationContext.child(condition, condition.variables(true).stream().collect(Collectors.toUnmodifiableSet()));
        Expression t = ifTrue instanceof InlineConditional inlineTrue ? inlineTrue.optimise(copyForThen, true, myself) : ifTrue;
        EvaluationResult copyForElse = resultIsBoolean ? evaluationContext : evaluationContext.child(Negation.negate(evaluationContext, condition), condition.variables(true).stream().collect(Collectors.toUnmodifiableSet()));
        Expression f = ifFalse instanceof InlineConditional inlineFalse ? inlineFalse.optimise(copyForElse, true, myself) : ifFalse;

        if (useState) {
            return EvaluateInlineConditional.conditionalValueCurrentState(evaluationContext, condition, t, f).getExpression();
        }
        return EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext, condition, t, f,
                false, myself).getExpression();

    }

    @Override
    public ParameterizedType returnType() {
        if (ifTrue.isNull() && ifFalse.isNull()) {
            return inspectionProvider.getPrimitives().objectParameterizedType();
        }
        if (ifTrue.isNull()) return ifFalse.returnType().ensureBoxed(inspectionProvider.getPrimitives());
        if (ifFalse.isNull()) return ifTrue.returnType().ensureBoxed(inspectionProvider.getPrimitives());
        return ifTrue.returnType().commonType(inspectionProvider, ifFalse.returnType());
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        if (ifTrue.isNull() && ifFalse.isNull()) {
            return Set.of(inspectionProvider.getPrimitives().objectParameterizedType());
        }
        if (ifTrue.isNull()) {
            return ifFalse.erasureTypes(typeContext);
        }
        if (ifFalse.isNull()) return ifTrue.erasureTypes(typeContext);
        return SetUtil.immutableUnion(ifTrue.erasureTypes(typeContext), ifFalse.erasureTypes(typeContext));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    // IMPORTANT NOTE: this is a pretty expensive operation, even with the causesOfDelay cache!
    // removing the condition is the least to do.
    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        //Expression c = condition.isDelayed() ? condition.mergeDelays(causesOfDelay) : condition;
        Expression t = ifTrue.isDelayed() ? ifTrue.mergeDelays(causesOfDelay) : ifTrue;
        Expression f = ifFalse.isDelayed() ? ifFalse.mergeDelays(causesOfDelay) : ifFalse;
        if (t != ifTrue || f != ifFalse) {
            return new InlineConditional(identifier, inspectionProvider, condition, t, f);
        }
        return this;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(condition, ifTrue, ifFalse);
    }

    @Override
    public Expression removeAllReturnValueParts(Primitives primitives) {
        Expression c = condition.removeAllReturnValueParts(primitives);
        Expression t = ifTrue.removeAllReturnValueParts(primitives);
        Expression f = ifFalse.removeAllReturnValueParts(primitives);
        if (c == null) return null;
        if (t == null) return f;
        if (f == null) return t;
        if (c.isBoolValueTrue()) return t;
        if (c.isBoolValueFalse()) return f;
        return new InlineConditional(identifier, inspectionProvider, c, t, f);
    }

    @Override
    public Expression extractConditions(Primitives primitives) {
        return condition;
    }

    @Override
    public Expression applyCondition(Expression newState) {
        if (newState.isBoolValueTrue()) return ifTrue;
        if (newState.isBoolValueFalse()) return ifFalse;
        return this;
    }

    @Override
    public boolean isNotYetAssigned() {
        return ifTrue.isNotYetAssigned() && ifFalse.isNotYetAssigned();
    }

    @Override
    public Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext, ParameterizedType parameterizedType) {
        var t = ifTrue.loopSourceVariables(analyserContext, parameterizedType);
        var f = ifFalse.loopSourceVariables(analyserContext, parameterizedType);
        if (t.isLeft() && f.isLeft()) return Either.left(t.getLeft().merge(f.getLeft()));
        if (t.isLeft()) return t;
        if (f.isLeft()) return f;
        return Either.right(SetUtil.immutableUnion(t.getRight(), f.getRight()));
    }

    @Override
    public Set<Variable> directAssignmentVariables() {
        return SetUtil.immutableUnion(ifTrue.directAssignmentVariables(), ifFalse.directAssignmentVariables());
    }
}
