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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.analyser.Property.CONTEXT_NOT_NULL;
import static org.e2immu.analyser.analyser.Property.MARK_CLEAR_INCREMENTAL;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;
import static org.e2immu.analyser.analyser.Stage.INITIAL;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

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
        try {
            CausesOfDelay merged = evaluationOfMainExpression0(sharedState);
            boolean progress = statementAnalysis.latestDelay(merged);
            return AnalysisStatus.of(merged).addProgress(progress);
        } catch (Throwable rte) {
            LOGGER.warn("Failed to evaluate main expression in statement {}", statementAnalysis.index());
            throw rte;
        }

    }

    private CausesOfDelay evaluationOfMainExpression0(StatementAnalyserSharedState sharedState) {
        List<Expression> expressionsFromInitAndUpdate = new SAInitializersAndUpdaters(statementAnalysis)
                .initializersAndUpdaters(sharedState.forwardAnalysisInfo(), sharedState.evaluationContext());
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        CausesOfDelay causes = ((StatementAnalysisImpl) statementAnalysis).localVariablesInLoop();

        Structure structure = statementAnalysis.statement().getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            return emptyExpression(sharedState, causes, structure);
        }

        if (structure.expression() != EmptyExpression.EMPTY_EXPRESSION) {
            List<Assignment> patterns = new SAPatternVariable(statementAnalysis)
                    .patternVariables(sharedState.evaluationContext(), structure.expression());
            expressionsFromInitAndUpdate.addAll(patterns);
            expressionsFromInitAndUpdate.add(structure.expression());
        }
        // Too dangerous to use CommaExpression.comma, because it filters out constants etc.!
        Expression toEvaluate = toEvaluate(expressionsFromInitAndUpdate);

        EvaluationResult result;
        if (statementAnalysis.statement() instanceof ReturnStatement) {
            assert structure.expression() != EmptyExpression.EMPTY_EXPRESSION;
            result = createAndEvaluateReturnStatement(sharedState, toEvaluate);
        } else {
            LOGGER.info("Eval it {} main {} in {}", sharedState.evaluationContext().getIteration(), index(), methodInfo().fullyQualifiedName);
            result = toEvaluate.evaluate(EvaluationResult.from(sharedState.evaluationContext()), structure.forwardEvaluationInfo());
        }
        if (statementAnalysis.statement() instanceof LoopStatement) {
            Range range = statementAnalysis.rangeData().getRange();
            if (range.isDelayed()) {
                statementAnalysis.rangeData().computeRange(statementAnalysis, result);
                statementAnalysis.ensureMessages(statementAnalysis.rangeData().messages());
            }
        }
        if (statementAnalysis.statement() instanceof ThrowStatement) {
            if (methodInfo().hasReturnValue()) {
                result = modifyReturnValueRemoveConditionBasedOnState(sharedState, result);
            }
        }
        if (statementAnalysis.statement() instanceof AssertStatement) {
            result = handleNotNullClausesInAssertStatement(sharedState.context(), result);
        }
        if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
            statementAnalysis.flowData().setTimeAfterEvaluation(result.statementTime(), index());
        }
        // we'll write a little later, in the second evaluation...
        boolean doNotWritePreconditionFromMethod = statement() instanceof ExplicitConstructorInvocation;
        ApplyStatusAndEnnStatus applyResult = apply.apply(sharedState, result, doNotWritePreconditionFromMethod);

        // post-process

        AnalysisStatus statusPost = AnalysisStatus.of(applyResult.status().merge(causes));
        CausesOfDelay ennStatus = applyResult.ennStatus();

        if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation eci) {
            // situation with parameters; this code is replicated below for the situation without params
            Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, result);
            if (assignments == null) {
                // force delay on subsequent statements; this is (eventually) handled by SAI.analyseAllStatementsInBlock
                CausesOfDelay eciDelay = DelayFactory.createDelay(new SimpleCause(statementAnalysis.location(EVALUATION), CauseOfDelay.Cause.ECI));
                statementAnalysis.stateData().setValueOfExpression(DelayedExpression.forECI(eci.identifier, eciDelay));
                return eciDelay;
            }
            if (!assignments.isBooleanConstant()) {
                LOGGER.debug("Assignment expressions: {}", assignments);
                EvaluationResult reResult = assignments.evaluate(EvaluationResult.from(sharedState.evaluationContext()), structure.forwardEvaluationInfo());
                ApplyStatusAndEnnStatus assignmentResult = apply.apply(sharedState, reResult, false);
                statusPost = assignmentResult.status().merge(causes);
                ennStatus = applyResult.ennStatus().merge(assignmentResult.ennStatus());
                result = reResult;
            }
        }
        if (ennStatus.isDelayed()) {
            LOGGER.debug("Delaying statement {} in {} because of external not null/external immutable: {}",
                    index(), methodInfo().fullyQualifiedName, ennStatus);
        }

        Expression value = result.value();
        assert value != null; // EmptyExpression in case there really is no value
        boolean valueIsDelayed = value.isDelayed() || statusPost != DONE;

        CausesOfDelay stateForLoop = CausesOfDelay.EMPTY;
        if (!valueIsDelayed && (statementAnalysis.statement() instanceof IfElseStatement ||
                statementAnalysis.statement() instanceof AssertStatement)) {
            value = eval_IfElse_Assert(sharedState, value);
            if (value.isDelayed()) {
                // for example, an if(...) inside a loop, when the loop's range is being computed
                stateForLoop = value.causesOfDelay();
            }
        } else if (!valueIsDelayed && statementAnalysis.statement() instanceof HasSwitchLabels switchStatement) {
            eval_Switch(sharedState, value, switchStatement);
        } else if (statementAnalysis.statement() instanceof ReturnStatement) {
            stateForLoop = addLoopReturnStatesToState(sharedState);
            Expression condition = sharedState.localConditionManager().condition();
            StatementAnalysisImpl.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(null);
            if (correspondingLoop != null &&
                    correspondingLoop.statementAnalysis().rangeData().getRange().generateErrorOnInterrupt(condition)) {
                statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(EVALUATION), Message.Label.INTERRUPT_IN_LOOP));
            }
        } else if (statement() instanceof ThrowStatement) {
            value = noReturnValue();
            // but, see also code above that changes the return variable's value; See SwitchExpression_4
        }

        // the value can be delayed even if it is "true", for example (Basics_3)
        // see Precondition_3 for an example where different values arise, because preconditions kick in
        if (statement() instanceof ExplicitConstructorInvocation) {
            value = UnknownExpression.forExplicitConstructorInvocation();
        }

        // this statement can never be fully correct, but it seems to do the job for now... preconditions may arrive late
        // and my cause delays in the evaluation after a number of iterations
        if (value.isDone() && statement() instanceof IfElseStatement && sharedState.localConditionManager().isDelayed()) {
            value = DelayedExpression.forState(sharedState.localConditionManager().getIdentifier(),
                    value.returnType(), LinkedVariables.EMPTY, sharedState.localConditionManager().causesOfDelay());
        }
        statementAnalysis.stateData().setValueOfExpression(value);

        return ennStatus.merge(statusPost.causesOfDelay()).merge(stateForLoop);
    }

    private CausesOfDelay emptyExpression(StatementAnalyserSharedState sharedState, CausesOfDelay causes, Structure structure) {
        // try-statement has no main expression, and it may not have initializers; break; continue; ...
        if (statementAnalysis.stateData().valueOfExpression.isVariable()) {
            setFinalAllowEquals(statementAnalysis.stateData().valueOfExpression, EmptyExpression.EMPTY_EXPRESSION);
        }
        if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
            statementAnalysis.flowData().copyTimeAfterExecutionFromInitialTime();
        }
        if (statementAnalysis.statement() instanceof BreakStatement breakStatement) {
            if (statementAnalysis.parent().statement() instanceof SwitchStatementOldStyle) {
                return causes;
            }
            StatementAnalysisImpl.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
            Expression state = sharedState.localConditionManager().stateUpTo(sharedState.context(), correspondingLoop.steps());
            correspondingLoop.statementAnalysis().stateData().addStateOfInterrupt(index(), state);
            if (state.isDelayed()) return state.causesOfDelay();
            Expression condition = sharedState.localConditionManager().condition();
            if (correspondingLoop.statementAnalysis().rangeData().getRange().generateErrorOnInterrupt(condition)) {
                statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(EVALUATION), Message.Label.INTERRUPT_IN_LOOP));
            }
        } else if (statement() instanceof LocalClassDeclaration) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.context());
            return apply.apply(sharedState, builder.build(), false).combinedStatus().causesOfDelay();
        } else if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation eci) {
            // empty parameters: this(); or super(); this code is replicated a bit higher for the situation of parameters
            Expression assignments = replaceExplicitConstructorInvocation(sharedState, eci, null);
            if (assignments == null) {
                CausesOfDelay eciDelay = DelayFactory.createDelay(statementAnalysis.location(EVALUATION), CauseOfDelay.Cause.ECI);
                statementAnalysis.stateData().setValueOfExpression(DelayedExpression.forECI(eci.identifier, eciDelay));
                return eciDelay;
            }
            if (!assignments.isBooleanConstant()) {
                EvaluationResult result = assignments.evaluate(EvaluationResult.from(sharedState.evaluationContext()),
                        structure.forwardEvaluationInfo());
                // FIXME clean up, AnalysisStatus -> Causes
                AnalysisStatus applyResult = apply.apply(sharedState, result, false).combinedStatus();
                return applyResult.causesOfDelay().merge(causes);
            }
        }
        return causes;
    }

    private Expression toEvaluate(List<Expression> expressionsFromInitAndUpdate) {
        if (expressionsFromInitAndUpdate.size() == 1) {
            Expression expression = expressionsFromInitAndUpdate.get(0);
            // we may have wrapped the expression with a comma expression marker...
            if (!(expression instanceof PropertyWrapper pw) || !pw.hasProperty(MARK_CLEAR_INCREMENTAL)) {
                return expression;
            }
        }
        return new CommaExpression(expressionsFromInitAndUpdate);
    }

    private Expression noReturnValue() {
        return UnknownExpression.forNoReturnValue(statement().getIdentifier(), methodInfo().returnType());
    }

    private CausesOfDelay addLoopReturnStatesToState(StatementAnalyserSharedState sharedState) {
        StatementAnalysis.FindLoopResult loop = statementAnalysis.findLoopByLabel(null);
        if (loop != null) {
            Expression state = sharedState.localConditionManager().stateUpTo(sharedState.context(), loop.steps());
            Expression notState = Negation.negate(sharedState.context(), state);
            loop.statementAnalysis().stateData().addStateOfReturnInLoop(index(), notState);
            if (state.isDelayed()) {
                // we'll have to come back
                CausesOfDelay stateForLoop = state.causesOfDelay();
                LOGGER.debug("Delaying statement {} in {} because of state propagation to loop",
                        index(), methodInfo().fullyQualifiedName);
                return stateForLoop;
            }
        }
        return CausesOfDelay.EMPTY;
    }


    /*
    fixme: this works in simpler situations, but does not when (much) more complex.

     */
    private EvaluationResult modifyReturnValueRemoveConditionBasedOnState(StatementAnalyserSharedState sharedState,
                                                                          EvaluationResult result) {
        if (sharedState.previous() == null) return result; // first statement of block, no need to change
        ReturnVariable returnVariable = new ReturnVariable(methodInfo());
        VariableInfo vi = sharedState.previous().findOrThrow(returnVariable);
        if (!vi.getValue().isReturnValue() && !vi.getValue().isDelayed()) {
            // remove all return_value parts
            Expression newValue = vi.getValue().removeAllReturnValueParts();
            EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.context()).compose(result);
            Assignment assignment = new Assignment(statementAnalysis.primitives(),
                    new VariableExpression(returnVariable), newValue);
            EvaluationResult assRes = assignment.evaluate(EvaluationResult.from(sharedState.evaluationContext()),
                    ForwardEvaluationInfo.DEFAULT);
            builder.compose(assRes);
            return builder.build();
        }
        return result;
    }

    private EvaluationResult handleNotNullClausesInAssertStatement(EvaluationResult context,
                                                                   EvaluationResult evaluationResult) {
        Expression expression = evaluationResult.getExpression();
        Filter.FilterResult<ParameterInfo> result = SAHelper.moveConditionToParameter(context, expression);
        if (result != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
            boolean changes = false;
            for (Map.Entry<ParameterInfo, Expression> e : result.accepted().entrySet()) {
                boolean isNotNull = e.getValue().equalsNotNull();
                Variable notNullVariable = e.getKey();
                LOGGER.debug("Found parameter (not)null ({}) assertion, {}", isNotNull, notNullVariable.simpleName());
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

        MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(eci.methodInfo);
        assert methodAnalysis != null : "Cannot find method analysis for " + eci.methodInfo.fullyQualifiedName;
        if (!methodAnalysis.hasBeenAnalysedUpToIteration0() && methodAnalysis.isComputed()) {
            assert sharedState.evaluationContext().getIteration() == 0 : "In iteration " + sharedState.evaluationContext().getIteration();
            /* if the method has not gone through 1st iteration of analysis, we need to wait.
             this should never be a circular wait because we're talking a strict constructor hierarchy
             the delay has to have an effect on CM in the next iterations, because introducing the assignments here
             will cause delays (see LoopStatement constructor, where "expression" appears in statement 1, iteration 1)
             because of the 'super' call to StatementWithExpression which comes after LoopStatement.
             Without method analysis we have no idea which variables will be affected
             */
            LOGGER.debug("Cannot continue with ECI because first iteration of this/super {} has not been analysed yet",
                    eci.methodInfo.fullyQualifiedName);
            return null;
        }

        int n = eci.methodInfo.methodInspection.get().getParameters().size();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.context());
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
                for (VariableInfo variableInfo : methodAnalysis.getFieldAsVariable(fieldInfo)) {
                    if (variableInfo.isAssigned()) {
                        EvaluationResult translated = variableInfo.getValue()
                                .reEvaluate(EvaluationResult.from(sharedState.evaluationContext()), translation);
                        Assignment assignment = new Assignment(Identifier.generate("assignment eci"),
                                statementAnalysis.primitives(),
                                new VariableExpression(new FieldReference(analyserContext, fieldInfo)),
                                translated.value(), null, null, false);
                        builder.compose(translated);
                        assignments.add(assignment);
                    }
                }
            }
        }
        return CommaExpression.comma(sharedState.context(), assignments);
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

    private EvaluationResult createAndEvaluateReturnStatement(StatementAnalyserSharedState sharedState, Expression expression) {
        assert methodInfo().hasReturnValue();
        Structure structure = statementAnalysis.statement().getStructure();
        ConditionManager localConditionManager = sharedState.localConditionManager();
        ReturnVariable returnVariable = new ReturnVariable(methodInfo());
        Expression currentReturnValue = statementAnalysis.initialValueOfReturnVariable(returnVariable);

        EvaluationContext evaluationContext;
        Expression toEvaluate;
        ForwardEvaluationInfo forwardEvaluationInfo;
        if (localConditionManager.state().isBoolValueTrue() || currentReturnValue instanceof UnknownExpression) {
            // default situation
            toEvaluate = expression;
            evaluationContext = sharedState.evaluationContext();
            forwardEvaluationInfo = structure.forwardEvaluationInfo();
        } else {
            // substitute <return value> for the current expression, rather than rely on condition manager in eval context
            Expression returnExpression = UnknownExpression.forReturnVariable(methodInfo().identifier,
                    returnVariable.returnType);
            TranslationMap tm = new TranslationMapImpl.Builder().put(returnExpression, expression).build();
            toEvaluate = currentReturnValue.translate(sharedState.evaluationContext().getAnalyserContext(), tm);
            evaluationContext = sharedState.evaluationContext().dropConditionManager();
            forwardEvaluationInfo = structure.forwardEvaluationInfo().copyDoNotComplainInlineConditional();
        }
        Assignment assignment = new Assignment(statementAnalysis.primitives(),
                new VariableExpression(new ReturnVariable(methodInfo())), toEvaluate);
        return assignment.evaluate(EvaluationResult.from(evaluationContext), forwardEvaluationInfo);
    }

    /*
    goal: raise errors, exclude branches, etc.
     */
    private void eval_Switch(StatementAnalyserSharedState sharedState, Expression switchExpression, HasSwitchLabels switchStatement) {
        assert switchExpression != null;
        List<String> never = new ArrayList<>();
        List<String> always = new ArrayList<>();
        switchStatement.labels().forEach(label -> {
            Expression labelEqualsSwitchExpression = Equals.equals(sharedState.context(), label, switchExpression);
            Expression evaluated = sharedState.localConditionManager().evaluate(sharedState.context(),
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
            statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(EVALUATION),
                    Message.Label.TRIVIAL_CASES_IN_SWITCH, msg));
        }
    }

    private Expression eval_IfElse_Assert(StatementAnalyserSharedState sharedState, Expression value) {
        assert value != null;

        if (sharedState.localConditionManager().isDelayed()) {
            CausesOfDelay causes = sharedState.localConditionManager().causesOfDelay();
            if (causes.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY)) {
                LOGGER.debug("Break init delay -- not delaying");
            } else {
                Identifier identifier = statement().getStructure().expression().getIdentifier();
                return DelayedExpression.forCondition(identifier, statementAnalysis.primitives().booleanParameterizedType(),
                        LinkedVariables.delayedEmpty(causes), causes);
            }
        }

        Expression evaluated = sharedState.localConditionManager().evaluate(sharedState.context(), value);

        if (evaluated.isConstant()) {
            Message.Label message;
            List<Optional<StatementAnalysis>> blocks = statementAnalysis.navigationData().blocks.get();
            if (statementAnalysis.statement() instanceof IfElseStatement) {
                message = Message.Label.CONDITION_EVALUATES_TO_CONSTANT;

                blocks.get(0).ifPresent(firstStatement -> {
                    boolean isTrue = evaluated.isBoolValueTrue();
                    if (!isTrue) {
                        Message msg = Message.newMessage(new LocationImpl(methodInfo(),
                                        firstStatement.index() + INITIAL,
                                        firstStatement.statement().getIdentifier()),
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
                                            firstStatement.index() + INITIAL,
                                            firstStatement.statement().getIdentifier()),
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
                        Message msg = Message.newMessage(new LocationImpl(methodInfo(),
                                nextAnalysis.index() + INITIAL,
                                nextAnalysis.statement().getIdentifier()), Message.Label.UNREACHABLE_STATEMENT);
                        statementAnalysis.ensure(msg);
                    }
                }
            } else throw new UnsupportedOperationException();
            statementAnalysis.ensure(Message.newMessage(sharedState.evaluationContext().getLocation(EVALUATION), message));
            return evaluated;
        }
        return value;
    }

    private void setExecutionOfSubBlock(StatementAnalysis firstStatement, DV execution) {
        DV mine = statementAnalysis.flowData().getGuaranteedToBeReachedInMethod();
        DV combined;
        if (FlowData.ALWAYS.equals(mine)) combined = execution;
        else if (FlowData.NEVER.equals(mine)) combined = FlowData.NEVER;
        else if (FlowData.CONDITIONALLY.equals(mine)) combined = FlowData.CONDITIONALLY.min(execution);
        else if (mine.isDelayed()) combined = mine.causesOfDelay().merge(execution.causesOfDelay());
        else throw new UnsupportedOperationException("Mine is " + mine);

        if (!firstStatement.flowData().getGuaranteedToBeReachedInMethod().equals(FlowData.NEVER) || !combined.equals(FlowData.CONDITIONALLY)) {
            firstStatement.flowData().setGuaranteedToBeReachedInMethod(combined);
        } // else: we'll keep NEVER
    }

}
