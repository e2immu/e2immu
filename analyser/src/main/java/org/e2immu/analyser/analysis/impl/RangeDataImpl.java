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
import org.e2immu.analyser.analysis.range.ConstantRange;
import org.e2immu.analyser.analysis.range.NumericRange;
import org.e2immu.analyser.analysis.range.Range;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ForEachStatement;
import org.e2immu.analyser.model.statement.ForStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

public class RangeDataImpl implements RangeData {
    private static final Logger LOGGER = LoggerFactory.getLogger(RangeDataImpl.class);

    private final Location location;
    private final EventuallyFinal<Range> range = new EventuallyFinal<>();
    private final SetOnce<Message> uselessAssignment = new SetOnce<>();

    public RangeDataImpl(Location location) {
        range.setVariable(new Range.Delayed(new SimpleSet(new SimpleCause(location, CauseOfDelay.Cause.INITIAL_RANGE))));
        this.location = location;
    }

    @Override
    public Stream<Message> messages() {
        Stream<Message> streamOfUselessAssignment = uselessAssignment.isSet() ? Stream.of(uselessAssignment.get()) : Stream.of();
        return Stream.concat(streamOfRangeObject(), streamOfUselessAssignment);
    }

    private Stream<Message> streamOfRangeObject() {
        Range r = range.get();
        if (r.isDelayed()) return Stream.of();
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

    void setDelayed(Range delayed) {
        this.range.setVariable(delayed);
    }

    void setRange(Range range) {
        this.range.setFinal(range);
    }

    @Override
    public Range getRange() {
        return range.get();
    }

    @Override
    public void computeRange(StatementAnalysis statementAnalysis, EvaluationResult result) {
        Statement statement = statementAnalysis.statement();
        if (statement instanceof ForStatement) {
            Range r = forStatementSingleCreatedLocalVariable(statement, statementAnalysis, result);
            if (r == null) {
                r = forStatementSingleAssignedLocalVariable(statement, statementAnalysis, result);
            }
            if (r == null) {
                r = forStatementNoInitializer(statement, statementAnalysis, result);
            }
            if (r != null) {
                if (r.isDelayed()) setDelayed(r);
                else setRange(r);
                return;
            }
        }
        if (statement instanceof ForEachStatement forEach) {
            Range r = forEachConstantRange(forEach, statementAnalysis, result);
            if (r != null) {
                if (r.isDelayed()) setDelayed(r);
                else setRange(r);
                return;
            }
        }
        setRange(Range.NO_RANGE);
    }

    // for(String x: new String[] { ... }) ...
    // for(EnumType e: EnumType.values()) ...
    private Range forEachConstantRange(ForEachStatement statement,
                                       StatementAnalysis statementAnalysis,
                                       EvaluationResult result) {
        if (result.value() instanceof ArrayInitializer ai) {
            if (statement.structure.initialisers().get(0) instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr = lvc.newLocalVariables().get(0);
                return new ConstantRange(ai, new VariableExpression(lvr));
            }
        }
        return null;
    }

    // int i=a; for(; i >=,<=b; i += c) {...do not write ...}
    private Range forStatementNoInitializer(Statement statement,
                                            StatementAnalysis statementAnalysis,
                                            EvaluationResult result) {
        if (statement.getStructure().initialisers().isEmpty()
                && !statement.getStructure().updaters().isEmpty()
                && statement.getStructure().updaters().get(0) instanceof Assignment updater
                && updater.variableTarget != null) {
            Variable variable = updater.variableTarget;
            DV modified = loopVariableIsModified(statementAnalysis, variable);
            if (modified.isDelayed()) {
                return new Range.Delayed(modified.causesOfDelay());
            }
            if (modified.equals(DV.TRUE_DV)) {
                return Range.NO_RANGE;
            }
            // storedValues: updater, using hack so that only the variable expression remains, condition(1)
            if (result.storedValues().size() != 2) return Range.NO_RANGE;
            Expression init = result.evaluationContext().currentValue(variable);
            Expression updateExpression = result.storedValues().get(0); // i++ --> i$1+1

            VariableExpression ve;
            Expression update;
            if (updateExpression instanceof Sum sum && sum.lhs instanceof IntConstant increment
                    && sum.rhs instanceof VariableExpression sve && sve.variable().equals(variable)) {
                // normal situation i = i + 4
                update = increment;
                ve = sve;
            } else {
                return Range.NO_RANGE;
            }
            Expression condition = result.value();
            CausesOfDelay causes = init.causesOfDelay().merge(update.causesOfDelay()).merge(condition.causesOfDelay());
            if (causes.isDelayed()) {
                LOGGER.debug("Delaying range at {}: {}", statement.getIdentifier(), causes);
                return new Range.Delayed(causes);
            }
            return computeRange(result.evaluationContext(), ve, init, update, condition);
        }
        return null;
    }

    // int i=...; for(i=a; i >=,<=b; i += c) {...do not write ...}
    private Range forStatementSingleAssignedLocalVariable(Statement statement,
                                                          StatementAnalysis statementAnalysis,
                                                          EvaluationResult result) {
        if (statement.getStructure().initialisers().size() == 1
                && statement.getStructure().initialisers().get(0) instanceof Assignment assignment
                && assignment.variableTarget != null) {
            Variable variable = assignment.variableTarget;
            DV modified = loopVariableIsModified(statementAnalysis, variable);
            if (modified.isDelayed()) {
                return new Range.Delayed(modified.causesOfDelay());
            }
            if (modified.equals(DV.TRUE_DV)) {
                return Range.NO_RANGE;
            }
            // storedValues: init (just 1), updaters, using hack so that only the variable expression remains, condition(1)
            if (result.storedValues().size() != 3) return Range.NO_RANGE;
            Expression init = result.storedValues().get(0); // i=0 --> 0

            // check for useless assignment on the initialiser... something we cannot do with the standard code
            // in SACheck
            VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
            assert !vic.isInitial();
            VariableInfo previous = vic.getPreviousOrInitial();
            boolean uselessAssignment = previous.isAssigned() && !previous.isRead();
            if (uselessAssignment && !this.uselessAssignment.isSet()) {
                this.uselessAssignment.set(Message.newMessage(location, Message.Label.USELESS_ASSIGNMENT));
            }
            Expression updateExpression = result.storedValues().get(1); // i++ --> i$1+1

            VariableExpression ve;
            Expression update;
            if (updateExpression instanceof Sum sum && sum.lhs instanceof IntConstant increment
                    && sum.rhs instanceof VariableExpression sve && sve.variable().equals(variable)) {
                // normal situation i = i + 4
                update = increment;
                ve = sve;
            } else {
                return Range.NO_RANGE;
            }
            Expression condition = result.value();
            CausesOfDelay causes = init.causesOfDelay().merge(update.causesOfDelay()).merge(condition.causesOfDelay());
            if (causes.isDelayed()) {
                LOGGER.debug("Delaying range at {}: {}", statement.getIdentifier(), causes);
                return new Range.Delayed(causes);
            }
            return computeRange(result.evaluationContext(), ve, init, update, condition);
        }
        return null;
    }

    // for(int i=a; i >=,<=b; i += ++ c) {...do not write i ...}
    private Range forStatementSingleCreatedLocalVariable(Statement statement,
                                                         StatementAnalysis statementAnalysis,
                                                         EvaluationResult result) {
        if (statement.getStructure().initialisers().size() == 1
                && statement.getStructure().initialisers().get(0) instanceof LocalVariableCreation lvc
                && lvc.declarations.size() == 1) {
            LocalVariableReference lvr = lvc.declarations.get(0).localVariableReference();
            DV modified = loopVariableIsModified(statementAnalysis, lvr);
            if (modified.isDelayed()) {
                return new Range.Delayed(modified.causesOfDelay());
            }
            if (modified.equals(DV.TRUE_DV)) {
                return Range.NO_RANGE;
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
                        return Range.NO_RANGE;
                    }
                    update = new IntConstant(result.evaluationContext().getPrimitives(), i);
                    variable = new VariableExpression(lvr);
                } else {
                    return Range.NO_RANGE;
                }
            } else if (updateExpression instanceof Sum sum && sum.lhs instanceof IntConstant increment
                    && sum.rhs instanceof VariableExpression ve && ve.variable().equals(lvr)) {
                // normal situation i = i + 4
                update = increment;
                variable = ve;
            } else {
                return Range.NO_RANGE;
            }
            Expression condition = result.value();
            CausesOfDelay causes = init.causesOfDelay().merge(update.causesOfDelay()).merge(condition.causesOfDelay());
            if (causes.isDelayed()) {
                LOGGER.debug("Delaying range at {}: {}", statement.getIdentifier(), causes);
                return new Range.Delayed(causes);
            }
            return computeRange(result.evaluationContext(), variable, init, update, condition);
        }
        return null;
    }

    /*
    The code above currently only works when the loop variable is not modified inside the loop.

     */
    private DV loopVariableIsModified(StatementAnalysis statementAnalysis, Variable lvr) {
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
        if (range.isFinal()) {
            return range.get().conditions(evaluationContext);
        }
        CausesOfDelay causes = range.get().causesOfDelay();
        return DelayedExpression.forState(Identifier.state(evaluationContext.statementIndex()),
                evaluationContext.getPrimitives().booleanParameterizedType(),
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
    private Range computeRange(EvaluationContext evaluationContext,
                               VariableExpression loopVar,
                               Expression initialValue,
                               Expression updatedValue,
                               Expression condition) {
        if (initialValue instanceof IntConstant init && updatedValue instanceof IntConstant update) {
            int start = init.getValue();
            int increment = update.getValue();
            if (condition instanceof GreaterThanZero gt0) {
                GreaterThanZero.XB xb = gt0.extract(EvaluationResult.from(evaluationContext));
                if (loopVar.equals(xb.x())) {
                    int endExcl = (int) (xb.b() + (gt0.allowEquals() ? (increment < 0 ? -1 : 1) : 0));
                    if (xb.lessThan() && start < endExcl && increment > 0 || !xb.lessThan() && start > endExcl && increment < 0) {
                        Range r = new NumericRange(start, endExcl, increment, loopVar);
                        LOGGER.debug("Identified range {}", r);
                        return r;
                    }
                    // int i=10; i<10; i++      int i=10; i>=11; i--
                    if (xb.lessThan() && start >= endExcl || !xb.lessThan() && start < endExcl && increment < 0) {
                        return Range.EMPTY;
                    }
                    if (xb.lessThan() && increment < 0 || !xb.lessThan() && increment > 0) {
                        return Range.INFINITE_LOOP;
                    }
                    if (increment == 0) {
                        return Range.INFINITE_LOOP;
                    }
                }
            }
        }
        return Range.NO_RANGE;
    }
}
