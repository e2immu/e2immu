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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.Modified;

import java.util.function.Function;

public class StateData {

    // precondition = conditions that cause an escape
    public final SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0

    public final SetOnce<ConditionManager> conditionManager = new SetOnce<>(); // the state as it is after evaluating the statement

    public final SetOnce<Value> valueOfExpression = new SetOnce<>();

    @Modified
    public AnalysisStatus apply(EvaluationContext evaluationContext, EvaluationResult evaluationResult) {
        AnalysisStatus result = AnalysisStatus.DONE;
        if (evaluationResult.value != UnknownValue.NO_VALUE) {
            valueOfExpression.set(evaluationResult.value);
        } else {
            result = AnalysisStatus.DELAYS;
        }

        // state changes get composed into one big operation, applied, and the result is set
        // the condition is copied from the evaluation context
        ConditionManager current = evaluationContext.getConditionManager();
        Function<Value, Value> composite = evaluationResult.getStateChangeStream()
                .reduce(v -> v, (f1, f2) -> v -> f2.apply(f1.apply(v)));
        Value reducedState = composite.apply(current.state);
        if (reducedState != UnknownValue.NO_VALUE) {
            conditionManager.set(new ConditionManager(current.condition, reducedState));
        } else {
            result = AnalysisStatus.DELAYS;
        }


        return result;
    }

    public static class RemoveVariableFromState implements StatementAnalysis.StateChange {
        private final Variable variable;

        public RemoveVariableFromState(Variable variable) {
            this.variable = variable;
        }

        @Override
        public Value apply(Value value) {
            return ConditionManager.removeClausesInvolving(value, variable, true);
        }
    }

}
