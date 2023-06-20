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
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.delay.ProgressWrapper;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.*;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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
            return evaluationOfMainExpression0(sharedState);
        } catch (Throwable rte) {
            LOGGER.warn("Failed to evaluate main expression in statement {}", statementAnalysis.index());
            throw rte;
        }

    }

    private AnalysisStatus evaluationOfMainExpression0(StatementAnalyserSharedState sharedState) {
        statementAnalysis.stateData().setAbsoluteState(sharedState.evaluationContext());

        List<Expression> expressionsFromInitAndUpdate = new SAInitializersAndUpdaters(statementAnalysis)
                .initializersAndUpdaters(sharedState.forwardAnalysisInfo(), sharedState.evaluationContext());
        /*
        if we're in a loop statement and there are delays (localVariablesAssignedInThisLoop not frozen)
        we have to come back!
         */
        CausesOfDelay causes = ((StatementAnalysisImpl) statementAnalysis).localVariablesInLoop();

        Statement statement = statementAnalysis.statement();
        Structure structure = statement.getStructure();
        if (structure.expression() == EmptyExpression.EMPTY_EXPRESSION && expressionsFromInitAndUpdate.isEmpty()) {
            CausesOfDelay absoluteStateDelays = statementAnalysis.stateData().getAbsoluteState().causesOfDelay();
            return emptyExpression(sharedState, causes.merge(absoluteStateDelays));
        }

        if (structure.expression() != EmptyExpression.EMPTY_EXPRESSION) {
            List<Assignment> patterns = new SAPatternVariable(statementAnalysis)
                    .patternVariables(sharedState.evaluationContext(), structure.expression());
            expressionsFromInitAndUpdate.addAll(patterns);
            expressionsFromInitAndUpdate.add(structure.expression());
        }
        // Too dangerous to use CommaExpression.comma, because it filters out constants etc.!
        Expression toEvaluate = toEvaluate(expressionsFromInitAndUpdate);
        EvaluationResult context = makeContext(sharedState.evaluationContext());


        LOGGER.info("Eval it {} main {} in {}", sharedState.evaluationContext().getIteration(), index(), methodInfo().fullyQualifiedName);
        ForwardEvaluationInfo forwardEvaluationInfo;
        if (statement instanceof ReturnStatement) {
            // code identical to snippet in Assignment.evaluate, to prepare for value evaluation
            ForwardEvaluationInfo.Builder fwdBuilder = new ForwardEvaluationInfo.Builder(structure.forwardEvaluationInfo())
                    .setAssignmentTarget(new ReturnVariable(methodInfo()));
            if (methodInfo().returnType().isPrimitiveExcludingVoid()) {
                fwdBuilder.setCnnNotNull();
            }
            forwardEvaluationInfo = fwdBuilder.build();
        } else {
            forwardEvaluationInfo = structure.forwardEvaluationInfo();
        }
        // here is a good breakpoint location, e.g. "4.0.1".equals(index())
        EvaluationResult result = toEvaluate.evaluate(context, forwardEvaluationInfo);

        if (statement instanceof ReturnStatement) {
            result = createAndEvaluateReturnStatement(sharedState.evaluationContext(), toEvaluate, result);
        } else if (statement instanceof LoopStatement) {
            Range range = statementAnalysis.rangeData().getRange();
            if (range.isDelayed()) {
                statementAnalysis.rangeData().computeRange(statementAnalysis, result);
                statementAnalysis.ensureMessages(statementAnalysis.rangeData().messages());
            }
        } else if (statement instanceof ThrowStatement) {
            if (methodInfo().hasReturnValue()) {
                result = modifyReturnValueRemoveConditionBasedOnState(sharedState, result);
            } else if(statementAnalysis.parent() == null) {
                /*
                 void method or constructor; top-level, so we don't reach SASubBlocks.assert/throws.
                 This can never be a pre- or post-condition, as it ALWAYS stops the method
                 */
                statementAnalysis.stateData().ensureEscapeNotInPreOrPostConditions();
            }
        } else if (statement instanceof AssertStatement) {
            result = handleNotNullClausesInAssertStatement(sharedState.context(), result);
        } else if (statement instanceof ExplicitConstructorInvocation) {
            result = result.filterChangeData(v -> !(v instanceof LocalVariableReference));
        }

        if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
            statementAnalysis.flowData().setTimeAfterEvaluation(result.statementTime(), index());
        }
        ApplyStatusAndEnnStatus applyResult = apply.apply(sharedState, result);

        // post-process

        ProgressAndDelay statusPost = applyResult.status().merge(causes);
        ProgressAndDelay ennStatus = applyResult.ennStatus();

        if (ennStatus.isDelayed()) {
            LOGGER.debug("Delaying statement {} in {} because of external not null/external immutable: {}",
                    index(), methodInfo().fullyQualifiedName, ennStatus);
        }

        Expression value = result.value();
        assert value != null; // EmptyExpression in case there really is no value

        CausesOfDelay stateForLoop = CausesOfDelay.EMPTY;
        if (value.isDone() && (statement instanceof IfElseStatement ||
                statement instanceof AssertStatement)) {
            value = eval_IfElse_Assert(sharedState, value);
            if (value.isDelayed()) {
                // for example, an if(...) inside a loop, when the loop's range is being computed
                stateForLoop = value.causesOfDelay();
            }
        } else if (value.isDone() && statement instanceof HasSwitchLabels switchStatement) {
            eval_Switch(sharedState, value, switchStatement);
        } else if (statement instanceof ReturnStatement) {
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
                    value.returnType(),
                    sharedState.localConditionManager().multiExpression(),
                    sharedState.localConditionManager().causesOfDelay());
        }
        statementAnalysis.stateData().setValueOfExpression(value);
        statementAnalysis.stateData().setEvaluatedExpressionCache(result.evaluatedExpressionCache());

        ProgressAndDelay endResult = ennStatus.combine(statusPost).merge(stateForLoop);
        return endResult.toAnalysisStatus();
    }

    /*
    context consists of "assignments" that mimic the condition manager, see VariableInLoop_1,
    VariableScope_11
     */
    private EvaluationResult makeContext(EvaluationContext evaluationContext) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        statementAnalysis.stateData().equalityAccordingToStateStream().forEach(e -> {
            VariableExpression ve = e.getKey();
            // if the variable expression is field$0, and we are in statement time 1, we cannot use this expression!
            // TODO is there an equivalent for loop variables?
            if (!(ve.getSuffix() instanceof VariableExpression.VariableField vf)
                    || vf.statementTime() == statementAnalysis.statementTime(EVALUATION)) {

                EvaluationResult context = EvaluationResult.from(evaluationContext);
                LinkedVariables newLv = e.getValue().linkedVariables(context).minimum(LinkedVariables.LINK_ASSIGNED);
                LinkedVariables originalLv = e.getKey().linkedVariables(context);
                LinkedVariables lv = originalLv.merge(newLv);
                Expression currentValue = evaluationContext.currentValue(ve.variable());
                Expression wrapped;
                // IMPROVE this is more or less hard coded to solve Identity_2, code should be generalised
                if (currentValue instanceof Instance instance && instance.valueProperties().getOrDefault(Property.IDENTITY, DV.FALSE_DV).valueIsTrue()) {
                    wrapped = PropertyWrapper.propertyWrapper(e.getValue(), lv, Map.of(Property.IDENTITY, DV.TRUE_DV));
                } else if (currentValue instanceof PropertyWrapper pw) {
                    wrapped = PropertyWrapper.propertyWrapper(e.getValue(), lv, pw);
                } else {
                    wrapped = PropertyWrapper.propertyWrapper(e.getValue(), lv);
                }
                builder.modifyingMethodAccess(ve.variable(), wrapped, lv);
            }

        });

        return builder.build();
    }

    private AnalysisStatus emptyExpression(StatementAnalyserSharedState sharedState, CausesOfDelay causes) {
        // try-statement has no main expression, and it may not have initializers; break; continue; ...
        boolean progress = false;
        StateData stateData = statementAnalysis.stateData();
        if (stateData.valueOfExpression.isVariable()) {
            progress = setFinalAllowEquals(stateData.valueOfExpression, EmptyExpression.EMPTY_EXPRESSION);
        }
        Primitives primitives = sharedState.context().getPrimitives();
        progress |= stateData.setPrecondition(Precondition.empty(primitives));
        progress |= stateData.setPostCondition(PostCondition.empty(primitives));
        progress |= stateData.setPreconditionFromMethodCalls(Precondition.empty(primitives));
        progress |= stateData.setEvaluatedExpressionCache(EvaluatedExpressionCache.EMPTY);

        if (statementAnalysis.flowData().timeAfterExecutionNotYetSet()) {
            statementAnalysis.flowData().copyTimeAfterExecutionFromInitialTime();
            progress = true;
        }
        if (statementAnalysis.statement() instanceof BreakStatement breakStatement) {
            if (statementAnalysis.parent().statement() instanceof SwitchStatementOldStyle) {
                return ProgressWrapper.of(progress, causes);
            }
            StatementAnalysisImpl.FindLoopResult correspondingLoop = statementAnalysis.findLoopByLabel(breakStatement);
            Expression state = sharedState.localConditionManager().stateUpTo(sharedState.context(), correspondingLoop.steps());
            progress |= correspondingLoop.statementAnalysis().stateData().addStateOfInterrupt(index(), state);
            if (state.isDelayed()) return ProgressWrapper.of(progress, state.causesOfDelay());
            Expression condition = sharedState.localConditionManager().condition();
            if (correspondingLoop.statementAnalysis().rangeData().getRange().generateErrorOnInterrupt(condition)) {
                statementAnalysis.ensure(Message.newMessage(statementAnalysis.location(EVALUATION), Message.Label.INTERRUPT_IN_LOOP));
            }
        } else if (statement() instanceof LocalClassDeclaration) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(sharedState.context());
            return apply.apply(sharedState, builder.build()).combinedStatus();
        }
        return ProgressWrapper.of(progress, causes);
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
            Expression newValue = vi.getValue().removeAllReturnValueParts(statementAnalysis.primitives());
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

    /*
      modify the value of the return variable according to the evaluation result and the current state

      consider
      if(x) return a;
      return b;

      after the if, the state is !x, and the return variable has the value x?a:<return>
      we should not immediately overwrite, but take the existing return value into account, and return x?a:b

      See Eg. Warnings_5, ConditionalChecks_4
   */

    private EvaluationResult createAndEvaluateReturnStatement(EvaluationContext evaluationContext,
                                                              Expression expression,
                                                              EvaluationResult result) {
        assert methodInfo().hasReturnValue();
        EvaluationResult context = EvaluationResult.from(evaluationContext);
        Structure structure = statementAnalysis.statement().getStructure();
        ReturnVariable returnVariable = new ReturnVariable(methodInfo());
        VariableInfo prev = statementAnalysis.getVariable(returnVariable.fullyQualifiedName()).getPreviousOrInitial();
        Expression currentReturnValue = prev.getValue();

        EvaluationResult updatedContext;
        Expression toEvaluate;
        ForwardEvaluationInfo forwardEvaluationInfo;
        Set<Variable> directAssignmentVariables;
        EvaluationResult hasAlreadyBeenEvaluated;
        if (currentReturnValue instanceof UnknownExpression) {
            // simplest situation
            toEvaluate = expression;
            updatedContext = context;
            forwardEvaluationInfo = structure.forwardEvaluationInfo();
            directAssignmentVariables = null;
            hasAlreadyBeenEvaluated = result;
        } else {
            /*
            The reason we compute the directAssignmentVariables from the previous LV rather than from the current
            translated expression is that the linked variables may be the product of a LV merge, which is different.
            See AnalysisProvider_0 for a full example.
             */
            directAssignmentVariables = prev.getLinkedVariables().directAssignmentVariables();
            // substitute <return value> for the current expression, rather than rely on condition manager in eval context
            Expression returnExpression = UnknownExpression.forReturnVariable(methodInfo().identifier,
                    returnVariable.returnType);
            TranslationMap tm = new TranslationMapImpl.Builder().put(returnExpression, expression).build();
            Expression translated = currentReturnValue.translate(context.evaluationContext().getAnalyserContext(), tm);
            // if translated == currentReturnValue, then there was no returnExpression, so we stick to expression
            toEvaluate = translated == currentReturnValue ? expression : translated;
            EvaluationContext newEc = context.evaluationContext().dropConditionManager();
            updatedContext = context.withNewEvaluationContext(newEc);
            forwardEvaluationInfo = new ForwardEvaluationInfo.Builder(structure.forwardEvaluationInfo())
                    .doNotComplainInlineConditional().setIgnoreValueFromState().build();
            hasAlreadyBeenEvaluated = null;
        }
        Assignment assignment = new Assignment(statementAnalysis.primitives(),
                new VariableExpression(new ReturnVariable(methodInfo())), toEvaluate, hasAlreadyBeenEvaluated,
                directAssignmentVariables);
        EvaluationResult evaluatedAssignment = assignment.evaluate(updatedContext, forwardEvaluationInfo);
        return new EvaluationResult.Builder(context)
                .compose(result)
                .copyChangeData(evaluatedAssignment, returnVariable)
                .setExpression(evaluatedAssignment.getExpression())
                .build();
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
                    labelEqualsSwitchExpression, false);
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

        Expression evaluated = sharedState.localConditionManager().evaluate(sharedState.context(), value, false);

        if (sharedState.localConditionManager().isDelayed()) {
            CausesOfDelay causes = sharedState.localConditionManager().causesOfDelay();
            if (causes.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY)) {
                LOGGER.debug("Break init delay -- not delaying");
            } else {
                Identifier identifier = statement().getStructure().expression().getIdentifier();
                return DelayedExpression.forCondition(identifier,
                        statementAnalysis.primitives().booleanParameterizedType(), evaluated, causes);
            }
        }

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
