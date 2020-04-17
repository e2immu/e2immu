/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.analyser.TypeAnalyser;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.util.SetUtil;

import java.util.Set;

public class ConditionalValue implements Value {

    public final Value condition;
    public final Value ifTrue;
    public final Value ifFalse;

    public ConditionalValue(Value condition, Value ifTrue, Value ifFalse) {
        this.condition = condition;
        this.ifFalse = ifFalse;
        this.ifTrue = ifTrue;
    }

    @Override
    public int compareTo(Value o) {
        return 0;
    }

    @Override
    public String asString() {
        return null;
    }

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        Boolean t = ifTrue.isNotNull(evaluationContext.child(condition, null));
        Boolean f = ifFalse.isNotNull(evaluationContext.child(NegatedValue.negate(condition), null));
        return TypeAnalyser.TERNARY_AND.apply(t, f);
    }

    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        return SetUtil.immutableUnion(ifTrue.linkedVariables(evaluationContext), ifFalse.linkedVariables(evaluationContext));
    }
}
