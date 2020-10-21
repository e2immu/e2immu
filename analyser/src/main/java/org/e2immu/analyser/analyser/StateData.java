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
import org.e2immu.analyser.model.abstractvalue.AndValue;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.statement.BreakOrContinueStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.model.statement.ThrowStatement;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.annotation.Modified;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StateData {

    // precondition = conditions that cause an escape
    public final SetOnce<Value> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0

    public final SetOnce<ConditionManager> conditionManager = new SetOnce<>(); // the state as it is after evaluating the statement

    public final SetOnce<Value> valueOfExpression = new SetOnce<>();

    public AnalysisStatus copyPrecondition(StatementAnalyser statementAnalyser, StatementAnalysis previous) {
        if (!precondition.isSet()) {
            Stream<Value> fromPrevious = Stream.of(previous == null ? UnknownValue.EMPTY : previous.stateData.precondition.get());
            Stream<Value> fromBlocks = statementAnalyser.lastStatementsOfSubBlocks().stream()
                    .map(sa -> sa.statementAnalysis.stateData.precondition.get());

            Value reduced = Stream.concat(fromBlocks, fromPrevious)
                    .reduce(UnknownValue.EMPTY, (v1, v2) -> new AndValue().append(v1, v2));
            precondition.set(reduced);
        }
        return AnalysisStatus.DONE;
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
