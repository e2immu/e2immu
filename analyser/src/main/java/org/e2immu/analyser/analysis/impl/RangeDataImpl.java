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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analysis.RangeData;
import org.e2immu.analyser.analysis.range.NumericRange;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ForStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.support.VariableFirstThen;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION;
import static org.e2immu.analyser.util.Logger.log;

public class RangeDataImpl implements RangeData {

    private final VariableFirstThen<CausesOfDelay, Range> range;

    public RangeDataImpl(Location location) {
        range = new VariableFirstThen<>(new SimpleSet(new SimpleCause(location, CauseOfDelay.Cause.INITIAL_RANGE)));
    }

    @Override
    public void setRangeDelay(CausesOfDelay causes) {
        range.setFirst(causes);
    }

    @Override
    public void setRange(Range range) {
        this.range.set(range);
    }

    @Override
    public Range getRange() {
        return range.get();
    }

    @Override
    public CausesOfDelay rangeDelays() {
        return range.isFirst() ? range.getFirst() : CausesOfDelay.EMPTY;
    }

    @Override
    public void computeRange(Statement statement, EvaluationResult result) {
        if (statement instanceof ForStatement) {
            if (statement.getStructure().initialisers().size() == 1
                    && statement.getStructure().initialisers().get(0) instanceof LocalVariableCreation lvc
                    && lvc.declarations.size() == 1) {
                LocalVariableReference lvr = lvc.declarations.get(0).localVariableReference();

                Expression init = result.storedValues().get(0);
                Expression updateExpression = result.storedValues().get(1);
                VariableExpression variable;
                Expression update;
                if (updateExpression instanceof VariableExpression ve) {
                    // this is a bit of a special situation: i++ gets evaluated into i, with a "post" increment not visible
                    // now obviously i-- also gets this treatment :-)
                    Expression updater = statement.getStructure().updaters().get(0);
                    if (updater instanceof Assignment assignment && assignment.isPostfix()) {
                        int increment = assignment.isPostfixPlusPlus() ? 1 : -1;
                        update = new IntConstant(result.evaluationContext().getPrimitives(), increment);
                        variable = ve;
                    } else {
                        range.set(Range.NO_RANGE);
                        return;
                    }
                } else if (updateExpression instanceof Sum sum && sum.lhs instanceof IntConstant increment
                        && sum.rhs instanceof VariableExpression ve && ve.variable().equals(lvr)) {
                    update = increment;
                    variable = ve;
                } else {
                    range.set(Range.NO_RANGE);
                    return;
                }
                Expression condition = result.value();
                CausesOfDelay causes = init.causesOfDelay().merge(update.causesOfDelay()).merge(condition.causesOfDelay());
                if (causes.isDelayed()) {
                    log(DELAYED, "Delaying range at {}: {}", statement.getIdentifier(), causes);
                    range.setFirst(causes);
                } else {
                    computeRange(result.evaluationContext(), variable, init, update, condition);
                }
                return;
            }
        }
        range.set(Range.NO_RANGE);
    }

    /**
     * Computes a range, or sets a delay
     *
     * @param loopVar      the loop variable, as in the updatedValue and condition
     * @param initialValue initial value
     * @param updatedValue updated value
     * @param condition    the condition
     */
    private void computeRange(EvaluationContext evaluationContext,
                              VariableExpression loopVar,
                              Expression initialValue,
                              Expression updatedValue,
                              Expression condition) {
        if (initialValue instanceof IntConstant init && updatedValue instanceof IntConstant update) {
            int start = init.getValue();
            int increment = update.getValue();
            if (condition instanceof GreaterThanZero gt0) {
                GreaterThanZero.XB xb = gt0.extract(evaluationContext);
                if (loopVar.equals(xb.x())) {
                    int endExcl = (int) (xb.b() + (gt0.allowEquals() ? 1 : 0));
                    if (xb.lessThan() && start < endExcl && increment > 0 || !xb.lessThan() && start > endExcl && increment < 0) {
                        Range r = new NumericRange(start, endExcl, increment, loopVar);
                        log(EXPRESSION, "Identified range {}", r);
                        setRange(r);
                        return;
                    }
                }
            }
        }
        range.set(Range.NO_RANGE);
    }
}
