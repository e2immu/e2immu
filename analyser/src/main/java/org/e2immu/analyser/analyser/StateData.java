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

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.value.AndValue;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.util.FlipSwitch;
import org.e2immu.analyser.util.SetOnce;

import java.util.stream.Stream;

public class StateData {

    // precondition = conditions that cause an escape
    public final SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0
    public final SetOnce<ConditionManager> conditionManager = new SetOnce<>(); // the state as it is after evaluating the statement
    public final SetOnce<Value> valueOfExpression = new SetOnce<>();
    public final FlipSwitch statementContributesToPrecondition = new FlipSwitch();

    public AnalysisStatus copyPrecondition(StatementAnalyser statementAnalyser, StatementAnalysis previous, EvaluationContext evaluationContext) {
        if (!precondition.isSet()) {
            Stream<Value> fromPrevious = Stream.of(previous == null ? UnknownValue.EMPTY : previous.stateData.precondition.get());
            Stream<Value> fromBlocks = statementAnalyser.lastStatementsOfNonEmptySubBlocks().stream()
                    .map(sa -> sa.statementAnalysis.stateData.precondition.get());

            Value reduced = Stream.concat(fromBlocks, fromPrevious)
                    .reduce(UnknownValue.EMPTY, (v1, v2) -> v1 == UnknownValue.EMPTY ? v2 : v2 == UnknownValue.EMPTY ? v1 :
                            new AndValue(evaluationContext.getPrimitives())
                                    .append(evaluationContext, v1, v2));
            precondition.set(reduced);
        }
        return AnalysisStatus.DONE;
    }

    public ConditionManager getConditionManager() {
        return conditionManager.getOrElse(ConditionManager.DELAYED);
    }

    public Value getValueOfExpression() {
        return valueOfExpression.getOrElse(UnknownValue.NO_VALUE);
    }

    public static class RemoveVariableFromState implements StatementAnalysis.StateChange {
        private final Variable variable;
        private final EvaluationContext evaluationContext;

        public RemoveVariableFromState(EvaluationContext evaluationContext, Variable variable) {
            this.variable = variable;
            this.evaluationContext = evaluationContext;
        }

        @Override
        public Value apply(Value value) {
            return ConditionManager.removeClausesInvolving(evaluationContext, value, variable, true);
        }
    }

}
