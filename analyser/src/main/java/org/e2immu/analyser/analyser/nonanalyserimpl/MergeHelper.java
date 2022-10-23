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

package org.e2immu.analyser.analyser.nonanalyserimpl;

// assignment in if and else block

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Stage.MERGE;
import static org.e2immu.analyser.analyser.VariableInfo.MERGE_WITHOUT_VALUE_PROPERTIES;

/*
Different situations but they need to be dealt with in more or less the same way.
Each time we have two triples of (value, state on assignment, assignment id): (s1, v1, a1), (s2, v2, a2)

Using a1, a2 we can compute if the assignment of 1 comes either before or at the same time as 2.

If 1 comes before 2, then 1 already is the summary of all previous assignments to the variable: there is
no third to look at.
If 1 is at the same time as 2, we are in an if(s) {1} else {2} situation, with an assignment in both cases.
There are no other situations!

The state on assignment reflects both situations. In the latter, we expect to find s and !s in the state,
and potentially already in the value as well. In the former, the state of 1 should be contained in the state of 2.

IMPORTANT: at this point, the execution delay only influences the non-value properties, it has no bearing on value
and/or value properties. This should be fine, as the value of the last statement in a block can only be known
when the execution of that statement is known. See TrieSimplified_3 as an example of why the delay is relevant.

 */
