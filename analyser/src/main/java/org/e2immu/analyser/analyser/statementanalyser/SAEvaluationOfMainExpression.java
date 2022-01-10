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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

record SAEvaluationOfMainExpression(StatementAnalysis statementAnalysis,
                                    SAApply apply,
                                    StatementAnalyser statementAnalyser,
                                    SetOnce<List<PrimaryTypeAnalyser>> localAnalysers) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAEvaluationOfMainExpression.class);

    private MethodInfo methodInfo() {
        return statementAnalysis.methodAnalysis().getMethodInfo();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    private String index() {
        return statementAnalysis.index();
    }
    /*
    We cannot yet set Linked1Variables in VIC.copy(), because the dependency graph is involved so
    "notReadInThisStatement" is not accurate. But unless an actual "delay" is set, there really is
    no involvement.
     */

    AnalysisStatus evaluationOfMainExpression(StatementAnalyserSharedState sharedState) {
        List<Expression> expressionsFromInitAndUpdate =
                new SAInitializersAndUpdaters(statementAnalysis)
                        .initializersAndUpdaters(sharedState.forwardAnalysisInfo(), sharedState.evaluationContext());
        CausesOfDelay causes = ((StatementAnalysisImpl) statementAnalysis).localVariablesInLoop();
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        AnalysisStatus analysisStatus = AnalysisStatus.of(causes);

        Structure structure = statementAnalysis.statement().getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            // try-statement has no main expression, and it may not have initializers; break; continue; ...
            if (statementAnalysis.stateData().valueOfExpression.isVariable()) {
                setFinalAllowEquals(statementAnalysis.stateData().valueOfExpression, EmptyExpression.EMPTY_EXPRESSION);
            }
            if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData().copyTimeAfterExecutionFromInitialTime();
            }
            if (statementAnalysis.statement() instanceof BreakStatement breakStatement) {
                if (statementAnalysis.parent().statement() instanceof SwitchStatementOldStyle) {
                    return analysisStatus;
                }
                StatementAnalysisImpl.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
                Expression state = sharedState.localConditionManager().stateUpTo(sharedState.evaluationContext(), correspondingLoop.steps());
                correspondingLoop.statementAnalysis().stateData().addStateOfInterrupt(index(), state, state.isDelayed());
                if (state.isDelayed()) return state.causesOfDelay();
            } else if (statement() instanceof LocalClassDeclaration localClassDeclaration) {
                EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext());
                PrimaryTypeAnalyser primaryTypeAnalyser =
                        localAnalysers.get().stream()
                                .filter(pta -> pta.containsPrimaryType(localClassDeclaration.typeInfo))
                                .findFirst().orElseThrow();
                builder.markVariablesFromPrimaryTypeAnalyser(primaryTypeAnalyser);
                return apply.apply(sharedState, builder.build(), localAnalysers.get()).combinedStatus();
            } else if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation eci) {
                // empty parameters: this(); or super();
                Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, null);
                if (!assignments.isBooleanConstant()) {
                    EvaluationResult result = assignments.evaluate(sharedState.evaluationContext(), structure.forwardEvaluationInfo());
                    AnalysisStatus applyResult = apply.apply(sharedState, result, localAnalysers.get()).combinedStatus();
                    return applyResult.combine(analysisStatus);
                }
            }
            return analysisStatus;
        }

        try {
            if (structure.expression() != EmptyExpression.EMPTY_EXPRESSION) {
                List<Assignment> patterns = new SAPatternVariable(statementAnalysis)
                        .patternVariables(sharedState.evaluationContext(), structure.expression());
                expressionsFromInitAndUpdate.addAll(patterns);
                expressionsFromInitAndUpdate.add(structure.expression());
            }
            // Too dangerous to use CommaExpression.comma, because it filters out constants etc.!
            Expression toEvaluate = expressionsFromInitAndUpdate.size() == 1 ? expressionsFromInitAndUpdate.get(0) :
                    new CommaExpression(expressionsFromInitAndUpdate);
            EvaluationResult result;
            if (statementAnalysis.statement() instanceof ReturnStatement) {
                assert structure.expression() != EmptyExpression.EMPTY_EXPRESSION;
                result = createAndEvaluateReturnStatement(sharedState);
            } else {
                result = toEvaluate.evaluate(sharedState.evaluationContext(), structure.forwardEvaluationInfo());
            }
            if (statementAnalysis.statement() instanceof ThrowStatement) {
                if (methodInfo().hasReturnValue()) {
                    result = modifyReturnValueRemoveConditionBasedOnState(sharedState, result);
                }
            }
            if (statementAnalysis.statement() instanceof AssertStatement) {
                result = handleNotNullClausesInAssertStatement(sharedState.evaluationContext(), result);
            }
            if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
                statementAnalysis.flowData().setTimeAfterEvaluation(result.statementTime(), index());
            }
            ApplyStatusAndEnnStatus applyResult = apply.apply(sharedState, result, localAnalysers.get());
            AnalysisStatus statusPost = AnalysisStatus.of(applyResult.status().merge(analysisStatus.causesOfDelay()));
            CausesOfDelay ennStatus = applyResult.ennStatus();

            if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation eci) {
                Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, result);
                if (!assignments.isBooleanConstant()) {
                    result = assignments.evaluate(sharedState.evaluationContext(), structure.forwardEvaluationInfo());
                    ApplyStatusAndEnnStatus assignmentResult = apply.apply(sharedState, result, localAnalysers.get());
                    statusPost = assignmentResult.status().merge(analysisStatus.causesOfDelay());
                    ennStatus = applyResult.ennStatus().merge(assignmentResult.ennStatus());
                }
            }

            Expression value = result.value();
            assert value != null; // EmptyExpression in case there really is no value
            boolean valueIsDelayed = value.isDelayed() || statusPost != DONE;

            if (!valueIsDelayed && (statementAnalysis.statement() instanceof IfElseStatement ||
                    statementAnalysis.statement() instanceof AssertStatement)) {
                value = eval_IfElse_Assert(sharedState, value);
            } else if (!valueIsDelayed && statementAnalysis.statement() instanceof HasSwitchLabels switchStatement) {
                eval_Switch(sharedState, value, switchStatement);
            }

            // the value can be delayed even if it is "true", for example (Basics_3)
            // see Precondition_3 for an example where different values arise, because preconditions kick in
            boolean valueIsDelayed2 = value.isDelayed() || statusPost != DONE;
            statementAnalysis.stateData().setValueOfExpression(value, valueIsDelayed2);

            if (ennStatus.isDelayed()) {
                log(DELAYED, "Delaying statement {} in {} because of external not null/external immutable: {}",
                        index(), methodInfo().fullyQualifiedName, ennStatus);
            }
            return AnalysisStatus.of(ennStatus.merge(statusPost.causesOfDelay()));
        } catch (Throwable rte) {
            LOGGER.warn("Failed to evaluate main expression in statement {}", statementAnalysis.index());
            throw rte;
        }
    }


    /*
    fixme: this works in simpler situations, but does not when (much) more complex.

     */
    private EvaluationResult modifyReturnValueRemoveConditionBasedOnState(StatementAnalyserSharedState sharedState,
                                                                          EvaluationResult result) {
        if (sharedState.previous() == null) return result; // first statement of block, no need to change
        ReturnVariable returnVariable = new ReturnVariable(methodInfo());
        VariableInfo vi = sharedState.previous().findOrThrow(returnVariable);
        if (!vi.getValue().isReturnValue()) {
            // remove all return_value parts
            Expression newValue = vi.getValue().removeAllReturnValueParts();
            EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.evaluationContext()).compose(result);
            Assignment assignment = new Assignment(statementAnalysis.primitives(),
                    new VariableExpression(returnVariable), newValue);
            EvaluationResult assRes = assignment.evaluate(sharedState.evaluationContext(), ForwardEvaluationInfo.DEFAULT);
            builder.compose(assRes);
            return builder.build();
        }
        return result;
    }

    private EvaluationResult handleNotNullClausesInAssertStatement(EvaluationContext evaluationContext,
                                                                   EvaluationResult evaluationResult) {
        Expression expression = evaluationResult.getExpression();
        Filter.FilterResult<ParameterInfo> result = SAHelper.moveConditionToParameter(evaluationContext, expression);
        if (result != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            boolean changes = false;
            for (Map.Entry<ParameterInfo, Expression> e : result.accepted().entrySet()) {
                boolean isNotNull = e.getValue().equalsNotNull();
                Variable notNullVariable = e.getKey();
                log(ANALYSER, "Found parameter (not)null ({}) assertion, {}", isNotNull, notNullVariable.simpleName());
                if (isNotNull) {
                    builder.setProperty(notNullVariable, CONTEXT_NOT_NULL, MultiLevel.EFFECTIVELY_NOT_NULL_DV);
                    changes = true;
                }
            }
            if (changes) {
                return builder.setExpression(evaluationResult.getExpression()).compose(evaluationResult).build();
            }
        }
        return evaluationResult;
    }

    private Expression replaceExplicitConstructorInvocation(StatementAnalyserSharedState sharedState,
                                                            ExplicitConstructorInvocation eci,
                                                            EvaluationResult result) {
         /* structure.updaters contains all the parameter values
               expressionsToEvaluate should contain assignments for each instance field, as found in the last statement of the
               explicit method
             */
        AnalyserContext analyserContext = sharedState.evaluationContext().getAnalyserContext();

        MethodAnalyser methodAnalyser = analyserContext.getMethodAnalyser(eci.methodInfo);
        int n = eci.methodInfo.methodInspection.get().getParameters().size();
        EvaluationResult.Builder builder = new EvaluationResult.Builder();
        Map<Expression, Expression> translation = new HashMap<>();
        if (result != null && n > 0) {
            int i = 0;
            List<Expression> storedValues = n == 1 ? List.of(result.value()) : result.storedValues();
            for (Expression parameterExpression : storedValues) {
                ParameterInfo parameterInfo = eci.methodInfo.methodInspection.get().getParameters().get(i);
                translation.put(new VariableExpression(parameterInfo), parameterExpression);
                i++;
            }
        }
        List<Expression> assignments = new ArrayList<>();
        for (FieldInfo fieldInfo : methodInfo().typeInfo.visibleFields(analyserContext)) {
            if (!fieldInfo.isStatic(analyserContext)) {
                for (VariableInfo variableInfo : methodAnalyser.getFieldAsVariable(fieldInfo)) {
                    if (variableInfo.isAssigned()) {
                        EvaluationResult translated = variableInfo.getValue()
                                .reEvaluate(sharedState.evaluationContext(), translation);
                        Assignment assignment = new Assignment(Identifier.generate(),
                                statementAnalysis.primitives(),
                                new VariableExpression(new FieldReference(analyserContext, fieldInfo)),
                                translated.value(), null, null, false);
                        builder.compose(translated);
                        assignments.add(assignment);
                    }
                }
            }
        }
        return CommaExpression.comma(sharedState.evaluationContext(), assignments);
    }

    /*
      modify the value of the return variable according to the evaluation result and the current state

      consider
      if(x) return a;
      return b;

      after the if, the state is !x, and the return variable has the value x?a:<return>
      we should not immediately overwrite, but take the existing return value into account, and return x?a:b

      See Eg. Warnings_5, ConditionalChecks_4
   */

    private EvaluationResult createAndEvaluateReturnStatement(StatementAnalyserSharedState sharedState) {
        assert methodInfo().hasReturnValue();
        Structure structure = statementAnalysis.statement().getStructure();
        ConditionManager localConditionManager = sharedState.localConditionManager();
        ReturnVariable returnVariable = new ReturnVariable(methodInfo());
        Expression currentReturnValue = statementAnalysis.initialValueOfReturnVariable(returnVariable);

        EvaluationContext evaluationContext;
        Expression toEvaluate;
        if (localConditionManager.state().isBoolValueTrue() || currentReturnValue instanceof UnknownExpression) {
            // default situation
            toEvaluate = structure.expression();
            evaluationContext = sharedState.evaluationContext();
        } else {
            evaluationContext = new SAEvaluationContext(
                    statementAnalysis, apply().myMethodAnalyser(), statementAnalyser,
                    sharedState.evaluationContext().getAnalyserContext(), localAnalysers,
                    sharedState.evaluationContext().getIteration(),
                    localConditionManager.withoutState(statementAnalysis.primitives()), sharedState.evaluationContext().getClosure());
            if (methodInfo().returnType().equals(statementAnalysis.primitives().booleanParameterizedType())) {
                // state, boolean; evaluation of And will add clauses to the context one by one
                toEvaluate = And.and(evaluationContext, localConditionManager.state(), structure.expression());
            } else {
                // state, not boolean
                AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
                InlineConditional inlineConditional = new InlineConditional(Identifier.generate(),
                        analyserContext, localConditionManager.state(), structure.expression(), currentReturnValue);
                toEvaluate = inlineConditional.optimise(evaluationContext);
            }
        }
        Assignment assignment = new Assignment(statementAnalysis.primitives(),
                new VariableExpression(new ReturnVariable(methodInfo())), toEvaluate);
        return assignment.evaluate(evaluationContext, structure.forwardEvaluationInfo());
    }

    /*
    goal: raise errors, exclude branches, etc.
     */
    private void eval_Switch(StatementAnalyserSharedState sharedState, Expression switchExpression, HasSwitchLabels switchStatement) {
        assert switchExpression != null;
        List<String> never = new ArrayList<>();
        List<String> always = new ArrayList<>();
        switchStatement.labels().forEach(label -> {
            Expression labelEqualsSwitchExpression = Equals.equals(sharedState.evaluationContext(), label, switchExpression);
            Expression evaluated = sharedState.localConditionManager().evaluate(sharedState.evaluationContext(),
                    labelEqualsSwitchExpression);
            if (evaluated.isBoolValueTrue()) {
                always.add(label.toString());
            } else if (evaluated.isBoolValueFalse()) {
                never.add(label.toString());
            }
        });
        // we could have any combination of the three variables

        if (!never.isEmpty() || !always.isEmpty()) {
            String msg = !always.isEmpty() ? "Is always reached: " + String.join("; ", always) :
                    "Is never reached: " + String.join("; ", never);
            statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(),
                    Message.Label.TRIVIAL_CASES_IN_SWITCH, msg));
        }
    }

    private Expression eval_IfElse_Assert(StatementAnalyserSharedState sharedState, Expression value) {
        assert value != null;

        Expression evaluated = sharedState.localConditionManager().evaluate(sharedState.evaluationContext(), value);

        if (evaluated.isConstant()) {
            Message.Label message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData().blocks.get();
            if (statementAnalysis.statement() instanceof IfElseStatement) {
                message = Message.Label.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        Message msg = Message.newMessage(new LocationImpl(methodInfo(),
                                        firstStatement.index(), firstStatement.statement().getIdentifier()),
                                Message.Label.UNREACHABLE_STATEMENT);
                        // let's add it to us, rather than to this unreachable statement
                        statementAnalysis.ensure(msg);
                    }
                    // guaranteed to be reached in block is always "ALWAYS" because it is the first statement
                    setExecutionOfSubBlock(firstStatement, isTrue ? FlowData.ALWAYS : FlowData.NEVER);
                });
                if (blocks.size() == 2) {
                    blocks.get(1).ifPresent(firstStatement -> {
                        boolean isTrue = evaluated.isBoolValueTrue();
                        if (isTrue) {
                            Message msg = Message.newMessage(new LocationImpl(methodInfo(),
                                            firstStatement.index(), firstStatement.statement().getIdentifier()),
                                    Message.Label.UNREACHABLE_STATEMENT);
                            statementAnalysis.ensure(msg);
                        }
                        setExecutionOfSubBlock(firstStatement, isTrue ? FlowData.NEVER : FlowData.ALWAYS);
                    });
                }
            } else if (statementAnalysis.statement() instanceof AssertStatement) {
                boolean isTrue = evaluated.isBoolValueTrue();
                if (isTrue) {
                    message = Message.Label.ASSERT_EVALUATES_TO_CONSTANT_TRUE;
                } else {
                    message = Message.Label.ASSERT_EVALUATES_TO_CONSTANT_FALSE;
                    Optional<StatementAnalysis> next = statementAnalysis.navigationData().next.get();
                    if (next.isPresent()) {
                        StatementAnalysis nextAnalysis = next.get();
                        nextAnalysis.flowData().setGuaranteedToBeReached(FlowData.NEVER);
                        Message msg = Message.newMessage(new LocationImpl(methodInfo(), nextAnalysis.index(),
                                nextAnalysis.statement().getIdentifier()), Message.Label.UNREACHABLE_STATEMENT);
                        statementAnalysis.ensure(msg);
                    }
                }
            } else throw new UnsupportedOperationException();
            statementAnalysis.ensure(Message.newMessage(sharedState.evaluationContext().getLocation(), message));
            return evaluated;
        }
        return value;
    }

    private void setExecutionOfSubBlock(StatementAnalysis firstStatement, DV execution) {
        DV mine = statementAnalysis.flowData().getGuaranteedToBeReachedInMethod();
        DV combined;
        if (FlowData.ALWAYS.equals(mine)) combined = execution;
        else if (FlowData.NEVER.equals(mine)) combined = FlowData.NEVER;
        else if (FlowData.CONDITIONALLY.equals(mine)) combined = FlowData.CONDITIONALLY;
        else if (mine.isDelayed()) combined = mine.causesOfDelay().merge(execution.causesOfDelay());
        else throw new UnsupportedOperationException("Mine is " + mine);

        if (!firstStatement.flowData().getGuaranteedToBeReachedInMethod().equals(FlowData.NEVER) || !combined.equals(FlowData.CONDITIONALLY)) {
            firstStatement.flowData().setGuaranteedToBeReachedInMethod(combined);
        } // else: we'll keep NEVER
    }

}
