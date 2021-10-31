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

import com.github.javaparser.ast.expr.AssignExpr;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static org.e2immu.analyser.analyser.StatementAnalyser.EVALUATION_OF_MAIN_EXPRESSION;
import static org.e2immu.analyser.analyser.util.DelayDebugger.*;
import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION;
import static org.e2immu.analyser.util.Logger.log;

public class Assignment extends ElementImpl implements Expression {

    public final Expression target;
    public final Expression value;
    public final MethodInfo assignmentOperator;
    public final MethodInfo binaryOperator;
    private final Primitives primitives;
    // see the discussion at DependentVariable
    public final Variable variableTarget;

    // if null, and binary operator not null, then the primitive operator counts (i += value)
    // if true, we have ++i
    // if false, we have i++ if primitive operator is +=, i-- if primitive is -=
    public final Boolean prefixPrimitiveOperator;
    public final boolean complainAboutAssignmentOutsideType;

    public Assignment(Primitives primitives, @NotNull Expression target, @NotNull Expression value) {
        this(Identifier.generate(), primitives,
                target, value, null, null, true);
    }

    public Assignment(Identifier identifier, Primitives primitives, @NotNull Expression target, @NotNull Expression value) {
        this(identifier, primitives, target, value, null, null, true);
    }

    public Assignment(Identifier identifier,
                      Primitives primitives,
                      @NotNull Expression target, @NotNull Expression value,
                      MethodInfo assignmentOperator,
                      Boolean prefixPrimitiveOperator,
                      boolean complainAboutAssignmentOutsideType) {
        super(identifier);
        this.complainAboutAssignmentOutsideType = complainAboutAssignmentOutsideType;
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
        this.assignmentOperator = assignmentOperator; // as in i+=1;
        this.prefixPrimitiveOperator = prefixPrimitiveOperator;
        binaryOperator = assignmentOperator == null ? null : BinaryOperator.fromAssignmentOperatorToNormalOperator(primitives, assignmentOperator);
        this.primitives = primitives;
        VariableExpression ve;
        if ((ve = target.asInstanceOf(VariableExpression.class)) != null) {
            variableTarget = ve.variable();
        } else if (target instanceof ArrayAccess arrayAccess) {
            variableTarget = arrayAccess.dependentVariable;
        } else {
            String name = target.minimalOutput() + "[" + value.minimalOutput() + "]";
            variableTarget = new DependentVariable(name, name, null, target.returnType(), value.variables(), null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return target.equals(that.target) &&
                value.equals(that.value) &&
                Objects.equals(assignmentOperator, that.assignmentOperator) &&
                Objects.equals(prefixPrimitiveOperator, that.prefixPrimitiveOperator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(target, value, assignmentOperator, prefixPrimitiveOperator);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new Assignment(identifier, primitives, translationMap.translateExpression(target),
                translationMap.translateExpression(value), assignmentOperator, prefixPrimitiveOperator,
                complainAboutAssignmentOutsideType);
    }

    @Override
    public int order() {
        return 0;
    }


    @NotNull
    public static MethodInfo operator(Primitives primitives, @NotNull AssignExpr.Operator operator,
                                      @NotNull TypeInfo widestType) {
        switch (operator) {
            case PLUS:
                return primitives.assignPlusOperatorInt;
            case MINUS:
                return primitives.assignMinusOperatorInt;
            case MULTIPLY:
                return primitives.assignMultiplyOperatorInt;
            case DIVIDE:
                return primitives.assignDivideOperatorInt;
            case BINARY_OR:
                return primitives.assignOrOperatorInt;
            case BINARY_AND:
                return primitives.assignAndOperatorInt;
            case ASSIGN:
                return primitives.assignOperatorInt;
        }
        throw new UnsupportedOperationException("Need to add primitive operator " +
                operator + " on type " + widestType.fullyQualifiedName);
    }

    @Override
    public ParameterizedType returnType() {
        return target.returnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (prefixPrimitiveOperator != null) {
            String operator = assignmentOperator == primitives.assignPlusOperatorInt ? "++" : "--";
            if (prefixPrimitiveOperator) {
                return new OutputBuilder().add(Symbol.plusPlusPrefix(operator)).add(outputInParenthesis(qualification, precedence(), target));
            }
            return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), target)).add(Symbol.plusPlusSuffix(operator));
        }
        //  != null && primitiveOperator != primitives.assignOperatorInt ? "=" + primitiveOperator.name : "=";
        String operator = assignmentOperator == null ? "=" : assignmentOperator.name;
        return new OutputBuilder().add(outputInParenthesis(qualification, precedence(), target))
                .add(Symbol.assignment(operator))
                .add(outputInParenthesis(qualification, precedence(), value));
    }

    @Override
    public Precedence precedence() {
        return Precedence.ASSIGNMENT;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(target, value);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            value.visit(predicate);
            target.visit(predicate);
        }
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        VariableExpression ve = target.asInstanceOf(VariableExpression.class);
        ForwardEvaluationInfo fwd = forwardEvaluationInfo.copyAddAssignmentTarget(ve == null ? null : ve.variable());

        EvaluationResult valueResult = value.evaluate(evaluationContext, fwd);

        EvaluationResult targetResult = target.evaluate(evaluationContext, ForwardEvaluationInfo.ASSIGNMENT_TARGET);
        builder.compose(valueResult);


        // re-assess the index in dependent variables TODO feels shaky implementation (re-assessing the index is correct)
        IsVariableExpression variableValue;
        Variable newVariableTarget = (variableValue = targetResult.value().asInstanceOf(IsVariableExpression.class)) != null &&
                variableValue.variable() instanceof DependentVariable
                ? variableValue.variable() : variableTarget;

        log(EXPRESSION, "Assignment: {} = {}", newVariableTarget.fullyQualifiedName(), value);