public record MergeHelper(EvaluationContext evaluationContext,
                          VariableInfoImpl vi,
                          CausesOfDelay executionDelay) {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeHelper.class);

    record MergeHelperResult(VariableInfoImpl vii, ProgressAndDelay progressAndDelay) {
    }

    /*
    Merge this object and merge sources into a newly created VariableInfo object.
     */
    public MergeHelperResult mergeIntoNewObject(Expression stateOfDestination,
                                                Merge.ExpressionAndProperties overwriteValue,
                                                boolean atLeastOneBlockExecuted,
                                                List<ConditionAndVariableInfo> mergeSources,
                                                GroupPropertyValues groupPropertyValues,
                                                TranslationMap translationMap,
                                                VariableInfoImpl previousForValue) {
        AssignmentIds mergedAssignmentIds = mergedAssignmentIds(atLeastOneBlockExecuted,
                vi.getAssignmentIds(), mergeSources);
        boolean mergeIsLoop = evaluationContext.getCurrentStatement().statement() instanceof LoopStatement;
        String mergedReadId = mergedReadId(mergeIsLoop, vi.getReadId(), mergeSources);
        VariableInfoImpl newObject = new VariableInfoImpl(evaluationContext.getLocation(MERGE),
                vi.variable(), mergedAssignmentIds, mergedReadId);
        ProgressAndDelay pad = new MergeHelper(evaluationContext, newObject, executionDelay)
                .mergeIntoMe(stateOfDestination, overwriteValue, atLeastOneBlockExecuted, vi, previousForValue, mergeSources,
                        groupPropertyValues, translationMap);
        return new MergeHelperResult(newObject, pad);
    }

    /**
     * We know that in each of the merge sources, the variable is either read or assigned to.
     * <p>
     * In case of a rename operation, "me" is a new VII object; the mergeSources still have VI's
     * that point to the old variable.
     */
    public ProgressAndDelay mergeIntoMe(Expression stateOfDestination,
                                        Merge.ExpressionAndProperties overwriteValue,
                                        boolean atLeastOneBlockExecuted,
                                        VariableInfoImpl previous,
                                        VariableInfoImpl previousForValue,
                                        List<ConditionAndVariableInfo> mergeSources,
                                        GroupPropertyValues groupPropertyValues,
                                        TranslationMap translationMap) {
        assert atLeastOneBlockExecuted || previous != vi;

        Merge.ExpressionAndProperties mergeValue;
        if (overwriteValue != null) {
            mergeValue = overwriteValue;
        } else {
            mergeValue = new MergeHelper(evaluationContext, previousForValue, executionDelay)
                    .mergeValue(stateOfDestination, atLeastOneBlockExecuted, mergeSources);
        }
        // replaceLocalVariables is based on a translation
        Expression beforeMoveDWE;
        if (!translationMap.isEmpty()) {
            beforeMoveDWE = mergeValue.expression().translate(evaluationContext.getAnalyserContext(), translationMap);
        } else {
            beforeMoveDWE = mergeValue.expression();
        }

        // delay wrapped expressions cannot work for return values! <return value> will disappear (E.g. EventuallyE1Immutable_2)
        Expression mergedValue = vi.variable() instanceof FieldReference
                ? DelayedWrappedExpression.moveDelayedWrappedExpressionToFront(evaluationContext.getAnalyserContext(), beforeMoveDWE)
                : beforeMoveDWE;
        AtomicBoolean progress = new AtomicBoolean();
        try {
            progress.set(vi.setValue(mergedValue)); // copy the delayed value
        } catch (IllegalStateException ise) {
            LOGGER.error("Problem overwriting!");
            throw ise;
        }
        if (!mergedValue.isDelayed() && !mergedValue.isNotYetAssigned()) {
            mergeValue.valueProperties().stream().forEach(e -> {
                boolean b = vi.setProperty(e.getKey(), e.getValue());
                if (b) progress.set(true);
            });
            assert allValuePropertiesSet();
        }

        // TODO maybe we should do these together with the value as well?
        boolean b = mergeNonValueProperties(atLeastOneBlockExecuted, previous, mergeSources, groupPropertyValues);
        if (b) progress.set(true);

        return new ProgressAndDelay(progress.get(), mergedValue.causesOfDelay());
    }

    private boolean allValuePropertiesSet() {
        return EvaluationContext.VALUE_PROPERTIES.stream().allMatch(p -> vi.getProperty(p).isDone());
    }

    private String mergedReadId(boolean mergeIsLoop, String previousId, List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + Stage.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getReadId().compareTo(currentStatementIdE) > 0);
        /*
        extra rule: when a variable is read in 2-E, but not in the subBlocks, we return 2:M when in a loop.
        see VariableInLoop_3: even if the variable is assigned inside the loop, the evaluation should occur again
        (unless the loop is only executed exactly once, but that will throw another error)
         */
        return inSubBlocks || mergeIsLoop && previousId.equals(currentStatementIdE) ? currentStatementIdM : previousId;
    }

    private AssignmentIds mergedAssignmentIds(boolean atLeastOneBlockExecuted,
                                              AssignmentIds previousIds,
                                              List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + Stage.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getAssignmentIds().getLatestAssignment().compareTo(currentStatementIdE) > 0);
        if (!inSubBlocks) return previousIds;
        Stream<AssignmentIds> sub = merge.stream().map(cav -> cav.variableInfo().getAssignmentIds());
        Stream<AssignmentIds> inclPrev = atLeastOneBlockExecuted ? sub : Stream.concat(Stream.of(previousIds), sub);
        return new AssignmentIds(currentStatementIdM, inclPrev);
    }


    /**
     * Compute and set or update in this object, the properties resulting from merging previous and merge sources.
     * If existingValuesWillBeOverwritten is true, the previous object is ignored.
     */
    boolean mergeNonValueProperties(boolean existingValuesWillBeOverwritten,
                                    VariableInfo previous,
                                    List<ConditionAndVariableInfo> mergeSources,
                                    GroupPropertyValues groupPropertyValues) {
        List<VariableInfo> list = mergeSources.stream()
                .map(ConditionAndVariableInfo::variableInfo)
                .collect(Collectors.toCollection(() -> new ArrayList<>(mergeSources.size() + 1)));
        if (!existingValuesWillBeOverwritten) {
            assert previous != null;
            list.add(previous);
        }
        boolean progress = false;
        for (VariableInfo.MergeOp mergeOp : MERGE_WITHOUT_VALUE_PROPERTIES) {
            Property property = mergeOp.property();
            Variable variable = previous.variable();

            DV commonValue;
            if (executionDelay.isDelayed()) {
                commonValue = executionDelay;
            } else {
                commonValue = mergeOp.initial();

                for (VariableInfo vi : list) {
                    if (vi != null) {
                        DV value = vi.getProperty(property);
                        commonValue = mergeOp.operator().apply(commonValue, value);
                    }
                }
            }
            // important that we always write to CNN, CM, even if there is a delay
            if (property.isGroupProperty()) {
                groupPropertyValues.set(property, variable, commonValue);
            } else {
                if (commonValue.isDone()) {
                    progress |= vi.setProperty(property, commonValue);
                } // else: the remaining properties (not value, not group property), have no meaningful delay
            }
        }
        return progress;
    }

    private Merge.ExpressionAndProperties addValueProperties(Expression expression) {
        return new Merge.ExpressionAndProperties(expression, evaluationContext.getValueProperties(expression));
    }

    private Merge.ExpressionAndProperties valueProperties() {
        return valueProperties(vi, vi.getValue());
    }

    private Merge.ExpressionAndProperties valueProperties(VariableInfo vi) {
        return new Merge.ExpressionAndProperties(vi.getValue(), vi.valueProperties());
    }

    private Merge.ExpressionAndProperties valuePropertiesWrapToBreakFieldInitDelay(VariableInfo vi) {
        CausesOfDelay causes = DelayFactory.createDelay(evaluationContext.getLocation(MERGE), CauseOfDelay.Cause.VALUES);
        Map<Property, DV> delayedProperties = EvaluationContext.delayedValueProperties(causes);
        Expression value = new DelayedWrappedExpression(Identifier.generate("dwe break init value props"),
                vi.variable(),
                vi.getValue(), vi.valueProperties(), vi.getLinkedVariables(), causes);
        return new Merge.ExpressionAndProperties(value, Properties.of(delayedProperties));
    }

    private Merge.ExpressionAndProperties valueProperties(VariableInfo vi, Expression value) {
        return new Merge.ExpressionAndProperties(value, vi.valueProperties());
    }

    /**
     * Compute, but do not set, the merge value between this object (the "previous") and the merge sources.
     * If atLeastOneBlockExecuted is true, this object's value is ignored.
     * <p>
     * Because we choose which expressions to include, and which not, we must compute the value properties here.
     */
    private Merge.ExpressionAndProperties mergeValue(Expression stateOfDestination,
                                                     boolean atLeastOneBlockExecuted,
                                                     List<ConditionAndVariableInfo> mergeSources) {
        Expression currentValue = vi.getValue();
        if (!atLeastOneBlockExecuted && currentValue.isEmpty()) return valueProperties();

        Variable variable = vi.variable();
        if (mergeSources.isEmpty()) {
            if (atLeastOneBlockExecuted) {
                throw new UnsupportedOperationException("No merge sources for " + variable.fullyQualifiedName());
            }
            return valueProperties();
        }

        // here is the correct point to remove "dead" branches (guaranteed to throw exception, and for non-return
        // variables, also guaranteed to return, so not continue after the merge)
        List<ConditionAndVariableInfo> reduced = mergeSources.stream().filter(ConditionAndVariableInfo::keepInMerge).toList();

        boolean allValuesIdentical = reduced.stream().allMatch(cav -> currentValue.equals(cav.value()));
        if (allValuesIdentical) return valueProperties();
        boolean allReducedIdentical = atLeastOneBlockExecuted && reduced.stream().skip(1)
                .allMatch(cav -> specialEquals(reduced.get(0).variableInfo(), cav.variableInfo()));
        if (allReducedIdentical) return valueProperties(reduced.get(0).variableInfo());

        EvaluationResult context = EvaluationResult.from(evaluationContext);
        if (reduced.size() == 1) {
            ConditionAndVariableInfo e = reduced.get(0);
            if (atLeastOneBlockExecuted) {
                return valueProperties(e.variableInfo());
            }
            Expression reworkedCondition = RewriteCondition.rewriteConditionFromLoopVariableToParameter(context,
                    e.condition(), e.absoluteState());
            if (reworkedCondition.causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.BREAK_INIT_DELAY) && !e.variableInfo().getValue().isDelayed()) {
                // simply accept this one, for now
                return valuePropertiesWrapToBreakFieldInitDelay(e.variableInfo());
            }
            Merge.ExpressionAndProperties result = one(e.variableInfo(), stateOfDestination, reworkedCondition);
            if (result != null) return result;
        }

        if (reduced.size() == 2 && atLeastOneBlockExecuted) {
            ConditionAndVariableInfo e = reduced.get(0);
            Expression negated = Negation.negate(context, e.condition());
            ConditionAndVariableInfo e2 = reduced.get(1);

            if (e2.condition().equals(negated)) {
                Merge.ExpressionAndProperties result = twoComplementary(e.variableInfo(),
                        stateOfDestination, e.condition(), e2.variableInfo());
                if (result != null) return result;
            } else if (e2.condition().isBoolValueTrue()) {
                return valueProperties(e2.variableInfo());
            }
        }

        // all the rest is the territory of switch and try statements, not yet implemented

        // one thing we can already do: if the try statement ends with a 'finally', we return this value
        ConditionAndVariableInfo eLast = reduced.get(reduced.size() - 1);
        if (eLast.condition().isBoolValueTrue()) return valueProperties(eLast.variableInfo());

        CausesOfDelay valuesDelayed = reduced.stream().map(cav -> cav.variableInfo().getValue().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (valuesDelayed.isDelayed()) {
            // all are delayed, they're not all identical delayed field references.
            return addValueProperties(delayedConclusion(valuesDelayed));
        }

        // no clue, but try to do something with @NotNull
        DV worstNotNull = reduced.stream().map(cav -> cav.variableInfo().getProperty(NOT_NULL_EXPRESSION))
                .reduce(DV.MIN_INT_DV, DV::min);
        DV worstNotNullIncludingCurrent = atLeastOneBlockExecuted ? worstNotNull :
                worstNotNull.min(evaluationContext.getProperty(currentValue, NOT_NULL_EXPRESSION, false, true));
        ParameterizedType pt = variable.parameterizedType();
        DV nne = worstNotNullIncludingCurrent == DV.MIN_INT_DV ? MultiLevel.NULLABLE_DV : worstNotNullIncludingCurrent;
        Properties valueProperties = evaluationContext.getAnalyserContext().defaultValueProperties(pt, nne);
        return addValueProperties(noConclusion(valueProperties));
    }

    /*
    there is the special situation of multiple delayed versions of the same field.
    we cannot have an idea yet if the result is equals (certainly when effectively final) or not (possibly
    when variable, with different statement times).
     */
    private boolean specialEquals(VariableInfo vi1, VariableInfo vi2) {
        Expression e1 = vi1.getValue();
        Expression e2 = vi2.getValue();
        if (e1 instanceof DelayedVariableExpression dve1 && e2 instanceof DelayedVariableExpression dve2 &&
                dve1.variable().equals(dve2.variable())) {
            return true;
        }
        return e1.equals(e2);
    }

    /*
    NON-RETURN variables

    x = 1;
    statement { --> implies condition b (synchronized(this) -> true, if(b) -> b)
      if(a) return; // some interrupts
      x = 3; --> state is !a,
      if(c) return;
      x = 5; --> end state is !a && !c
    }
    result is simply b?3:1, in other words, we do not care about interrupts, nor about the state
    at the moment of assignment. The simple truth is that we cannot get to this point (the end of the statement)
    if there is an interrupt.

    x = 1;
    while(true) {
       if(a) break;
       x = 2;
       if(b) break;
       x = 3;
       ... some loop stuff ...
    }
    now it depends if a and b are dependent on the loop modification or not.
    if not, then the interrupts act as a return variable, and state becomes important.
    if they are dependent on modifications, replacement of the loop variable by some anonymous instance
    implies that we cannot compute a decent value for x anyway, so the state won't matter.

    RETURN VARIABLE
    the return variable starts with an unknown return value in each block!
    Only merge accumulates; assignment plays a role.

    x = 3; --> ret var = <unknown return value>
    if(c) {
      return x;  --> ret var = 3
    } --> ret var = c?3:<unknown return value>


    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) return zz;
      --> state is !a, ret var = a?zz:<unknown>
      return x;  --> ret var = a?zz:3 [here the state plays a role, in the level 3 statement analyser/assignment!]
    } --> ret var = c?a?zz:3:<unknown return value>

    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) return zz;
      --> state is !a, ret var = a?zz:<unknown>
      if(b) {
        return x;
      } else {
        return y;
      } --> ret var = a?zz:b?x:y
    } --> ret v

    x = 3; --> ret var = <unknown return value>
    if(c) {
      if(a) break;
      --> state is !a, ret var is still unknown
      if(b) {
        return x; --> return x
      } else {
        return y; --> return y
      } --> ret var = a?<unknown>:b?x:y
    } --> ret v
    */

    public Merge.ExpressionAndProperties one(VariableInfo vi1, Expression stateOfParent, Expression condition) {
        Expression vi1value = vi1.getValue();
        if (condition.isBoolValueTrue()) {

            // this if-statement replays the code in level 3 return statement:
            // it is identical to do if(x) return a; return b or if(x) return a; if(!x) return b;
            if (vi.variable() instanceof ReturnVariable) {
                if (stateOfParent.isBoolValueTrue()) return valueProperties(vi1);
                if (vi.variable().parameterizedType().equals(evaluationContext.getPrimitives().booleanParameterizedType())) {
                    EvaluationResult context = EvaluationResult.from(evaluationContext);
                    return new Merge.ExpressionAndProperties(And.and(context, stateOfParent, vi1value),
                            EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
                }
                return inlineConditionalIfFalseIsExisting(stateOfParent, vi1);
            }
            return valueProperties(vi1); // so we by-pass the "safe" in inlineConditional
        }
        return inlineConditionalIfFalseIsExisting(condition, vi1);
    }

    public Merge.ExpressionAndProperties twoComplementary(VariableInfo e1,
                                                          Expression stateOfParent,
                                                          Expression firstCondition,
                                                          VariableInfo e2) {
        if (vi.variable() instanceof ReturnVariable) {

            if (firstCondition.isBooleanConstant()) {
                VariableInfo two = firstCondition.isBoolValueTrue() ? e1 : e2; // to bypass the error check on "safe"
                if (stateOfParent.isBoolValueTrue()) return valueProperties(two);
                if (stateOfParent.isBoolValueFalse())
                    throw new UnsupportedOperationException(); // unreachable statement
                return inlineConditionalIfFalseIsExisting(stateOfParent, two);
            }

            Merge.ExpressionAndProperties two = inlineConditional(firstCondition, e1, e2, false);
            if (stateOfParent.isBoolValueTrue()) return two;
            if (stateOfParent.isBoolValueFalse()) throw new UnsupportedOperationException(); // unreachable statement
            VariableInfoImpl vii = new VariableInfoImpl(evaluationContext.getLocation(MERGE),
                    vi.variable(), two.expression(), two.valueProperties()); // exact variable not relevant
            return inlineConditionalIfFalseIsExisting(stateOfParent, vii);
        }

        if (firstCondition.isBoolValueTrue()) return valueProperties(e1); // to bypass the error check on "safe"
        if (firstCondition.isBoolValueFalse()) return valueProperties(e2);
        return inlineConditional(firstCondition, e1, e2, false);
    }

    /* condition and ifFalse have been evaluated in the same expression
       if(condition) {
         ifTrue
       } // else keep what we had: ifFalse

       if ifFalse is an Instance, and the condition contains the actual variable expression, we have to make sure that
       they are joined as the same expression. We prefer the instance at the moment.
       See e.g. TrieSimplified_5, StaticSideEffects_1, ConditionalInitialization_0, ...
    */
    private Merge.ExpressionAndProperties inlineConditionalIfFalseIsExisting(Expression condition, VariableInfo ifTrue) {
        Expression c;
        if (vi.getValue().isInstanceOf(Instance.class)) {
            TranslationMap translationMap = new TranslationMapImpl.Builder().addVariableExpression(vi.variable(), vi.getValue()).build();
            c = condition.translate(evaluationContext.getAnalyserContext(), translationMap);
        } else {
            c = condition;
        }
        return inlineConditional(c, ifTrue, vi, true);
    }

    private Merge.ExpressionAndProperties inlineConditional(Expression condition, VariableInfo ifTrue, VariableInfo ifFalse, boolean one) {
        if (ifTrue.isDelayed() && !ifFalse.isDelayed() && conditionsMetForBreakingInitialisationDelay(ifTrue)) {
            return valuePropertiesWrapToBreakFieldInitDelay(ifFalse);
        }
        if (!ifTrue.isDelayed() && ifFalse.isDelayed() && conditionsMetForBreakingInitialisationDelay(ifFalse)) {
            return valuePropertiesWrapToBreakFieldInitDelay(ifTrue);
        }
        EvaluationResult context = EvaluationResult.from(evaluationContext);
        Expression safe;
        if (vi.variable() instanceof ReturnVariable rv) {
            MethodInfo methodInfo = evaluationContext.getCurrentMethod().getMethodInfo();
            ReturnVariable returnVariable = new ReturnVariable(methodInfo);
            Expression returnExpression = UnknownExpression.forReturnVariable(methodInfo.identifier, returnVariable.returnType);
            Expression secondValue = one ? returnExpression : ifFalse.getValue();
            Expression ternary = safe(EvaluateInlineConditional.conditionalValueConditionResolved(context, condition,
                    ifTrue.getValue(), secondValue, false, vi.variable(), DV.FALSE_DV));
            TranslationMap tm = new TranslationMapImpl.Builder().put(returnExpression, ternary).build();
            safe = vi.getValue().translate(evaluationContext.getAnalyserContext(), tm);
        } else {
            safe = safe(EvaluateInlineConditional.conditionalValueConditionResolved(context,
                    condition, ifTrue.getValue(), ifFalse.getValue(), false, vi.variable(), DV.FALSE_DV));
        }
        // 2nd check (safe.isDelayed) because safe could be "true" even if the condition is delayed
        if (condition.isDelayed() && safe.isDelayed()) {
            CausesOfDelay delay = DelayFactory.createDelay(new VariableCause(vi.variable(),
                    evaluationContext.getLocation(MERGE), CauseOfDelay.Cause.CONDITION));
            Properties delayed = Properties.ofWritable(EvaluationContext.delayedValueProperties(delay));
            return new Merge.ExpressionAndProperties(safe, delayed);
        }
        /*
        !<v:boolean> ? <v:boolean> : true -> result is always true, but we cannot yet decide this, because <v:boolean> may be hiding a more complex
        operation
         */
        if (safe.isDone()) {
            if (ifTrue.isDelayed()) return valueProperties(ifTrue);
            if (ifFalse.isDelayed()) return valueProperties(ifFalse);
        }
        if (safe.equals(ifTrue.getValue())) {
            return valueProperties(ifTrue);
        }
        if (safe.equals(ifFalse.getValue())) {
            return valueProperties(ifFalse);
        }
        // check for <return value>, no need to compute properties
        boolean compute1 = ifTrue.getValue().isComputeProperties();
        boolean compute2 = ifFalse.getValue().isComputeProperties();
        Properties properties;
        if (compute1 && compute2) {
            Properties p = overwriteNotNull(condition, ifTrue, ifFalse);
            properties = ifTrue.valueProperties().merge(ifFalse.valueProperties()).combineSafely(p);
        } else if (compute1) {
            properties = ifTrue.valueProperties();
        } else {
            properties = ifFalse.valueProperties();
        }
        return new Merge.ExpressionAndProperties(safe, properties);
    }

    // x==null?y:x should avoid the nullable on x
    private Properties overwriteNotNull(Expression condition, VariableInfo ifTrue, VariableInfo ifFalse) {
        boolean negate = condition instanceof Negation;
        Expression conditionNoNegate = condition instanceof Negation neg ? neg.expression : condition;
        Properties properties = Properties.writable();
        if (conditionNoNegate instanceof Equals eq) {
            if (eq.lhs.isInstanceOf(NullConstant.class) && !negate && eq.rhs.equals(ifFalse.getValue())) {
                // null == x ? y : x
                properties.overwrite(NOT_NULL_EXPRESSION, ifTrue.getProperty(NOT_NULL_EXPRESSION));
            }
            if (eq.lhs.isInstanceOf(NullConstant.class) && negate && eq.rhs.equals(ifTrue.getValue())) {
                // null != x ? x : y
                DV max = ifFalse.getProperty(NOT_NULL_EXPRESSION).max(ifTrue.getProperty(NOT_NULL_EXPRESSION));
                properties.overwrite(NOT_NULL_EXPRESSION, max);
            }
        }
        return properties;
    }

    private boolean conditionsMetForBreakingInitialisationDelay(VariableInfo vi) {
        if (vi.variable() instanceof FieldReference) {
            CausesOfDelay causes = vi.getValue().causesOfDelay();
            if (vi.getValue() instanceof DelayedVariableExpression) {
                if (causes.containsCauseOfDelay(CauseOfDelay.Cause.VALUES, c -> c instanceof VariableCause vc
                        && vc.variable().equals(vi.variable())) && evaluationContext.getCurrentMethod() != null) {
                    MethodInfo methodInfo = evaluationContext.getCurrentMethod().getMethodInfo();
                    // we have a field whose values cannot be determined; this is causing issues right now
                    // do not do this during construction; for that purpose, use the break system of SAEvaluationContext.initialValueForReading
                    return !methodInfo.inConstruction();
                }
            }
        }
        return false;
    }

    private Expression safe(EvaluationResult result) {
        if (result.getMessageStream().anyMatch(m -> true)) {
            // something gone wrong, retreat
            Properties variableProperties = evaluationContext.getValueProperties(vi.getValue());
            return noConclusion(variableProperties);
        }
        return result.value();
    }

    public Expression noConclusion(Properties variableProperties) {
        CausesOfDelay delay = variableProperties.delays();
        if (delay.isDelayed()) {
            return DelayedVariableExpression.forVariable(vi.variable(), evaluationContext.getFinalStatementTime(), delay);
        }
        return Instance.genericMergeResult(evaluationContext.getCurrentStatement().index(), vi.variable(), variableProperties);
    }

    public Expression delayedConclusion(CausesOfDelay causes) {
        return DelayedVariableExpression.forMerge(vi.variable(), causes);
    }
}
