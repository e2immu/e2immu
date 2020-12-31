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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Or;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.FlipSwitch;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMapOverwriteNoValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StateData {

    /*
     precondition = conditions that cause an escape
     they are generated in the throws statement
     are copied upwards, and to the next statement
     */

    public final SetOnce<Expression> precondition = new SetOnce<>(); // set on statements of depth 1, ie., 0, 1, 2,..., not 0.0.0, 1.0.0
    public final SetOnce<ConditionManager> conditionManager = new SetOnce<>(); // the state as it is after evaluating the statement
    public final SetOnce<Expression> valueOfExpression = new SetOnce<>();
    public final FlipSwitch statementContributesToPrecondition = new FlipSwitch();

    public final SetOnceMapOverwriteNoValue<String> statesOfInterrupts;

    public StateData(boolean isLoop) {
        statesOfInterrupts = isLoop ? new SetOnceMapOverwriteNoValue<>() : null;
    }

    public AnalysisStatus copyPrecondition(StatementAnalyser statementAnalyser, StatementAnalysis previous, EvaluationContext evaluationContext) {
        if (!precondition.isSet()) {
            List<Expression> fromPrevious = previous == null ? List.of() : List.of(previous.stateData.precondition.get());
            List<Expression> fromBlocks = statementAnalyser.lastStatementsOfNonEmptySubBlocks().stream()
                    .map(sa -> sa.statementAnalysis.stateData.precondition.get())
                    .collect(Collectors.toUnmodifiableList());
            List<Expression> all = ListUtil.immutableConcat(fromBlocks, fromPrevious);
            Expression or = new Or(evaluationContext.getPrimitives()).append(evaluationContext, all);
            precondition.set(or);
        }
        return AnalysisStatus.DONE;
    }

    public ConditionManager getConditionManager() {
        return conditionManager.getOrElse(ConditionManager.DELAYED);
    }

    public Expression getValueOfExpression() {
        return valueOfExpression.getOrElse(EmptyExpression.NO_VALUE);
    }

    public void addStateOfInterrupt(String index, Expression expression) {
        statesOfInterrupts.put(index, expression);
    }

    public Stream<Expression> statesOfInterruptsStream() {
        return statesOfInterrupts.stream().map(Map.Entry::getValue);
    }

    public static record RemoveVariableFromState(EvaluationContext evaluationContext,
                                                 Variable variable) implements StatementAnalysis.StateChange {

        @Override
        public Expression apply(Expression value) {
            return ConditionManager.removeClausesInvolving(evaluationContext, value, variable, true);
        }
    }

}
