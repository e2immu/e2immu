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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.impl.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.DelayedWrappedExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import static org.e2immu.analyser.analyser.Stage.MERGE;

public record Merge(EvaluationContext evaluationContext,
                    VariableInfoContainer vic,
                    CausesOfDelay executionDelay) {
    private static final Logger LOGGER = LoggerFactory.getLogger(Merge.class);

    public enum Action {
        MERGE, REMOVE, IGNORE
    }

    public record ExpressionAndProperties(Expression expression, Properties valueProperties) {
        public ExpressionAndProperties {
            assert expression.isDelayed() || expression.isNotYetAssigned() ||
                    EvaluationContext.VALUE_PROPERTIES.stream().allMatch(p -> valueProperties.get(p).isDone()) :
                    "Value properties are delayed for expression " + expression;
        }
    }

    public ProgressAndDelay merge(Expression stateOfDestination,
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

        VariableInfoImpl previousForValue = previousForValue(vici);
        VariableInfoImpl existingForValue = breakInitDelay(previousForValue);
        VariableInfoImpl existing = vici.currentExcludingMerge();
        if (!vic.hasMerge()) {
            MergeHelper mergeHelper = new MergeHelper(evaluationContext, existing, executionDelay);
            MergeHelper.MergeHelperResult mhr = mergeHelper.mergeIntoNewObject(stateOfDestination,
                    overwriteValue, atLeastOneBlockExecuted,
                    mergeSources, groupPropertyValues, translationMap, existingForValue);
            vici.setMerge(mhr.vii());
            return new ProgressAndDelay(true, mhr.progressAndDelay().causes());
        }
        MergeHelper mergeHelper = new MergeHelper(evaluationContext, vici.getMerge(), executionDelay);
        return mergeHelper.mergeIntoMe(stateOfDestination,
                overwriteValue, atLeastOneBlockExecuted, existing, existingForValue,
                mergeSources, groupPropertyValues, translationMap);
    }

    private VariableInfoImpl previousForValue(VariableInfoContainerImpl vici) {
        if (vici.hasEvaluation()) {
            VariableInfoImpl eval = vici.currentExcludingMerge();
            if (eval.isAssignedAt(evaluationContext().statementIndex())) {
                return eval;
            }
        }
        return (VariableInfoImpl) vici.getPreviousOrInitial();
    }

    private VariableInfoImpl breakInitDelay(VariableInfoImpl vii) {
        if (vii.variable() instanceof FieldReference fieldReference && vii.getValue().isDelayed()) {
            boolean selfReference = vii.getValue().causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.VALUES,
                    c -> c instanceof VariableCause vc && vc.variable().equals(fieldReference));
            if (selfReference) {
                LOGGER.debug("Detected self-reference in merge helper on variable field {}", fieldReference);
                Expression instance;
                Expression fromAnalysis = evaluationContext.getAnalyserContext()
                        .getFieldAnalysis(fieldReference.fieldInfo())
                        .getValueForStatementAnalyser(evaluationContext.getCurrentType().primaryType(),
                                fieldReference, evaluationContext.getFinalStatementTime());
                Properties properties;
                if (fromAnalysis.isDelayed()) {
                    instance = ConstantExpression.nullValue(evaluationContext.getAnalyserContext().getPrimitives(),
                            fromAnalysis.getIdentifier(),
                            fieldReference.fieldInfo().type.bestTypeInfo());
                    IsMyself isMyself = evaluationContext.isMyself(vii.variable());
                    properties = evaluationContext.valuePropertiesOfNullConstant(isMyself, fieldReference.parameterizedType());
                } else {
                    instance = fromAnalysis;
                    properties = evaluationContext.getValueProperties(instance);
                    // TODO explicitly not passing the properties -- is this ok?
                }
                assert instance.isDone();

                Expression wrapped = new DelayedWrappedExpression(Identifier.generate("wrapped in merge"),
                        vii.variable(), instance, properties, vii.getLinkedVariables(),
                        DelayFactory.createDelay(evaluationContext.getLocation(MERGE),
                                CauseOfDelay.Cause.BREAK_INIT_DELAY_IN_MERGE));
                vii.setValue(wrapped);
            }
        }
        return vii;
    }
}
