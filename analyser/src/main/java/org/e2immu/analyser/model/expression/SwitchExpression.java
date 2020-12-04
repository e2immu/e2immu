/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.expression;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.statement.SwitchEntry;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Spacer;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;

public record SwitchExpression(Expression selector,
                               List<SwitchEntry> switchEntries,
                               ParameterizedType returnType) implements Expression {

    public SwitchExpression(Expression selector, List<SwitchEntry> switchEntries, ParameterizedType returnType) {
        this.selector = selector;
        this.switchEntries = ImmutableList.copyOf(switchEntries);
        this.returnType = returnType;
    }

    @Override
    public OutputBuilder output() {
        return new OutputBuilder().add(new Text("switch"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(selector.output())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(Symbol.LEFT_BRACE)
                .add(switchEntries.stream().map(SwitchEntry::output).collect(OutputBuilder.joining(Spacer.EASY)))
                .add(Symbol.RIGHT_BRACE);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public int precedence() {
        return 16; // TODO verify this is correct
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
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return null;
    }
}
