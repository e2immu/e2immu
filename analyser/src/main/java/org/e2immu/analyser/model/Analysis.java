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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.annotation.AnnotationMode;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public interface Analysis {

    default Stream<Map.Entry<AnnotationExpression, AnnotationCheck>> getAnnotationStream() {
        return Stream.empty();
    }

    /*
    three states of computed:
    (1) present computed correctly
    (2) present, computed wrongly
    (3) explicitly absent, or absent because not computed
     */
    enum AnnotationCheck {
        OK, // expected present, and computed correctly;
        OK_ABSENT, //  expected absent, and computed absent
        WRONG, // expected present, computed, but not correctly
        MISSING, // expected present, not computed or explicitly absent
        PRESENT, // expected absent, but still computed (correctly or not, we cannot know)

        CONTRACTED, // demanded to be present, no point in computing
        CONTRACTED_ABSENT, // demanded to be absent, no point in computing

        ABSENT, // computed to be absent, not in inspection; fully invisible
        COMPUTED, // computed to be present, not in inspection

        NO_INFORMATION; // not one of our annotations, no need to comment upon

        public boolean hasBeenComputed() {
            return this == OK || this == WRONG || this == PRESENT || this == COMPUTED;
        }

        public Boolean isPresent() {
            return this == OK || this == WRONG || this == PRESENT || this == NO_INFORMATION || this == CONTRACTED || this == COMPUTED;
        }

        public boolean isVisible() {
            return this != ABSENT;
        }

        public boolean writeComment() {
            return this != CONTRACTED && this != CONTRACTED_ABSENT && this != NO_INFORMATION && this != COMPUTED;
        }
    }

    default AnnotationCheck getAnnotation(AnnotationExpression annotationExpression) {
        return AnnotationCheck.NO_INFORMATION;
    }

    default int getProperty(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    Location location();

    default AnnotationMode annotationMode() {
        return AnnotationMode.DEFENSIVE;
    }

    default OutputBuilder peekIntoAnnotations(AnnotationExpression annotation, Set<TypeInfo> annotationsSeen) {
        AnnotationParameters parameters = annotation.e2ImmuAnnotationParameters();
        OutputBuilder outputBuilder = new OutputBuilder();
        if (parameters != null) {
            if (!parameters.contract()) {
                AnnotationCheck annotationCheck = getAnnotation(annotation);
                outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                        .add(new Text(annotationCheck.toString()))
                        .add(Symbol.RIGHT_BLOCK_COMMENT);
                if (annotationCheck.hasBeenComputed()) {
                    // no need to add our computed value
                    annotationsSeen.add(annotation.typeInfo());
                }
            } else {
                // contract, not absent -> no need to add our computed value
                if (!parameters.absent()) annotationsSeen.add(annotation.typeInfo());
            }
        }
        return outputBuilder;
    }

    default Analysis build() {
        throw new UnsupportedOperationException();
    }

    default Map<VariableProperty, Integer> getProperties(Set<VariableProperty> forwardPropertiesOnParameters) {
        return Map.of();
    }

    default int getPropertyAsIs(VariableProperty variableProperty) {
        return getProperty(variableProperty);
    }

    default int internalGetProperty(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    /**
     * Helps to decide whether absence of a property must equal a delay. If the analyser is actively going over the method's code,
     * then this method has to return true.
     * <p>
     * Note that in the annotated APIs, a method has a method analysis builder even if it is not being analysed, but its companion methods
     * are.
     *
     * @return true when the method is being analysed, irrespective of whether its companion methods are being analysed.
     */
    default boolean isBeingAnalysed() {
        return false;
    }
}
