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
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.EvaluateInlineConditional;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.Property.*;
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



 */
public record MergeHelper(EvaluationContext evaluationContext, VariableInfoImpl vi) {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeHelper.class);

    /*
    Merge this object and merge sources into a newly created VariableInfo object.
     */
    public VariableInfoImpl mergeIntoNewObject(Expression stateOfDestination,
                                               Expression postProcessState,
                                               Merge.ExpressionAndProperties overwriteValue,
                                               boolean atLeastOneBlockExecuted,
                                               List<ConditionAndVariableInfo> mergeSources,
                                               GroupPropertyValues groupPropertyValues,
                                               TranslationMap translationMap) {
        AssignmentIds mergedAssignmentIds = mergedAssignmentIds(atLeastOneBlockExecuted,
                vi.getAssignmentIds(), mergeSources);
        String mergedReadId = mergedReadId(vi.getReadId(), mergeSources);
        VariableInfoImpl newObject = new VariableInfoImpl(evaluationContext.getLocation(),
                vi.variable(), mergedAssignmentIds, mergedReadId);
        new MergeHelper(evaluationContext, newObject).mergeIntoMe(stateOfDestination, postProcessState, overwriteValue,
                atLeastOneBlockExecuted, vi, mergeSources, groupPropertyValues, translationMap);
        return newObject;
    }

    /**
     * We know that in each of the merge sources, the variable is either read or assigned to.
     * <p>
     * In case of a rename operation, "me" is a new VII object; the mergeSources still have VI's
     * that point to the old variable.
     */
    public void mergeIntoMe(Expression stateOfDestination,
                            Expression postProcessState,
                            Merge.ExpressionAndProperties overwriteValue,
                            boolean atLeastOneBlockExecuted,
                            VariableInfoImpl previous,
                            List<ConditionAndVariableInfo> mergeSources,
                            GroupPropertyValues groupPropertyValues,
                            TranslationMap translationMap) {
        assert atLeastOneBlockExecuted || previous != vi;

        Merge.ExpressionAndProperties mergeValue;
        if (overwriteValue != null) {
            mergeValue = overwriteValue;
        } else {
            mergeValue = new MergeHelper(evaluationContext, previous)
                    .mergeValue(stateOfDestination, atLeastOneBlockExecuted, mergeSources);
        }
        // replaceLocalVariables is based on a translation
        Expression beforePostProcess;
        if (!translationMap.isEmpty()) {
            beforePostProcess = mergeValue.expression().translate(translationMap);
        } else {
            beforePostProcess = mergeValue.expression();
        }

        // postProcess, if applied, uses evaluation in a child context
        Expression mergedValue = postProcess(evaluationContext, beforePostProcess, postProcessState);

        // TODO do post-process and replace local variables change the value properties?
        vi.setValue(mergedValue); // copy the delayed value
        mergeValue.valueProperties().stream().forEach(e -> vi.setProperty(e.getKey(), e.getValue()));
        assert mergedValue.isDelayed() || allValuePropertiesSet();


        // TODO maybe we should do these together with the value as well?
        mergeNonValueProperties(atLeastOneBlockExecuted, previous, mergeSources, groupPropertyValues);


        if (evaluationContext.isMyself(vi.variable())) {
            vi.setProperty(CONTEXT_IMMUTABLE, MultiLevel.MUTABLE_DV);
            vi.setProperty(CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        }
    }

    private boolean allValuePropertiesSet() {
        return EvaluationContext.VALUE_PROPERTIES.stream().allMatch(p -> vi.getProperty(p).isDone());
    }

    private Expression postProcess(EvaluationContext evaluationContext,
                                   Expression beforePostProcess,
                                   Expression postProcessState) {
        if (postProcessState != null && !beforePostProcess.isDelayed() && !postProcessState.isDelayed() && !postProcessState.isBoolValueTrue()) {
            EvaluationContext child = evaluationContext.childState(postProcessState);
            Expression reEval = beforePostProcess.evaluate(child, ForwardEvaluationInfo.DEFAULT).getExpression();
            LOGGER.debug("Post-processed {} into {} to reflect state after block", beforePostProcess, reEval);
            return reEval;
        }
        return beforePostProcess;
    }

    private String mergedReadId(String previousId,
                                List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
        boolean inSubBlocks =
                merge.stream()
                        .map(ConditionAndVariableInfo::variableInfo)
                        .anyMatch(vi -> vi.getReadId().compareTo(currentStatementIdE) > 0);
        return inSubBlocks ? currentStatementIdM : previousId;
    }

    private AssignmentIds mergedAssignmentIds(boolean atLeastOneBlockExecuted,
                                              AssignmentIds previousIds,
                                              List<ConditionAndVariableInfo> merge) {
        // null current statement in tests
        String currentStatementIdE = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.EVALUATION;
        String currentStatementIdM = (evaluationContext.getCurrentStatement() == null ? "-" :
                evaluationContext.getCurrentStatement().index()) + VariableInfoContainer.Level.MERGE;
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
    void mergeNonValueProperties(boolean existingValuesWillBeOverwritten,
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
        for (VariableInfo.MergeOp mergeOp : MERGE_WITHOUT_VALUE_PROPERTIES) {
            DV commonValue = mergeOp.initial();

            for (VariableInfo vi : list) {
                if (vi != null) {
                    DV value = vi.getProperty(mergeOp.property());
                    commonValue = mergeOp.operator().apply(commonValue, value);
                }
            }
            // important that we always write to CNN, CM, even if there is a delay
            if (GroupPropertyValues.PROPERTIES.contains(mergeOp.property())) {
                groupPropertyValues.set(mergeOp.property(), previous.variable(), commonValue);
            } else {
                if (commonValue.isDone()) {
                    vi.setProperty(mergeOp.property(), commonValue);
                }
            }
        }
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
        SimpleSet causes = new SimpleSet(evaluationContext.getLocation(), CauseOfDelay.Cause.VALUES);
        Map<Property, DV> delayedProperties = EvaluationContext.VALUE_PROPERTIES.stream()
                .collect(Collectors.toUnmodifiableMap(p -> p, p -> causes));
        Expression value = new DelayedWrappedExpression(Identifier.generate(), vi, causes);
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
        if (!atLeastOneBlockExecuted && currentValue.isUnknown()) return valueProperties();

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

        boolean allValuesIdentical = reduced.stream().allMatch(cav ->
                currentValue.equals(cav.value()));
        if (allValuesIdentical) return valueProperties();
        boolean allReducedIdentical = atLeastOneBlockExecuted && reduced.stream().skip(1)
                .allMatch(cav -> specialEquals(evaluationContext.getVariableValue(variable, reduced.get(0).variableInfo()),
                        evaluationContext.getVariableValue(variable, cav.variableInfo())));
        if (allReducedIdentical) return valueProperties(reduced.get(0).variableInfo());

        if (reduced.size() == 1) {
            ConditionAndVariableInfo e = reduced.get(0);
            if (atLeastOneBlockExecuted) {
                return valueProperties(e.variableInfo());
            }
            Merge.ExpressionAndProperties result = one(e.variableInfo(), stateOfDestination, e.condition());
            if (result != null) return result;
        }

        if (reduced.size() == 2 && atLeastOneBlockExecuted) {
            ConditionAndVariableInfo e = reduced.get(0);
            Expression negated = Negation.negate(evaluationContext, e.condition());
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
    private static boolean specialEquals(Expression e1, Expression e2) {
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
                    return new Merge.ExpressionAndProperties(And.and(evaluationContext, stateOfParent, vi1value),
                            EvaluationContext.PRIMITIVE_VALUE_PROPERTIES);
                }
                return inlineConditional(stateOfParent, vi1, vi);
            }
            return valueProperties(vi1); // so we by-pass the "safe" in inlineConditional
        }
        return inlineConditional(condition, vi1, vi);
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
                return inlineConditional(stateOfParent, two, vi);
            }

            Merge.ExpressionAndProperties two = inlineConditional(firstCondition, e1, e2);
            if (stateOfParent.isBoolValueTrue()) return two;
            if (stateOfParent.isBoolValueFalse()) throw new UnsupportedOperationException(); // unreachable statement
            VariableInfoImpl vii = new VariableInfoImpl(vi.variable(), two.expression(), two.valueProperties()); // exact variable not relevant
            return inlineConditional(stateOfParent, vii, vi);
        }

        if (firstCondition.isBoolValueTrue()) return valueProperties(e1); // to bypass the error check on "safe"
        if (firstCondition.isBoolValueFalse()) return valueProperties(e2);
        return inlineConditional(firstCondition, e1, e2);
    }

    private Merge.ExpressionAndProperties inlineConditional(Expression condition, VariableInfo ifTrue, VariableInfo ifFalse) {
        if (ifTrue.isDelayed() && !ifFalse.isDelayed() && conditionsMetForBreakingInitialisationDelay(ifTrue)) {
            return valuePropertiesWrapToBreakFieldInitDelay(ifFalse);
        }
        if (!ifTrue.isDelayed() && ifFalse.isDelayed() && conditionsMetForBreakingInitialisationDelay(ifFalse)) {
            return valuePropertiesWrapToBreakFieldInitDelay(ifTrue);
        }

        Expression safe = safe(EvaluateInlineConditional.conditionalValueConditionResolved(evaluationContext,
                condition, ifTrue.getValue(), ifFalse.getValue()));
        if (condition.isDelayed()) {
            CausesOfDelay delay = new SimpleSet(new VariableCause(vi.variable(), evaluationContext.getLocation(), CauseOfDelay.Cause.CONDITION));
            Properties delayed = Properties.ofWritable(EvaluationContext.VALUE_PROPERTIES.stream().collect(Collectors.toUnmodifiableMap(p -> p, p -> delay)));
            return new Merge.ExpressionAndProperties(safe, delayed);
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
            properties = ifTrue.valueProperties().merge(ifFalse.valueProperties());
        } else if (compute1) {
            properties = ifTrue.valueProperties();
        } else {
            properties = ifFalse.valueProperties();
        }
        return new Merge.ExpressionAndProperties(safe, properties);
    }

    private boolean conditionsMetForBreakingInitialisationDelay(VariableInfo vi) {
        if (vi.variable() instanceof FieldReference) {
            CausesOfDelay causes = vi.getValue().causesOfDelay();
            if (vi.getValue() instanceof DelayedVariableExpression) {
                if (causes.causesStream().anyMatch(c -> c instanceof VariableCause vc && vc.cause() == CauseOfDelay.Cause.VALUES
                        && vc.variable().equals(vi.variable())) && evaluationContext.getCurrentMethod() != null) {
                    MethodInfo methodInfo = evaluationContext.getCurrentMethod().getMethodInfo();
                    // we have a field whose values cannot be determined; this is causing issues right now
                    // do not do this during construction; for that purpose, use the break system of SAEvaluationContext.initialValueForReading
                    return !methodInfo.isConstructor || methodInfo.methodResolution.get().partOfConstruction()
                            != MethodResolution.CallStatus.PART_OF_CONSTRUCTION;
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
            return DelayedVariableExpression.forVariable(vi.variable(), delay);
        }
        return Instance.genericMergeResult(evaluationContext.getCurrentStatement().index(), vi.variable(), variableProperties);
    }

    public Expression delayedConclusion(CausesOfDelay causes) {
        return DelayedExpression.forMerge(vi.variable().parameterizedType(), vi.getLinkedVariables(), causes);
    }
}
