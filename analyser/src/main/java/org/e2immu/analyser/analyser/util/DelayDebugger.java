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

    // field analyser
    String D_EXTERNAL_NOT_NULL = ".EXTERNAL_NOT_NULL";
    String D_VALUES = ".values";

    // method analyser
    String D_MODIFIED_METHOD = ".MODIFIED_METHOD";

    // statement analyser, MLD
    String D_CAUSES_OF_CONTENT_MODIFICATION_DELAY = ".causesOfContextModificationDelay";
    String D_COMBINED_PRECONDITION = ".combinedPrecondition";

    // companion analyser
    String D_COMPANION_METHOD = ".COMPANION_METHOD";

    // Type analyser
    String D_ASPECTS = ".aspects";
    String D_IMPLICITLY_IMMUTABLE_DATA = ".implicitlyImmutableData";

    // EvaluationResult.value()
    String D_EVALUATION_RESULT = ".evaluationResult";
}
