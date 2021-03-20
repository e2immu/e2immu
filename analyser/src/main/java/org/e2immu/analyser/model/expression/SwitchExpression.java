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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.*;

import java.util.List;
import java.util.Objects;

public record SwitchExpression(Expression selector,
                               List<SwitchEntry> switchEntries,
                               ParameterizedType returnType,
                               ObjectFlow objectFlow) implements Expression {

    public SwitchExpression {
        if (hasBeenEvaluated()) {
            if (switchEntries.size() <= 2) {
                throw new IllegalArgumentException("Expect at least 3 entries to have a bit of a reasonable switch value");
            }
            switchEntries.forEach(e -> {
                Objects.requireNonNull(e.switchVariableAsExpression); // FIXME
                Objects.requireNonNull(e.labels);
                if (e.labels.contains(EmptyExpression.EMPTY_EXPRESSION) && e.labels.size() != 1)
                    throw new UnsupportedOperationException();
                if (e.labels.isEmpty()) throw new UnsupportedOperationException();
            });
        }
    }

    public SwitchExpression(Expression selector, List<SwitchEntry> switchEntries, ParameterizedType returnType) {
        this(selector, switchEntries, returnType, ObjectFlow.NYE);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(selector.output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(switchEntries.stream().map(switchEntry -> switchEntry.output(qualification))
                        .collect(OutputBuilder.joining(Space.ONE_IS_NICE_EASY_SPLIT, Symbol.LEFT_BRACE,
                                Symbol.RIGHT_BRACE, Guide.generatorForBlock())));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY; // TODO verify this is correct
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException("NYI");
        //return SwitchValue.switchValue(evaluationContext, selectorValue, entries, objectFlow);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return null;
    }
}
