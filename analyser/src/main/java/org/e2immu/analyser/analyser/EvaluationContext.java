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

package org.e2immu.analyser.analyser;


import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;

/**
 * Defaults because of tests
 */
public interface EvaluationContext {

    int limitOnComplexity();

    int getIteration();

    @NotNull
    TypeInfo getCurrentType();

    // convenient in breakpoints while debugging
    @SuppressWarnings("unused")
    String safeMethodName();

    MethodAnalyser getCurrentMethod();

    StatementAnalyser getCurrentStatement();

    boolean haveCurrentStatement();

    Location getLocation(Stage level);

    Location getEvaluationLocation(Identifier identifier);

    Primitives getPrimitives();

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    EvaluationContext child(Expression condition, Set<Variable> conditionVariables);

    EvaluationContext dropConditionManager();

    EvaluationContext child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods);

    EvaluationContext childState(Expression state, Set<Variable> stateVariables);

    EvaluationContext updateStatementTime(int statementTime);

    int getCurrentStatementTime();

    /*
     This  implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
     companion analyser, that would be chicken-and-egg).
    */
    Expression currentValue(Variable variable);

    Expression currentValue(Variable variable,
                            Expression scopeValue,
                            Expression indexValue,
                            Identifier identifier,
                            ForwardEvaluationInfo forwardEvaluationInfo);

    AnalyserContext getAnalyserContext();

    Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo);

    // will have a more performant implementation in SAEvaluationContext,
    // because getVariableProperty is pretty expensive
    Properties getProperties(IsMyself isMyself,
                             Expression value,
                             List<Property> properties,
                             boolean duringEvaluation,
                             boolean ignoreStateInConditionManager);

    /**
     * IMPROVE move to evaluationResult?
     *
     * @param duringEvaluation true when this method is called during the EVAL process. It then reads variable's properties from the
     *                         INIT side, rather than current. Current may be MERGE, which is definitely wrong during the EVAL process.
     */
    DV getProperty(Expression value, Property property, boolean duringEvaluation, boolean ignoreStateInConditionManager);

    /*
     assumes that currentValue has been queried before!
     */
    DV getProperty(Variable variable, Property property);

    DV getPropertyFromPreviousOrInitial(Variable variable, Property property);

    ConditionManager getConditionManager();

    DV isNotNull0(Expression value, boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo);

    DV notNullAccordingToConditionManager(Variable variable);

    DV notNullAccordingToConditionManager(Expression expression);

    LinkedVariables linkedVariables(Variable variable);

    // do not change order: compatible with SingleDelay
    List<Property> VALUE_PROPERTIES = List.of(CONTAINER, IDENTITY, IGNORE_MODIFICATIONS, IMMUTABLE, INDEPENDENT, NOT_NULL_EXPRESSION);

    Properties PRIMITIVE_VALUE_PROPERTIES = Properties.of(Map.of(NOT_NULL_EXPRESSION, MultiLevel.EFFECTIVELY_NOT_NULL_DV,
            IMMUTABLE, MultiLevel.EFFECTIVELY_IMMUTABLE_DV,
            INDEPENDENT, MultiLevel.INDEPENDENT_DV,
            CONTAINER, MultiLevel.CONTAINER_DV,
            IDENTITY, IDENTITY.falseDv,
            IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS.falseDv));

    Properties getValueProperties(Expression value);

    Properties getValueProperties(ParameterizedType formalType, Expression value);

    // NOTE: when the value is a VariableExpression pointing to a variable field, variable in loop or anything that
    // causes findForReading to generate a new VariableInfoImpl, this loop will cause 5x the same logic to be applied.
    // should be able to do better/faster.
    Properties getValueProperties(ParameterizedType formalType, Expression value, boolean ignoreConditionInConditionManager);

    Properties valuePropertiesOfFormalType(ParameterizedType formalType);

    Properties valuePropertiesOfFormalType(ParameterizedType formalType, DV notNullExpression);

    Properties valuePropertiesOfNullConstant(IsMyself isMyself, ParameterizedType formalType);

    boolean disableEvaluationOfMethodCallsUsingCompanionMethods();

    EvaluationContext getClosure();

    int getInitialStatementTime();

    int getFinalStatementTime();

    boolean allowedToIncrementStatementTime();

    Expression replaceLocalVariables(Expression expression);

    Expression acceptAndTranslatePrecondition(Identifier identifier, Expression rest);

    boolean isPresent(Variable variable);

    List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers();

    Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream();

    MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo);

    This currentThis();

    DV cannotBeModified(Expression value);

    MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo);

    String statementIndex();

    boolean firstAssignmentOfFieldInConstructor(Variable variable);

    boolean hasBeenAssigned(Variable variable);

    /*
    should we compute context immutable? not if we're a variable of the type itself
     */
    IsMyself isMyself(Variable variable);

    IsMyself isMyselfExcludeThis(Variable variable);

    IsMyself isMyself(ParameterizedType type);

    Properties ensureMyselfValueProperties(Properties existing);

    boolean inConstruction();

    /*
     Store_0 shows an example of a stack overflow going from the ConditionManager.absoluteState via And, Negation,
     Equals, EvaluationContext.isNotNull0, notNullAccordingToConditionManager, findIndividualNullInState and back to
     absoluteState... This method, only applied in Negation at the moment, prevents this infinite loop from occurring.
     */
    boolean preventAbsoluteStateComputation();

    EvaluationContext copyToPreventAbsoluteStateComputation();

    /**
     * @param variable   the variable in the nested type
     * @param nestedType the nested type, can be null in case of method references
     * @return true when we want to transfer properties from the nested type to the current type
     */
    boolean acceptForVariableAccessReport(Variable variable, TypeInfo nestedType);

    DependentVariable searchInEquivalenceGroupForLatestAssignment(DependentVariable variable,
                                                                  Expression arrayValue,
                                                                  Expression indexValue,
                                                                  ForwardEvaluationInfo forwardEvaluationInfo);

    // problem: definedInBlock() is only non-null after the first evaluation
    boolean isPatternVariableCreatedAt(Variable v, String index);

    Either<CausesOfDelay, Set<Variable>> NO_LOOP_SOURCE_VARIABLES = Either.right(Set.of());

    Either<CausesOfDelay, Set<Variable>> loopSourceVariables(Variable variable);

    Stream<Map.Entry<String, VariableInfoContainer>> variablesFromClosure();

    Properties getExternalProperties(Expression valueToWrite);

    BreakDelayLevel breakDelayLevel();

    /*
    modifications on immutable object...
     */
    boolean inConstructionOrInStaticWithRespectTo(TypeInfo typeInfo);

    int initialModificationTimeOrZero(Variable variable);

    // meant for computing method analyser, computing field analyser

    boolean hasState(Expression expression);

    Expression state(Expression expression);

    Expression getVariableValue(Variable myself,
                                Expression scopeValue,
                                Expression indexValue,
                                Identifier identifier,
                                VariableInfo variableInfo,
                                ForwardEvaluationInfo forwardEvaluationInfo);

    boolean delayStatementBecauseOfECI();

    int getDepth();

    Properties defaultValueProperties(ParameterizedType parameterizedType, DV valueForNotNullExpression);

    Properties defaultValueProperties(ParameterizedType parameterizedType);

    Properties defaultValueProperties(ParameterizedType parameterizedType, boolean writable);
}
