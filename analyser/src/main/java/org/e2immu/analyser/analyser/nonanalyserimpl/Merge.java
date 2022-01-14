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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.GroupPropertyValues;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.List;
import java.util.Objects;

public record Merge(EvaluationContext evaluationContext,
                    VariableInfoContainer vic) {

    public enum Action {
        MERGE, REMOVE, IGNORE
    }

    public Expression merge(Expression stateOfDestination,
                            Expression postProcessState,
                            Expression overwriteValue,
                            boolean atLeastOneBlockExecuted,
                            List<ConditionAndVariableInfo> mergeSources,
                            GroupPropertyValues groupPropertyValues) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        VariableInfoContainerImpl vici = (VariableInfoContainerImpl) vic;

        assert vici.canMerge() : "For variable " + vic.getPreviousOrInitial().variable().fullyQualifiedName();

        Expression postProcess = activate(postProcessState, evaluationContext);

        VariableInfoImpl existing = vici.currentExcludingMerge();
        if (!vic.hasMerge()) {
            MergeHelper mergeHelper = new MergeHelper(evaluationContext, existing);
            VariableInfoImpl vii = mergeHelper.mergeIntoNewObject(stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted,
                    mergeSources, groupPropertyValues);
            vici.setMerge(vii);
        } else {
            MergeHelper mergeHelper = new MergeHelper(evaluationContext, vici.getMerge());
            mergeHelper.mergeIntoMe(stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted, existing,
                    mergeSources, groupPropertyValues);
        }
        return vici.getMerge().getValue();
    }

    // post-processing the state is only done under certain limited conditions, currently ONLY to
    // merge variables, defined outside the loop but assigned inside, back to the outside of the loop.
    private Expression activate(Expression postProcessState, EvaluationContext evaluationContext) {
        if (vic.variableNature() instanceof VariableNature.VariableDefinedOutsideLoop outside
                && outside.statementIndex().equals(evaluationContext.statementIndex())) {
            return postProcessState;
        }
        return null;
    }
}