        Expression resultOfExpression;
        Expression assignedToTarget;
        if (binaryOperator != null) {
            BinaryOperator operation = new BinaryOperator(identifier,
                    primitives, new VariableExpression(newVariableTarget), binaryOperator, value,
                    BinaryOperator.precedence(evaluationContext.getPrimitives(), binaryOperator));
            EvaluationResult operationResult = operation.evaluate(evaluationContext, forwardEvaluationInfo);
            builder.compose(operationResult);

            if (prefixPrimitiveOperator == null || prefixPrimitiveOperator) {
                // ++i, i += 1
                resultOfExpression = operationResult.value();
            } else {
                // i++
                Expression post = new VariableExpression(newVariableTarget);
                EvaluationResult variableOnly = post.evaluate(evaluationContext, forwardEvaluationInfo);
                resultOfExpression = variableOnly.value();
                // not composing, any error will have been raised already
            }
            assignedToTarget = operationResult.value();
        } else {
            resultOfExpression = valueResult.value();
            assignedToTarget = valueResult.value();

            /*
            we compare to value and not resultOfExpression here, to catch a literal j = j assignment
            the resultOfExpression may be different!
            however, this does not catch j = idem(j), with idem @Identity; for that, we'll need resultOfExpression,
            but that needs comparing against the current value
             */
            IsVariableExpression ive;
            if ((ive = value.asInstanceOf(IsVariableExpression.class)) != null && ive.variable().equals(newVariableTarget)) {
                return builder.assignmentToSelfIgnored(newVariableTarget).build();
            }

            EvaluationResult currentTargetValue = target.evaluate(evaluationContext, fwd);
            Expression currentValue = currentTargetValue.value();
            IsVariableExpression ive2;
            if (currentValue != null && (currentValue.equals(assignedToTarget) ||
                    ((ive2 = assignedToTarget.asInstanceOf(IsVariableExpression.class)) != null)
                            && newVariableTarget.equalsOrEqualToCopy(ive2.variable())) &&
                    !evaluationContext.firstAssignmentOfFieldInConstructor(newVariableTarget)) {
                log(EXPRESSION, "Assigning identical value {} to {}", currentValue, newVariableTarget);
                builder.assignmentToCurrentValue(newVariableTarget);
                // do continue! we do not want to ignore the assignment
            }
        }
        builder.composeIgnoreExpression(targetResult);

        assert assignedToTarget != null;
        assert assignedToTarget != EmptyExpression.EMPTY_EXPRESSION;
        doAssignmentWork(builder, evaluationContext, newVariableTarget, assignedToTarget);
        assert resultOfExpression != null;
        return builder.setExpression(resultOfExpression).build();
    }

    private void doAssignmentWork(EvaluationResult.Builder builder,
                                  EvaluationContext evaluationContext,
                                  Variable at,
                                  Expression resultOfExpression) {

        // see if we need to raise an error (writing to fields outside our class, etc.)
        if (at instanceof FieldReference fieldReference) {

            // check illegal assignment into nested type
            if (complainAboutAssignmentOutsideType &&
                    checkIllAdvisedAssignment(fieldReference, evaluationContext.getCurrentType(),
                            fieldReference.fieldInfo.isStatic(evaluationContext.getAnalyserContext()))) {
                builder.addErrorAssigningToFieldOutsideType(fieldReference.fieldInfo);
            }
        } else if (at instanceof ParameterInfo parameterInfo) {
            builder.addParameterShouldNotBeAssignedTo(parameterInfo);
        }

        // may already be linked to others
        LinkedVariables lvExpression = resultOfExpression.linkedVariables(evaluationContext);
        LinkedVariables linkedVariables;
        IsVariableExpression ive = value.asInstanceOf(IsVariableExpression.class);
        IsVariableExpression iveResult = resultOfExpression.asInstanceOf(IsVariableExpression.class);
        if(ive != null && iveResult != null && iveResult.variable().variableNature().localCopyOf() == null) {
            /* To preserve uni-directionality from local copy to main variable, we only add the variable when the
            result of the expression is not a variable either. (in which case they point to the same underlying variable anyway)
            */
            linkedVariables = lvExpression.merge(new LinkedVariables(Map.of(ive.variable(), LinkedVariables.ASSIGNED)));
        } else {
            linkedVariables = lvExpression.minimum(LinkedVariables.DEPENDENT);
        }
        assert !linkedVariables.isDelayed() ||
                evaluationContext.translatedDelay(EVALUATION_OF_MAIN_EXPRESSION,
                        "EXPRESSION " + resultOfExpression + "@" + evaluationContext.statementIndex() + D_LINKED_VARIABLES,
                        at.fullyQualifiedName() + "@" + evaluationContext.statementIndex() + D_LINKED_VARIABLES_SET);


        builder.assignment(at, resultOfExpression, linkedVariables);
    }

    private static boolean checkIllAdvisedAssignment(FieldReference fieldReference, TypeInfo currentType, boolean isStatic) {
        TypeInfo owner = fieldReference.fieldInfo.owner;
        if (owner.primaryType() != currentType.primaryType()) return true; // outside primary type
        if (owner == currentType) { // in the same type
            // so if x is a local variable of the current type, we can do this.field =, but not x.field = !
            if (isStatic) {
                return fieldReference.scope != null && !(fieldReference.scope instanceof TypeExpression);
            }
            return !fieldReference.scopeIsThis();
        }
        /* outside current type, but inside primary type: we allow assignments
         1. when the owner is an enclosing type (up)
         2. when the owner is private, and the owner is enclosed (down)
         */
        return !(owner.isPrivateNested() && owner.isEnclosedIn(currentType)) && !currentType.isEnclosedIn(owner);
    }
}
