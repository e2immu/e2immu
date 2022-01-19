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
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.List;
import java.util.Objects;

public record Merge(EvaluationContext evaluationContext,
                    VariableInfoContainer vic) {

    public enum Action {
        MERGE, REMOVE, IGNORE
    }

    public record ExpressionAndProperties(Expression expression, Properties valueProperties) {
        public ExpressionAndProperties {
            assert expression.isDelayed() ||
                    EvaluationContext.VALUE_PROPERTIES.stream().allMatch(p -> valueProperties.get(p).isDone());
        }
    }

    public void merge(Expression stateOfDestination,
                      Expression postProcessState,
                      ExpressionAndProperties overwriteValue,
                      boolean atLeastOneBlockExecuted,
                      List<ConditionAndVariableInfo> mergeSources,
                      GroupPropertyValues groupPropertyValues,
                      TranslationMap translationMap) {
        Objects.requireNonNull(mergeSources);
        Objects.requireNonNull(evaluationContext);
        VariableInfoContainerImpl vici = (VariableInfoContainerImpl) vic;

        // note: in case of a rename operation, the vici/vic .prevInitial.variable is the renamed variable
        assert vici.canMerge() : "For variable " + vic.getPreviousOrInitial().variable().fullyQualifiedName();

        Expression postProcess = activate(postProcessState, evaluationContext);

        VariableInfoImpl existing = vici.currentExcludingMerge();
        if (!vic.hasMerge()) {
            MergeHelper mergeHelper = new MergeHelper(evaluationContext, existing);
            VariableInfoImpl vii = mergeHelper.mergeIntoNewObject(stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted,
                    mergeSources, groupPropertyValues, translationMap);
            vici.setMerge(vii);
        } else {
            MergeHelper mergeHelper = new MergeHelper(evaluationContext, vici.getMerge());
            mergeHelper.mergeIntoMe(stateOfDestination,
                    postProcess, overwriteValue, atLeastOneBlockExecuted, existing,
                    mergeSources, groupPropertyValues, translationMap);
        }
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
