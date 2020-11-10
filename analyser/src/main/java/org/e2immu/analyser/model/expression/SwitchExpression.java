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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.SwitchValue;
import org.e2immu.analyser.model.statement.SwitchEntry;

import java.util.List;
import java.util.stream.Collectors;

public class SwitchExpression implements Expression {

    public final Expression selector;
    public final List<SwitchEntry> switchEntries;
    public final ParameterizedType returnType;

    public SwitchExpression(Expression selector, List<SwitchEntry> switchEntries, ParameterizedType returnType) {
        this.selector = selector;
        this.switchEntries = ImmutableList.copyOf(switchEntries);
        this.returnType = returnType;
    }

    @Override
    public ParameterizedType returnType() {
        return returnType;
    }

    // switch(selector) { case X, Y, Z -> expression or block; ...; default -> expression or block }
    @Override
    public String expressionString(int indent) {
        String blocks = switchEntries.stream().map(switchEntry -> switchEntry.statementString(indent + 4, null)).collect(Collectors.joining("\n"));
        return "switch(" + selector.expressionString(0) + ") {" + blocks + "}";
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
}
