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
import org.e2immu.analyser.analyser.util.DelayDebugNode;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.VariableProperty.INDEPENDENT;
import static org.e2immu.analyser.analyser.VariableProperty.MODIFIED_METHOD;

/*
 can only be created as the single result value of a method

 will be substituted at any time in MethodCall

 Big question: do properties come from the expression, or from the method??
 In the case of Supplier.get() (which is modifying by default), the expression may for example be a parameterized string (t + "abc").
 The string expression is Level 2 immutable, hence not-modified.

 Properties that rely on the return value, should come from the Value. Properties to do with modification, should come from the method.
 */
public record InlinedMethod(MethodInfo methodInfo,
                            Expression expression,
                            Set<Variable> variablesOfExpression,
                            Applicability applicability) implements Expression {

    public enum Applicability {
        EVERYWHERE(0), // no references to fields, static or otherwise, unless they are public
        PROTECTED(1), // reference to protected fields
        PACKAGE(2),  // reference to package-private fields
        TYPE(3),   // can only be applied in the same type (reference to private fields)
        METHOD(4), // can only be applied in the same method (reference to local variables)

        NONE(5); // cannot be expressed properly

        public final int order;

        Applicability(int order) {
            this.order = order;
        }

        public Applicability mostRestrictive(Applicability other) {
            return order < other.order ? other : this;
        }
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InlinedMethod(methodInfo,
                expression.translate(translationMap),
                variablesOfExpression.stream()
                        .map(translationMap::translateVariable).collect(Collectors.toUnmodifiableSet()),
                applicability);
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("", "/* inline " + methodInfo.name + " */"))
                .add(expression.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlinedMethod mv = (InlinedMethod) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    /*
    an inline method has properties on the method, and properties on the expression. these are on the method.
    */
    private final static Set<VariableProperty> METHOD_PROPERTIES_IN_INLINE_SAM = Set.of(MODIFIED_METHOD, INDEPENDENT);

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            case MODIFIED_METHOD, INDEPENDENT -> evaluationContext.getAnalyserContext()
                    .getMethodAnalysis(methodInfo).getProperty(variableProperty);
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE; // a method is immutable
            default -> evaluationContext.getProperty(expression, variableProperty, duringEvaluation, false);
        };
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Set<Variable> targetVariables = translation.values().stream().filter(e -> e instanceof VariableExpression)
                .map(e -> ((VariableExpression)e).variable()).collect(Collectors.toUnmodifiableSet());
        EvaluationContext closure = new EvaluationContextImpl(evaluationContext, targetVariables);
        EvaluationResult result = expression.reEvaluate(closure, translation);
        if (expression instanceof InlinedMethod im) {
            InlinedMethod newIm = new InlinedMethod(im.methodInfo(), result.getExpression(),
                    new HashSet<>(result.getExpression().variables()), im.applicability);
            return new EvaluationResult.Builder().compose(result).setExpression(newIm).build();
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlinedMethod that = (InlinedMethod) o;
        return methodInfo.equals(that.methodInfo) && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    public boolean canBeApplied(EvaluationContext evaluationContext) {
        return switch (applicability) {
            case EVERYWHERE -> true;
            case NONE -> false;
            case TYPE -> evaluationContext.getCurrentType().primaryType().equals(methodInfo.typeInfo.primaryType());
            case METHOD -> methodInfo.equals(evaluationContext.getCurrentMethod().methodInfo);
            case PACKAGE -> evaluationContext.getCurrentType().packageName().equals(methodInfo.typeInfo.packageName());
            default -> throw new UnsupportedOperationException("TODO");
        };
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationContext) {
        // TODO verify this
        return expression.getInstance(evaluationContext);
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final EvaluationContext evaluationContext;
        private final Set<Variable> acceptedVariables;

        protected EvaluationContextImpl(EvaluationContext evaluationContext, Set<Variable> targetVariables) {
            super(evaluationContext.getIteration(),
                    ConditionManager.initialConditionManager(evaluationContext.getPrimitives()), null);
            this.evaluationContext = evaluationContext;
            this.acceptedVariables = SetUtil.immutableUnion(variablesOfExpression, targetVariables);
        }

        protected EvaluationContextImpl(EvaluationContextImpl parent, ConditionManager conditionManager) {
            super(parent.iteration, conditionManager, null);
            this.evaluationContext = parent.evaluationContext;
            this.acceptedVariables = parent.acceptedVariables;
        }

        private void ensureVariableIsKnown(Variable variable) {
            if (!(variable instanceof This)) {
                assert acceptedVariables.contains(variable) : "there should be no other variables in this expression: " +
                        variable + " is not in " + variablesOfExpression();
            }
        }

        @Override
        public TypeInfo getCurrentType() {
            return applicability == Applicability.EVERYWHERE ? null : evaluationContext.getCurrentType();
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return applicability == Applicability.METHOD ? evaluationContext.getCurrentMethod() : null;
        }

        @Override
        public StatementAnalyser getCurrentStatement() {
            return null;
        }

        @Override
        public Location getLocation() {
            return evaluationContext.getLocation();
        }

        @Override
        public Location getLocation(Expression expression) {
            return evaluationContext.getLocation(expression);
        }

        @Override
        public Primitives getPrimitives() {
            return evaluationContext.getPrimitives();
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext dropConditionManager() {
            ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
            return new EvaluationContextImpl(this, cm);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            Set<Variable> conditionIsDelayed = isDelayedSet(condition);
            return new EvaluationContextImpl(this,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, conditionIsDelayed));
        }

        @Override
        public EvaluationContext childState(Expression state) {
            Set<Variable> stateIsDelayed = isDelayedSet(state);
            return new EvaluationContextImpl(this, conditionManager.addState(state, stateIsDelayed));
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            ensureVariableIsKnown(variable);
            return new VariableExpression(variable);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return evaluationContext.getAnalyserContext();
        }

        @Override
        public Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
            return evaluationContext.getParameterAnalyses(methodInfo);
        }

        @Override
        public int getProperty(Expression value, VariableProperty variableProperty, boolean duringEvaluation, boolean ignoreStateInConditionManager) {
            return evaluationContext.getProperty(value, variableProperty, duringEvaluation, ignoreStateInConditionManager);
        }

        @Override
        public int getProperty(Variable variable, VariableProperty variableProperty) {
            ensureVariableIsKnown(variable);
            return variableProperty.falseValue; // FIXME
        }

        @Override
        public int getPropertyFromPreviousOrInitial(Variable variable, VariableProperty variableProperty, int statementTime) {
            ensureVariableIsKnown(variable);
            return variableProperty.falseValue; // FIXME
        }

        @Override
        public boolean notNullAccordingToConditionManager(Variable variable) {
            return notNullAccordingToConditionManager(variable, fr -> {
                throw new UnsupportedOperationException("there should be no local copies of field references!");
            });
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            ensureVariableIsKnown(variable);
            return LinkedVariables.EMPTY;
        }

        @Override
        public Map<VariableProperty, Integer> getValueProperties(Expression value) {
            return evaluationContext.getValueProperties(value);
        }

        @Override
        public Map<VariableProperty, Integer> getValueProperties(Expression value, boolean ignoreConditionInConditionManager) {
            return evaluationContext.getValueProperties(value, ignoreConditionInConditionManager);
        }

        // FIXME nullable
        @Override
        public NewObject currentInstance(Variable variable, int statementTime) {
            ensureVariableIsKnown(variable);
            return NewObject.forInlinedMethod(evaluationContext.getPrimitives(), evaluationContext.newObjectIdentifier(),
                    variable.parameterizedType(), MultiLevel.NULLABLE);
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return evaluationContext.disableEvaluationOfMethodCallsUsingCompanionMethods();
        }

        @Override
        public int getInitialStatementTime() {
            return evaluationContext.getInitialStatementTime();
        }

        @Override
        public int getFinalStatementTime() {
            return evaluationContext.getFinalStatementTime();
        }

        @Override
        public boolean allowedToIncrementStatementTime() {
            return evaluationContext.allowedToIncrementStatementTime();
        }

        @Override
        public Expression replaceLocalVariables(Expression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression acceptAndTranslatePrecondition(Expression rest) {
            return evaluationContext.acceptAndTranslatePrecondition(rest);
        }

        @Override
        public boolean isPresent(Variable variable) {
            return variablesOfExpression.contains(variable);
        }

        @Override
        public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
            return evaluationContext.getLocalPrimaryTypeAnalysers();
        }

        @Override
        public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
            return evaluationContext.localVariableStream();
        }

        @Override
        public MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo) {
            return evaluationContext.findMethodAnalysisOfLambda(methodInfo);
        }

        @Override
        public LinkedVariables getStaticallyAssignedVariables(Variable variable, int statementTime) {
            return evaluationContext.getStaticallyAssignedVariables(variable, statementTime);
        }

        @Override
        public boolean variableIsDelayed(Variable variable) {
            return false; // nothing can be delayed here
        }

        @Override
        public boolean isDelayed(Expression expression) {
            return false; // nothing can be delayed here
        }

        @Override
        public Set<Variable> isDelayedSet(Expression expression) {
            return null; // nothing can be delayed here
        }

        @Override
        public boolean isNotDelayed(Expression expression) {
            return true; // nothing can be delayed here
        }

        @Override
        public String newObjectIdentifier() {
            return evaluationContext.newObjectIdentifier();
        }

        @Override
        public This currentThis() {
            return evaluationContext.currentThis();
        }

        @Override
        public Boolean isCurrentlyLinkedToField(Expression objectValue) {
            return evaluationContext.isCurrentlyLinkedToField(objectValue);
        }

        @Override
        public boolean cannotBeModified(Expression value) {
            return evaluationContext.cannotBeModified(value);
        }

        @Override
        public MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
            return evaluationContext.concreteMethod(variable, methodInfo);
        }

        @Override
        public String statementIndex() {
            return evaluationContext.statementIndex();
        }

        @Override
        public boolean firstAssignmentOfFieldInConstructor(Variable variable) {
            return evaluationContext.firstAssignmentOfFieldInConstructor(variable);
        }

        @Override
        public boolean hasBeenAssigned(Variable variable) {
            return evaluationContext.hasBeenAssigned(variable);
        }

        @Override
        public boolean foundDelay(String where, String delayFqn) {
            return evaluationContext.foundDelay(where, delayFqn);
        }

        @Override
        public boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
            return evaluationContext.translatedDelay(where, delayFromFqn, newDelayFqn);
        }

        @Override
        public boolean createDelay(String where, String delayFqn) {
            return evaluationContext.createDelay(where, delayFqn);
        }

        @Override
        public Stream<DelayDebugNode> streamNodes() {
            return Stream.of();
        }
    }
}
