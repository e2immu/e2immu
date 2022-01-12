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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.RangeData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.range.NumericRange;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ForStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.parser.Message;
import org.e2immu.support.VariableFirstThen;

import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.EXPRESSION;
import static org.e2immu.analyser.util.Logger.log;

public class RangeDataImpl implements RangeData {
    private final Location location;
    private final VariableFirstThen<CausesOfDelay, Range> range;

    public RangeDataImpl(Location location) {
        range = new VariableFirstThen<>(new SimpleSet(new SimpleCause(location, CauseOfDelay.Cause.INITIAL_RANGE)));
        this.location = location;
    }

    @Override
    public Stream<Message> messages() {
        if (range.isFirst()) return Stream.of();
        Range r = range.get();
        if (r == Range.EMPTY) {
            return Stream.of(Message.newMessage(location, Message.Label.EMPTY_LOOP));
        }
        if (r == Range.INFINITE_LOOP) {
            return Stream.of(Message.newMessage(location, Message.Label.INFINITE_LOOP_CONDITION));
        }
        int count = r.loopCount();
        if (count == 1) {
            return Stream.of(Message.newMessage(location, Message.Label.LOOP_ONCE));
        }
        return Stream.of();
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
    public void computeRange(StatementAnalysis statementAnalysis, EvaluationResult result) {
        Statement statement = statementAnalysis.statement();
        if (statement instanceof ForStatement) {
            if (statement.getStructure().initialisers().size() == 1
                    && statement.getStructure().initialisers().get(0) instanceof LocalVariableCreation lvc
                    && lvc.declarations.size() == 1) {
                LocalVariableReference lvr = lvc.declarations.get(0).localVariableReference();
                DV modified = loopVariableIsModified(statementAnalysis, lvr);
                if (modified.isDelayed()) {
                    range.setFirst(modified.causesOfDelay());
                    return;
                }
                if (modified.equals(DV.TRUE_DV)) {
                    range.set(Range.NO_RANGE);
                    return;
                }
                Expression init = result.storedValues().get(0);
                Expression updateExpression = result.storedValues().get(1);
                VariableExpression variable;
                Expression update;
                if (updateExpression instanceof IntConstant increment) {
                    // += 1, -= 1, ++ post+pre, -- post+pre
                    Expression updater = statement.getStructure().updaters().get(0);
                    if (updater instanceof Assignment assignment) {
                        int i;
                        if (assignment.isPlusEquals()) {
                            i = increment.constant();
                        } else if (assignment.isMinusEquals()) {
                            i = -increment.constant();
                        } else {
                            range.set(Range.NO_RANGE);
                            return;
                        }
                        update = new IntConstant(result.evaluationContext().getPrimitives(), i);
                        variable = new VariableExpression(lvr);
                    } else {
                        range.set(Range.NO_RANGE);
                        return;
                    }
                } else if (updateExpression instanceof Sum sum && sum.lhs instanceof IntConstant increment
                        && sum.rhs instanceof VariableExpression ve && ve.variable().equals(lvr)) {
                    // normal situation i = i + 4
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
                    return;
                }
                computeRange(result.evaluationContext(), variable, init, update, condition);
                return;
            }
        }
        range.set(Range.NO_RANGE);
    }

    /*
    The code above currently only works when the loop variable is not modified inside the loop.

     */
    private DV loopVariableIsModified(StatementAnalysis statementAnalysis, LocalVariableReference lvr) {
        StatementAnalysis first = statementAnalysis.navigationData().blocks.get().get(0).orElse(null);
        if (first == null) return DV.FALSE_DV;
        StatementAnalysis last = first.lastStatement();
        VariableInfoContainer vic = last.findOrNull(lvr);
        if (vic == null) {
            return new SimpleSet(new VariableCause(lvr, location, CauseOfDelay.Cause.WAIT_FOR_ASSIGNMENT));
        }
        VariableInfo current = vic.current();
        if (current.getAssignmentIds().getLatestAssignment().compareTo(first.index()) >= 0) {
            return DV.TRUE_DV;
        }
        return DV.FALSE_DV;
    }

    public Expression extraState(EvaluationContext evaluationContext) {
        if (range.isSet()) {
            return range.get().conditions(evaluationContext);
        }
        CausesOfDelay causes = range.getFirst();
        return DelayedExpression.forState(evaluationContext.getPrimitives().booleanParameterizedType(),
                LinkedVariables.delayedEmpty(causes), causes);
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
                    int endExcl = (int) (xb.b() + (gt0.allowEquals() ? (increment < 0 ? -1 : 1) : 0));
                    if (xb.lessThan() && start < endExcl && increment > 0 || !xb.lessThan() && start > endExcl && increment < 0) {
                        Range r = new NumericRange(start, endExcl, increment, loopVar);
                        log(EXPRESSION, "Identified range {}", r);
                        setRange(r);
                        return;
                    }
                    // int i=10; i<10; i++      int i=10; i>=11; i--
                    if (xb.lessThan() && start >= endExcl || !xb.lessThan() && start < endExcl && increment < 0) {
                        setRange(Range.EMPTY);
                        return;
                    }
                    if (xb.lessThan() && increment < 0 || !xb.lessThan() && increment > 0) {
                        setRange(Range.INFINITE_LOOP);
                        return;
                    }
                    if (increment == 0) {
                        setRange(Range.INFINITE_LOOP);
                        return;
                    }
                }
            }
        }
        range.set(Range.NO_RANGE);
    }
}
