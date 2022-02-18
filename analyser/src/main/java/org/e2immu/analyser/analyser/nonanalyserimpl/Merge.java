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
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.ConditionAndVariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.DelayedWrappedExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.VariableNature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public record Merge(EvaluationContext evaluationContext,
                    VariableInfoContainer vic) {
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

        VariableInfoImpl existing = breakInitDelay(vici);
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

    private VariableInfoImpl breakInitDelay(VariableInfoContainerImpl vici) {
        VariableInfoImpl vii = vici.currentExcludingMerge();
        if (vii.variable() instanceof FieldReference fieldReference && vii.getValue().isDelayed()) {
            boolean selfReference = vii.getValue().causesOfDelay().causesStream()
                    .anyMatch(c -> (c.cause() == CauseOfDelay.Cause.VALUES)
                            && c instanceof VariableCause vc
                            && vc.variable().equals(fieldReference));
            if (selfReference) {
                LOGGER.debug("Detected self-reference in merge helper on variable field {}", fieldReference);
                Expression instance;
                Expression fromAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(fieldReference.fieldInfo)
                        .getValueForStatementAnalyser(fieldReference, evaluationContext.getFinalStatementTime());
                Properties properties;
                if (fromAnalysis.isDelayed()) {
                    // if (evaluationContext.getCurrentMethod().getMethodInfo().isConstructor && !fieldReference.isStatic) {
                    instance = ConstantExpression.nullValue(evaluationContext.getAnalyserContext().getPrimitives(),
                            fieldReference.fieldInfo.type.bestTypeInfo());
                    properties = evaluationContext.valuePropertiesOfNullConstant(fieldReference.parameterizedType);
                    //  } else {
                    //     instance = ;
                    //  }
                } else {
                    instance = fromAnalysis;
                    properties = evaluationContext.getValueProperties(instance);
                    // TODO explicitly not passing the properties -- is this ok?
                }
                assert instance.isDone();
                Expression wrapped = new DelayedWrappedExpression(Identifier.generate(),
                        instance,
                        vii, new SimpleSet(evaluationContext.getLocation(), CauseOfDelay.Cause.BREAK_INIT_DELAY_IN_MERGE));
                vii.setProperty(Property.IMMUTABLE_BREAK, properties.get(Property.IMMUTABLE));
                vii.setProperty(Property.NOT_NULL_BREAK, properties.get(Property.NOT_NULL_EXPRESSION));
                vii.setValue(wrapped);
            }
        }
        return vii;
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
