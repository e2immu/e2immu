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
    String D_CONTEXT_MODIFIED = ".CONTEXT_MODIFIED";
    String D_EXTERNAL_IMMUTABLE = ".EXTERNAL_IMMUTABLE";
    String D_EXTERNAL_NOT_NULL = ".EXTERNAL_NOT_NULL";
    String D_FINAL = ".FINAL";
    String D_MODIFIED_METHOD = ".MODIFIED_METHOD";

    // variable info
    String D_LINKED_VARIABLES_SET = ".linkedVariablesSet";

    // field analyser
    String D_VALUES = ".values";

    // method analyser
    String D_PRECONDITION_FOR_EVENTUAL = ".preconditionForEventual";

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
    String D_IMPLICITLY_IMMUTABLE_DATA = ".implicitlyImmutableData";

    // EvaluationResult.value()
    String D_EVALUATION_RESULT = ".evaluationResult";
}
