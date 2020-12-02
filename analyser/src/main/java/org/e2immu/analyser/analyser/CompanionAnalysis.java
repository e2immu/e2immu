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

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;

public interface CompanionAnalysis {

    AnnotationParameters getAnnotationType();

    /**
     * @return the value that represents the companion.
     */
    Expression getValue();

    /**
     * The variable value referring to the "pre" aspect variable.
     * This value is part of the getValue() value.
     * We provide it to facilitate re-evaluation.
     *
     * @return NO_VALUE when there is none
     */
    Expression getPreAspectVariableValue();

    /**
     * The values of the parameters, part of the getValue() value.
     * We provide them to facilitate re-evaluation.
     *
     * @return a list of parameters, never null.
     */
    List<Expression> getParameterValues();

    /**
     * Re-evaluate the companion method with concrete parameters and object
     *
     * @param evaluationContext the evaluation context
     * @return a re-evaluated Value
     */
    default Expression reEvaluate(EvaluationContext evaluationContext, List<Expression> parameterValues) {
        ImmutableMap.Builder<Expression, Expression> translationMap = new ImmutableMap.Builder<>();
        ListUtil.joinLists(getParameterValues(), parameterValues).forEach(pair -> translationMap.put(pair.k, pair.v));
        return getValue().reEvaluate(evaluationContext, translationMap.build()).value;
    }
}
