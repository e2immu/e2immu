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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.annotation.AnnotationMode;

import java.util.HashMap;
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

    /*
     public use, consistently implemented by delegating to getXXXProperty,
     (with XXX = Method, Parameter, Type, Field), which can be called with an AnalysisProvider

     getXXXProperty is then always implemented in XXXAnalysis
     */
    default int getProperty(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    // internal use, with obvious implementations in AbstractAnalysisBuilder and AnalysisImpl only
    default int getPropertyFromMapDelayWhenAbsent(VariableProperty variableProperty) {
        return Level.DELAY;
    }

    /**
     * internal use, with obvious implementations in AbstractAnalysisBuilder and AnalysisImpl only.
     * Reverts to <code>variableProperty.valueWhenAbsent(annotationMode())</code> when no value present in map.
     */
    default int getPropertyFromMapNeverDelay(VariableProperty variableProperty) {
        return variableProperty.valueWhenAbsent(annotationMode());
    }

    Location location();

    default AnnotationMode annotationMode() {
        return AnnotationMode.GREEN;
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

    /*
    Has to return a (new) modifiable map for efficiency reasons
     */
    default Map<VariableProperty, Integer> getProperties(Set<VariableProperty> forwardPropertiesOnParameters) {
        return new HashMap<>();
    }

    enum AnalysisMode {
        /**
         * Properties are contracted.
         * <p>
         * Byte code inspection/shallow analyser: all types, methods
         * Java parser: types: when nothing has code, and annotations. Methods: when no code
         * <p>
         * Absence of a property implies the default false value.
         */
        CONTRACTED,
        /**
         * Properties are generally computed by analysing the code block provided; they can be
         * contracted explicitly.
         * <p>
         * Byte code inspection: never
         * Java parser: as soon as one method has code, or a field has a non-constant initializer.
         * <p>
         * Means that absence of a property is typically interpreted as a delay.
         */
        COMPUTED,
        /**
         * Only in the case of abstract methods in a sealed type.
         * The properties are aggregated over the implementations/overrides of the abstract method.
         */
        AGGREGATED,
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
    default AnalysisMode analysisMode() {
        return AnalysisMode.CONTRACTED;
    }

    default boolean isComputed() {
        return analysisMode() == AnalysisMode.COMPUTED;
    }

    default boolean isNotContracted() {
        return analysisMode() != AnalysisMode.CONTRACTED;
    }
}
