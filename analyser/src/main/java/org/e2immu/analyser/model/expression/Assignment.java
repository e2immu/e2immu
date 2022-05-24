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
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Assignment extends BaseExpression implements Expression {
    private static final Logger LOGGER = LoggerFactory.getLogger(Assignment.class);

    public final Expression target;
    public final Expression value;
    public final MethodInfo assignmentOperator;
    public final MethodInfo binaryOperator;
    private final Primitives primitives;
    // see the discussion at DependentVariable
    public final Variable variableTarget;

    // if null, and binary operator not null, then the primitive operator counts (i += value)
    // if true, we have ++i
    // if false, we have i++
    public final Boolean prefixPrimitiveOperator;
    public final boolean complainAboutAssignmentOutsideType;
    public final boolean hackForUpdatersInForLoop;

    private Assignment(Identifier identifier,
                       Primitives primitives,
                       @NotNull Expression target,
                       @NotNull Expression value,
                       MethodInfo assignmentOperator,
                       Boolean prefixPrimitiveOperator,
                       boolean complainAboutAssignmentOutsideType,
                       Variable variableTarget,
                       MethodInfo binaryOperator,
                       boolean hackForUpdatersInForLoop) {
        super(identifier);
        this.primitives = primitives;
        this.target = target;
        this.value = value;
        this.assignmentOperator = assignmentOperator;
        this.prefixPrimitiveOperator = prefixPrimitiveOperator;
        this.complainAboutAssignmentOutsideType = complainAboutAssignmentOutsideType;
        this.variableTarget = variableTarget;
        this.binaryOperator = binaryOperator;
        this.hackForUpdatersInForLoop = hackForUpdatersInForLoop;
    }

    // see explanation below (makeHackInstance); called in SAInitializersAndUpdaters
    public Expression cloneWithHackForLoop() {
        return new Assignment(identifier, primitives, target, value, assignmentOperator, prefixPrimitiveOperator,
                complainAboutAssignmentOutsideType, variableTarget, binaryOperator, true);
    }

    public Assignment(Primitives primitives, @NotNull Expression target, @NotNull Expression value) {
        this(Identifier.joined("new assignment", List.of(target.getIdentifier(), value.getIdentifier())), primitives,
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
            throw new UnsupportedOperationException();
        }
        hackForUpdatersInForLoop = false;
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translatedTarget = target.translate(inspectionProvider, translationMap);
        Expression translatedValue = value.translate(inspectionProvider, translationMap);
        if (translatedValue == this.value && translatedTarget == this.target) return this;
        return new Assignment(identifier, primitives, translatedTarget,
                translatedValue, assignmentOperator, prefixPrimitiveOperator,
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
                return primitives.assignPlusOperatorInt();
            case MINUS:
                return primitives.assignMinusOperatorInt();
            case MULTIPLY:
                return primitives.assignMultiplyOperatorInt();
            case DIVIDE:
                return primitives.assignDivideOperatorInt();
            case BINARY_OR:
                return primitives.assignOrOperatorInt();
            case BINARY_AND:
                return primitives.assignAndOperatorInt();
            case ASSIGN:
                return primitives.assignOperatorInt();
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
            String operator = assignmentOperator == primitives.assignPlusOperatorInt() ? "++" : "--";
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

    public boolean isPlusEquals() {
        return assignmentOperator != null && "+=".equals(assignmentOperator.name);
    }

    public boolean isMinusEquals() {
        return assignmentOperator != null && "-=".equals(assignmentOperator.name);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return value.causesOfDelay();
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        if (value.isDelayed()) {
            return new Assignment(identifier, primitives, target, value.mergeDelays(causesOfDelay),
                    assignmentOperator, prefixPrimitiveOperator, complainAboutAssignmentOutsideType, variableTarget,
                    binaryOperator, hackForUpdatersInForLoop);
        }
        return this;
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
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            value.visit(predicate);
            target.visit(predicate);
        }
    }

    private record E2(Expression resultOfExpression, Expression assignedToTarget) {
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        VariableExpression ve = target.asInstanceOf(VariableExpression.class);

        // see Warnings_13, we want to raise a potential null pointer exception when a non-primitive is assigned to a primitive
        Variable variable = ve == null ? null : ve.variable();
        ForwardEvaluationInfo.Builder fwdBuilder = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                .setAssignmentTarget(variable);
        if (target.returnType().isPrimitiveExcludingVoid()) {
            fwdBuilder.setCnnNotNull();
        }
        ForwardEvaluationInfo fwd = fwdBuilder.build();

        EvaluationResult valueResult = value.evaluate(context, fwd);

        EvaluationResult targetResult = target.evaluate(context, ForwardEvaluationInfo.ASSIGNMENT_TARGET);
        builder.compose(valueResult);

        Variable newVariableTarget = handleArrayAccess(targetResult.value());

        LOGGER.debug("Assignment: {} = {}", newVariableTarget.fullyQualifiedName(), value);

        E2 e2;
        if (binaryOperator != null) {
            e2 = handleBinaryOperator(context, forwardEvaluationInfo, newVariableTarget, builder);
        } else {
            /*
            we compare to value and not resultOfExpression here, to catch a literal j = j assignment
            the resultOfExpression may be different!
            however, this does not catch j = idem(j), with idem @Identity; for that, we'll need resultOfExpression,
            but that needs comparing against the current value
             */
            IsVariableExpression ive;
            if ((ive = value.asInstanceOf(IsVariableExpression.class)) != null && ive.variable().equals(newVariableTarget) && value.isDone()) {
                return builder.assignmentToSelfIgnored(newVariableTarget).build();
            }
            e2 = handleNormalAssignment(context, fwd, valueResult.value(), newVariableTarget, builder);
        }
        builder.composeIgnoreExpression(targetResult);

        assert e2.assignedToTarget != null;
        assert e2.assignedToTarget != EmptyExpression.EMPTY_EXPRESSION;
        Expression finalValue;
        Expression expression;
        if (hackForUpdatersInForLoop) {
            finalValue = makeHackInstance(context, e2.assignedToTarget.causesOfDelay());
            expression = e2.assignedToTarget;
        } else {
            finalValue = e2.assignedToTarget;
            expression = e2.resultOfExpression;
        }
        // we by-pass the result of normal assignment which raises the i=i assign to myself error
        doAssignmentWork(builder, context, newVariableTarget, finalValue);
        assert expression != null;
        return builder.setExpression(expression).build();
    }

    /*
    The "hack" consists of, when faced with an updater i=i+1 which is executed at evaluation in the loop,
    to replace i=i+1 with i=instance, while still evaluating i+1.
    If we don't do this, the variable will appear updated (1+instance) throughout the loop, which messes up all kinds
    of comparisons.

    We've chosen this approach over a blanket search for variables which should get an instance value.
     */
    private Expression makeHackInstance(EvaluationResult context, CausesOfDelay causes) {
        if (causes.isDelayed()) {
            return DelayedVariableExpression.forVariable(variableTarget,
                    context.evaluationContext().getInitialStatementTime(), causes);
        }
        Properties valueProperties = context.getAnalyserContext().defaultValueProperties(target.returnType());
        return Instance.forVariableInLoopDefinedOutside(identifier, target.returnType(), valueProperties);
    }

    // in a normal assignment, we use the "unevaluated" variable
    // in case of array access, like integers[3] in Warnings_1, this becomes a different variable.
    // this method deals with that.
    private Variable handleArrayAccess(Expression evaluatedTarget) {
        IsVariableExpression ive;
        if (variableTarget instanceof DependentVariable &&
                (ive = evaluatedTarget.asInstanceOf(IsVariableExpression.class)) != null &&
                ive.variable() instanceof DependentVariable) {
            return ive.variable();
        }
        return variableTarget;
    }

    private E2 handleNormalAssignment(EvaluationResult context,
                                      ForwardEvaluationInfo fwd,
                                      Expression valueResultValue,
                                      Variable newVariableTarget,
                                      EvaluationResult.Builder builder) {

        EvaluationResult currentTargetValue = target.evaluate(context, fwd);
        Expression currentValue = currentTargetValue.value();
        IsVariableExpression ive2;
        if (currentValue != null && (currentValue.equals(valueResultValue) ||
                ((ive2 = valueResultValue.asInstanceOf(IsVariableExpression.class)) != null)
                        && newVariableTarget.equals(ive2.variable())) &&
                !(newVariableTarget instanceof ReturnVariable) &&
                !context.evaluationContext().firstAssignmentOfFieldInConstructor(newVariableTarget)) {
            LOGGER.debug("Assigning identical value {} to {}", currentValue, newVariableTarget);
            builder.assignmentToCurrentValue(newVariableTarget);
            // do continue! we do not want to ignore the assignment; however, due to warnings for self-assignment
            // we'll assign to the value
            Expression previous = context.currentValue(newVariableTarget);
            return new E2(valueResultValue, previous);
        }
        return new E2(valueResultValue, valueResultValue);
    }

    private E2 handleBinaryOperator(EvaluationResult context,
                                    ForwardEvaluationInfo forwardEvaluationInfo,
                                    Variable newVariableTarget,
                                    EvaluationResult.Builder builder) {

        Expression resultOfExpression;
        BinaryOperator operation = new BinaryOperator(identifier,
                primitives, new VariableExpression(newVariableTarget), binaryOperator, value,
                BinaryOperator.precedence(context.getPrimitives(), binaryOperator));
        EvaluationResult operationResult = operation.evaluate(context, forwardEvaluationInfo);
        builder.compose(operationResult);

        if (prefixPrimitiveOperator == null || prefixPrimitiveOperator) {
            // ++i, i += 1
            resultOfExpression = operationResult.value();
        } else {
            // i++
            Expression post = new VariableExpression(newVariableTarget);
            EvaluationResult variableOnly = post.evaluate(context, forwardEvaluationInfo);
            resultOfExpression = variableOnly.value();
            // not composing, any error will have been raised already
        }
        return new E2(resultOfExpression, operationResult.value());
    }

    private void doAssignmentWork(EvaluationResult.Builder builder,
                                  EvaluationResult context,
                                  Variable at,
                                  Expression resultOfExpression) {

        // see if we need to raise an error (writing out to fields outside our class, etc.)
        if (at instanceof FieldReference fieldReference) {

            // check illegal assignment into nested type
            if (complainAboutAssignmentOutsideType &&
                    checkIllAdvisedAssignment(fieldReference, context.getCurrentType(),
                            fieldReference.fieldInfo.isStatic(context.getAnalyserContext()))) {
                builder.addErrorAssigningToFieldOutsideType(fieldReference.fieldInfo);
            }

            if (fieldReference.scopeVariable != null && !fieldReference.scopeIsThis()) {
                // set the variable's value to instance, much like calling a modifying method
                // see Basics_24 as a fine example
                // note: this one will overwrite the value of the scope, even if it is currently delayed
                ParameterizedType returnType = fieldReference.scopeVariable.parameterizedType();
                Properties valueProperties = context.getAnalyserContext().defaultValueProperties(returnType,
                        MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                CausesOfDelay causesOfDelay = valueProperties.delays();
                Expression instance;
                if (causesOfDelay.isDelayed()) {
                    instance = DelayedExpression.forDelayedValueProperties(identifier, returnType,
                            fieldReference.scope, causesOfDelay, Properties.EMPTY);
                } else {
                    instance = Instance.forGetInstance(identifier, returnType, valueProperties);
                }
                LinkedVariables lvs = fieldReference.scope.linkedVariables(context);
                builder.modifyingMethodAccess(fieldReference.scopeVariable, instance, lvs);

            }
        } else if (at instanceof ParameterInfo parameterInfo) {
            builder.addParameterShouldNotBeAssignedTo(parameterInfo);
        }

        /*
        There are fundamentally two approaches to computing linked variables here.
        The first is to compute them on "value", the second one on "resultOfExpression".
        The former stays the same, and does not include the "tryShortCut", "single return value" substitutions,
        computation simplifications, etc. etc., which are present in the latter.

        We choose the latter approach, but introduce a delay on all possible variables of the former as long as
        "resultOfExpression" is delayed.
         */
        LinkedVariables lvExpression = resultOfExpression.linkedVariables(context).minimum(LinkedVariables.ASSIGNED_DV);
        Set<Variable> directAssignment = value.directAssignmentVariables();
        LinkedVariables linkedVariables;
        if (!directAssignment.isEmpty()) {
            Map<Variable, DV> map = directAssignment.stream()
                    .collect(Collectors.toMap(v -> v, v -> LinkedVariables.STATICALLY_ASSIGNED_DV));
            linkedVariables = lvExpression.merge(LinkedVariables.of(map));
        } else {
            linkedVariables = lvExpression;
        }
        LinkedVariables lvAfterDelay;
        if (resultOfExpression.isDelayed()) {
            Set<Variable> vars = new HashSet<>(value.variables(true));
            Map<Variable, DV> map = vars.stream()
                    .collect(Collectors.toUnmodifiableMap(v -> v, v -> resultOfExpression.causesOfDelay()));
            lvAfterDelay = linkedVariables.merge(LinkedVariables.of(map));
        } else {
            lvAfterDelay = linkedVariables;
        }
        builder.assignment(at, resultOfExpression, lvAfterDelay);
    }

    private static boolean checkIllAdvisedAssignment(FieldReference fieldReference, TypeInfo currentType, boolean isStatic) {
        TypeInfo owner = fieldReference.fieldInfo.owner;
        if (owner.primaryType() != currentType.primaryType()) return true; // outside primary type
        if (owner == currentType) { // in the same type
            // so if x is a local variable of the current type, we can do this.field =, but not x.field = !
            if (isStatic) {
                return !(fieldReference.scope instanceof TypeExpression);
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
