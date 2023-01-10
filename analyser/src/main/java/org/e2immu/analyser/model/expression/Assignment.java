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
    private final EvaluationResult evaluationOfValue;

    // used for assignments with Explicit Constructor Invocation, which cannot happen in the first iteration
    // see SAInitializersAndUpdaters
    public final boolean allowStaticallyAssigned;
    // used for computation of return statement linked variables, after merge
    // use null to indicate that this set is to be computed (the default!)
    private final Set<Variable> directAssignmentVariables;

    private Assignment(Identifier identifier,
                       Primitives primitives,
                       @NotNull Expression target,
                       @NotNull Expression value,
                       MethodInfo assignmentOperator,
                       Boolean prefixPrimitiveOperator,
                       boolean complainAboutAssignmentOutsideType,
                       Variable variableTarget,
                       MethodInfo binaryOperator,
                       boolean hackForUpdatersInForLoop,
                       boolean allowStaticallyAssigned,
                       EvaluationResult evaluationOfValue,
                       Set<Variable> directAssignmentVariables) {
        super(identifier, 1 + target.getComplexity() + value.getComplexity());
        this.primitives = primitives;
        this.target = target;
        this.value = value;
        this.assignmentOperator = assignmentOperator;
        this.prefixPrimitiveOperator = prefixPrimitiveOperator;
        this.complainAboutAssignmentOutsideType = complainAboutAssignmentOutsideType;
        this.variableTarget = variableTarget;
        this.binaryOperator = binaryOperator;
        this.hackForUpdatersInForLoop = hackForUpdatersInForLoop;
        this.allowStaticallyAssigned = allowStaticallyAssigned;
        this.directAssignmentVariables = directAssignmentVariables;
        this.evaluationOfValue = evaluationOfValue;
    }

    // see explanation below (makeHackInstance); called in SAInitializersAndUpdaters
    public Expression cloneWithHackForLoop() {
        return new Assignment(identifier, primitives, target, value, assignmentOperator, prefixPrimitiveOperator,
                complainAboutAssignmentOutsideType, variableTarget, binaryOperator, true,
                allowStaticallyAssigned, evaluationOfValue, directAssignmentVariables);
    }

    public Assignment(Primitives primitives, @NotNull Expression target, @NotNull Expression value) {
        this(Identifier.joined("new assignment", List.of(target.getIdentifier(), value.getIdentifier())), primitives,
                target, value, null, null, true,
                true, null, null);
    }

    // used in SAEvaluationOfMainExpression, for assignments to the return variable
    public Assignment(Primitives primitives,
                      @NotNull Expression target,
                      @NotNull Expression value,
                      EvaluationResult evaluationOfValue,
                      Set<Variable> directAssignmentVariables) {
        this(Identifier.joined("new assignment", List.of(target.getIdentifier(), value.getIdentifier())), primitives,
                target, value, null, null, true,
                true, evaluationOfValue, directAssignmentVariables);
    }

    public Assignment(Identifier identifier, Primitives primitives, @NotNull Expression target, @NotNull Expression value) {
        this(identifier, primitives, target, value, null, null,
                true, true, null, null);
    }

    public Assignment(Identifier identifier,
                      Primitives primitives,
                      @NotNull Expression target,
                      @NotNull Expression value,
                      MethodInfo assignmentOperator,
                      Boolean prefixPrimitiveOperator,
                      boolean complainAboutAssignmentOutsideType,
                      boolean allowStaticallyAssigned,
                      EvaluationResult evaluationOfValue,
                      Set<Variable> directAssignmentVariables) {
        super(identifier, 1 + target.getComplexity() + value.getComplexity());
        this.complainAboutAssignmentOutsideType = complainAboutAssignmentOutsideType;
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
        this.assignmentOperator = Objects.requireNonNullElseGet(assignmentOperator,
                () -> primitives.assignOperator(target.returnType())); // as in i+=1, j=a;
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
        this.allowStaticallyAssigned = allowStaticallyAssigned;
        this.directAssignmentVariables = directAssignmentVariables;
        this.evaluationOfValue = evaluationOfValue;
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
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        Expression translatedTarget = target.translate(inspectionProvider, translationMap);
        Expression translatedValue = value.translate(inspectionProvider, translationMap);
        Set<Variable> translatedDirect;
        if (directAssignmentVariables == null) {
            translatedDirect = null;
        } else {
            Set<Variable> translatedVariables = directAssignmentVariables.stream()
                    .map(v -> translationMap.translateVariable(inspectionProvider, v))
                    .collect(Collectors.toUnmodifiableSet());
            translatedDirect = translatedVariables.equals(directAssignmentVariables)
                    ? directAssignmentVariables : translatedVariables;
        }
        if (translatedValue == this.value && translatedTarget == this.target && translatedDirect == directAssignmentVariables)
            return this;

        Assignment a = new Assignment(identifier, primitives, translatedTarget,
                translatedValue, assignmentOperator, prefixPrimitiveOperator,
                complainAboutAssignmentOutsideType, allowStaticallyAssigned,
                evaluationOfValue, directAssignmentVariables);
        if (translationMap.translateAgain()) {
            return a.translate(inspectionProvider, translationMap);
        }
        return a;
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
                    binaryOperator, hackForUpdatersInForLoop, allowStaticallyAssigned,
                    evaluationOfValue, directAssignmentVariables);
        }
        return this;
    }

    @Override
    public int internalCompareTo(Expression v) {
        if (v instanceof Assignment other) {
            int c = target.compareTo(other.target);
            if (c != 0) return c;
            return value.compareTo(other.value);
        }
        throw new UnsupportedOperationException();
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
        if (forwardEvaluationInfo.isOnlySort()) {
            Expression evalTarget = target.evaluate(context, forwardEvaluationInfo).getExpression();
            Expression evalValue = value.evaluate(context, forwardEvaluationInfo).getExpression();
            Expression newAssignment = new Assignment(identifier, primitives, evalTarget, evalValue, assignmentOperator,
                    prefixPrimitiveOperator, complainAboutAssignmentOutsideType, variableTarget,
                    binaryOperator, hackForUpdatersInForLoop, allowStaticallyAssigned,
                    evaluationOfValue, directAssignmentVariables);
            return builder.setExpression(newAssignment).build();
        }

        VariableExpression ve = target.asInstanceOf(VariableExpression.class);

        // see Warnings_13, we want to raise a potential null pointer exception when a non-primitive is assigned to a primitive
        Variable variable = ve == null ? null : ve.variable();
        ForwardEvaluationInfo.Builder fwdBuilder = new ForwardEvaluationInfo.Builder(forwardEvaluationInfo)
                .setAssignmentTarget(variable);
        if (target.returnType().isPrimitiveExcludingVoid()) {
            fwdBuilder.setCnnNotNull();
        }
        ForwardEvaluationInfo fwd = fwdBuilder.build();

        EvaluationResult valueResult;
        if (evaluationOfValue != null) {
            valueResult = evaluationOfValue;
        } else {
            valueResult = value.evaluate(context, fwd);
        }

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
            if ((ive = value.asInstanceOf(IsVariableExpression.class)) != null
                    && ive.variable().equals(newVariableTarget) && value.isDone()) {
                return builder.assignmentToSelfIgnored(newVariableTarget).build();
            }
            e2 = handleNormalAssignment(context, valueResult.value(), newVariableTarget, targetResult.value(), builder);
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

        markModified(builder, context, newVariableTarget);
        LinkedVariables lvAfterDelay = computeLinkedVariables(context, finalValue);
        builder.assignment(newVariableTarget, finalValue, lvAfterDelay);

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
                                      Expression valueResultValue,
                                      Variable newVariableTarget,
                                      Expression currentValueOfTarget,
                                      EvaluationResult.Builder builder) {
        IsVariableExpression ive2;
        if (currentValueOfTarget != null && (currentValueOfTarget.equals(valueResultValue) ||
                ((ive2 = valueResultValue.asInstanceOf(IsVariableExpression.class)) != null)
                        && newVariableTarget.equals(ive2.variable())) &&
                !(newVariableTarget instanceof ReturnVariable) &&
                !context.evaluationContext().firstAssignmentOfFieldInConstructor(newVariableTarget)) {
            LOGGER.debug("Assigning identical value {} to {}", currentValueOfTarget, newVariableTarget);
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

    private void markModified(EvaluationResult.Builder builder, EvaluationResult context, Variable at) {
        // see if we need to raise an error (writing out to fields outside our class, etc.)
        if (at instanceof FieldReference fieldReference) {

            // check illegal assignment into nested type
            if (complainAboutAssignmentOutsideType &&
                    checkIllAdvisedAssignment(fieldReference, context.getCurrentType(),
                            context.getAnalyserContext().getFieldInspection(fieldReference.fieldInfo).isStatic())) {
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

                // IMPROVE: recursion also in markModified --  but what about the code above?
                // recurse!
                markModified(builder, context, fieldReference.scopeVariable);
            }
        } else if (at instanceof ParameterInfo parameterInfo) {
            builder.addParameterShouldNotBeAssignedTo(parameterInfo);
        } else if (at instanceof DependentVariable dv) {
            Variable arrayVariable = dv.arrayVariable();
            if (arrayVariable != null) {
                builder.markRead(arrayVariable);
                builder.markContextModified(arrayVariable, DV.TRUE_DV);

                // IMPROVE: recursion also in markModified
                // recurse!
                markModified(builder, context, arrayVariable);
            }
        }
    }

    private LinkedVariables computeLinkedVariables(EvaluationResult context, Expression resultOfExpression) {
        /*
        There are fundamentally two approaches to computing linked variables here.
        The first is to compute them on "value", the second one on "resultOfExpression".
        The former stays the same, and does not include the "tryShortCut", "single return value" substitutions,
        computation simplifications, etc. etc., which are present in the latter.

        We choose the former approach! this has repercussions...
        Update 20221030: we do both! See Independent1_12 for why this is necessary (stream:2)
         */
        LinkedVariables lvExpression = resultOfExpression.linkedVariables(context)
                .merge(value.linkedVariables(context))
                .minimum(LinkedVariables.LINK_ASSIGNED);
        LinkedVariables linkedVariables;
        if (allowStaticallyAssigned) {
            Set<Variable> directAssignment = directAssignmentVariables != null ? directAssignmentVariables
                    : value.directAssignmentVariables();
            if (!directAssignment.isEmpty()) {
                Map<Variable, DV> map = directAssignment.stream()
                        .collect(Collectors.toMap(v -> v, v -> LinkedVariables.LINK_STATICALLY_ASSIGNED));
                linkedVariables = lvExpression.merge(LinkedVariables.of(map));
            } else {
                linkedVariables = lvExpression;
            }
        } else {
            linkedVariables = lvExpression;
        }
        if (resultOfExpression.isDelayed()) {
            Set<Variable> vars = new HashSet<>(value.variables(true));
            Map<Variable, DV> map = vars.stream()
                    .collect(Collectors.toUnmodifiableMap(v -> v, v -> resultOfExpression.causesOfDelay()));
            return linkedVariables.merge(LinkedVariables.of(map));
        }
        return linkedVariables;
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
