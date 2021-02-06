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
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public record ForwardAnalysisInfo(FlowData.Execution execution, ConditionManager conditionManager,
                                  LocalVariableCreation catchVariable,
                                  Map<String, Expression> switchIdToLabels,
                                  Expression switchSelector,
                                  boolean switchSelectorIsDelayed) {

    public static ForwardAnalysisInfo startOfMethod(Primitives primitives) {
        return new ForwardAnalysisInfo(FlowData.Execution.ALWAYS, ConditionManager.initialConditionManager(primitives),
                null, null, null, false);
    }

    public ForwardAnalysisInfo startOfSwitchOldStyle(Map<String, Expression> switchIdToLabels, Expression switchSelector, boolean switchSelectorIsDelayed) {
        return new ForwardAnalysisInfo(execution, conditionManager, null, switchIdToLabels, switchSelector, switchSelectorIsDelayed);
    }

    public ForwardAnalysisInfo otherConditionManager(ConditionManager conditionManager) {
        return new ForwardAnalysisInfo(execution, conditionManager, catchVariable, switchIdToLabels, switchSelector, switchSelectorIsDelayed);
    }
}
