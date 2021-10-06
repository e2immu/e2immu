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

package org.e2immu.analyser.analyser.util;

import java.util.stream.Stream;

/**
 * All registration methods return a boolean so that they can be used as assert expressions.
 */
public interface DelayDebugger {

    default boolean foundDelay(String where, String delayFqn) {
        return false;
    }

    default boolean translatedDelay(String where, String delayFromFqn, String newDelayFqn) {
        return false;
    }

    default boolean createDelay(String where, String delayFqn) {
        return false;
    }

    Stream<DelayDebugNode> streamNodes();

    // common properties
    String D_IMMUTABLE = ".IMMUTABLE";
    String D_CONTEXT_IMMUTABLE = ".CONTEXT_IMMUTABLE";
    String D_CONTEXT_MODIFIED = ".CONTEXT_MODIFIED";
    String D_EXTERNAL_IMMUTABLE = ".EXTERNAL_IMMUTABLE";
    String D_EXTERNAL_NOT_NULL = ".EXTERNAL_NOT_NULL";
    String D_FINAL = ".FINAL";
    String D_MODIFIED_METHOD = ".MODIFIED_METHOD";

    // variable info
    String D_LINKED_VARIABLES_SET = ".linkedVariablesSet";
    String D_LINKED1_VARIABLES_SET = ".linked1VariablesSet";

    // field analyser
    String D_VALUES = ".values";

    // method analyser
    String D_PRECONDITION_FOR_EVENTUAL = ".preconditionForEventual";
    String D_METHOD_RETURN_VALUE = ".methodReturnValue";

    // statement analyser, state data
    String D_PRECONDITION = ".precondition";
    String D_VALUE_OF_EXPRESSION = ".valueOfExpression";
    String D_CONDITION_MANAGER_FOR_NEXT_STMT = ".conditionManagerForNextStatement";

    // statement analyser, MLD
    String D_CAUSES_OF_CONTENT_MODIFICATION_DELAY = ".causesOfContextModificationDelay";
    String D_COMBINED_PRECONDITION = ".combinedPrecondition";
    String D_LINKS_HAVE_BEEN_ESTABLISHED = ".linksHaveBeenEstablished";

    // companion analyser
    String D_COMPANION_METHOD = ".COMPANION_METHOD";

    // Type analyser
    String D_ASPECTS = ".aspects";
    String D_TRANSPARENT_TYPE = ".transparentType";
    String D_APPROVED_PRECONDITIONS_E1 = ".approvedPreconditionsE1";
    String D_APPROVED_PRECONDITIONS_E2 = ".approvedPreconditionsE2";

    // EvaluationResult.value()
    String D_EVALUATION_RESULT = ".evaluationResult";

    String D_LINKED_VARIABLES = ".linkedVariables";
    String D_LINKED1_VARIABLES = ".linkedVariables";
}

