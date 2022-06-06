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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Set;

public interface Expression extends Element, Comparable<Expression> {
    int HARD_LIMIT_ON_COMPLEXITY = 1000;

    int getComplexity();

    @NotModified
    @NotNull
    ParameterizedType returnType();

    @NotModified
    @NotNull
    Precedence precedence();

    /**
     * Evaluate an expression
     *
     * @param context               rather than using an EvaluationContext, we use an EvaluationResult, for exactly one
     *                              purpose: in CommaExpressions, we can analyse in the context of the earlier expressions
     *                              in the list.
     * @param forwardEvaluationInfo information to be passed on in the forward direction
     * @return the independent evaluation result
     */
    @NotModified
    @NotNull
    EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo);

    @NotModified
    @NotNull1
    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    /*
    used to compute the variables linking a field and its scope. main point: does not include condition in InlineConditional
     */
    default List<Variable> variablesWithoutCondition() {
        return List.of();
    }

    @NotNull
    @Override
    default Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        throw new UnsupportedOperationException("all expressions need to have this implemented! " + getClass());
    }

    // ********************************

    int order();

    default boolean isNumeric() {
        return false;
    }

    boolean isReturnValue();

    @Override
    int compareTo(Expression v);

    default int internalCompareTo(Expression v) {
        return 0;
    }

    default boolean isConstant() {
        return false;
    }

    default Expression unwrapIfConstant() {
        return this;
    }

    // empty expressions are unknown! They should NOT appear in operations (binary, unary)
    default boolean isEmpty() {
        return false;
    }

    default boolean isUnknown() {
        return false;
    }

    default boolean isDiscreteType() {
        ParameterizedType type = returnType();
        return type != null && type.isDiscrete();
    }

    // only called from EvaluationContext.getProperty().
    // Use that method as the general way of obtaining a value for a property from a Value object
    // do NOT fall back on evaluationContext.getProperty(this, ...) because that'll be an infinite loop!

    @NotNull
    default DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        throw new UnsupportedOperationException("For type " + getClass() + ", property " + property);
    }

    @NotNull
    default LinkedVariables linkedVariables(EvaluationResult context) {
        return LinkedVariables.EMPTY;
    }

    /*
    Primarily used for method call a = b.method(c,d), to potentially content link b to c and or d.

    Method reference can do this too: set::add ~ t -> set.add(t), in context list.forEach(set::add)
    we need a content link from list to set (via the implicit t)
    TODO how do we implement the implicit parameter?
    TODO Lambda's
     */
    @NotNull
    default LinkedVariables linked1VariablesScope(EvaluationResult context) {
        return LinkedVariables.EMPTY;
    }

    boolean isNotNull();

    boolean isNull();

    boolean equalsNull();

    boolean equalsNotNull();

    default boolean isComputeProperties() {
        return true;
    }

    boolean isBoolValueTrue();

    boolean isBoolValueFalse();

    @NotNull
    default OutputBuilder outputInParenthesis(Qualification qualification, Precedence precedence, Expression expression) {
        if (precedence.greaterThan(expression.precedence())) {
            return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(expression.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
        }
        return expression.output(qualification);
    }

    boolean isInitialReturnExpression();

    boolean isBooleanConstant();

    /*
    important: returns null on non-boolean expressions containing a return value part
     */
    default Expression removeAllReturnValueParts(Primitives primitives) {
        return this;
    }

    // such as /*this.contains(s)&&AnnotatedAPI.isKnown(true)&&1==this.size()*/
    default boolean hasState() {
        return false;
    }

    @NotNull
    default Expression state() {
        throw new UnsupportedOperationException("Guarded by haveState();");
    }

    default boolean cannotHaveState() {
        return false;
    }

    @NotNull
    Expression stateTranslateThisTo(InspectionProvider inspectionProvider, FieldReference fieldReference);

    @NotNull
    Expression createDelayedValue(Identifier identifier, EvaluationResult context, CausesOfDelay causes);

    @NotNull
    default CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    // goes together with causesOfDelay()
    @NotNull
    default Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return this;
    }

    default boolean isDelayed() {
        return causesOfDelay().isDelayed();
    }

    default boolean isDone() {
        return causesOfDelay().isDone();
    }

    @NotNull1
    default Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        return Set.of(returnType());
    }

    default boolean isErased() {
        return false;
    }

    default boolean containsErasedExpressions() {
        if (isErased()) return true;
        return subElements().stream().anyMatch(e ->
                e instanceof Expression expression && expression.containsErasedExpressions());
    }

    @NotNull
    default Expression generify(EvaluationContext evaluationContext) {
        return this;
    }

    default boolean isNotYetAssigned() {
        return false;
    }

    default DV invertTrueFalse() {
        if (isDelayed()) return causesOfDelay();
        if (isBoolValueFalse()) return DV.TRUE_DV;
        return DV.FALSE_DV;
    }

    default Expression extractConditions(Primitives primitives) {
        return returnType().isBooleanOrBoxedBoolean() ? this : new BooleanConstant(primitives, true);
    }

    default Expression applyCondition(Expression newState) {
        return this;
    }

    default Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext,
                                                                     ParameterizedType parameterizedType) {
        return EvaluationContext.NO_LOOP_SOURCE_VARIABLES;
    }

    default Set<Variable> directAssignmentVariables() {
        return Set.of();
    }

    default DV hardCodedPropertyOrNull(Property property) {
        return null;
    }

    /*
    helps to make the conversion
     */
    default TypeInfo typeInfoOfReturnType() {
        return returnType().typeInfo;
    }
}
