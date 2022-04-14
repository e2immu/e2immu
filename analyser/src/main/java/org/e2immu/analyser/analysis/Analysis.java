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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.Analyser;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.Stage;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/*
Principle of property computation, for 3 modes: aggregated, contracted, computed

In the case of contracted, we may not have information but the parent type/method/parameter
may have. Due to loading order, this parent may not be available yet.
For this reason, the final computation is done on-demand rather than hard-baked.
Unless the mode is computed, we should always be able to return a value, and never a delay.

 */
public interface Analysis {

    default Stream<Map.Entry<AnnotationExpression, AnnotationCheck>> getAnnotationStream() {
        return Stream.empty();
    }

    default void putAnnotationCheck(AnnotationExpression expression, AnnotationCheck missing) {
        throw new UnsupportedOperationException("Only in builder!");
    }

    default Boolean annotationGetOrDefaultNull(AnnotationExpression expression) {
        throw new UnsupportedOperationException("Only in builder!");
    }

    default Map.Entry<AnnotationExpression, Boolean> findAnnotation(String annotationFqn) {
        throw new UnsupportedOperationException("Only in builder!");
    }

    void internalAllDoneCheck();

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
    DV getProperty(Property property);

    // internal use, with obvious implementations in AbstractAnalysisBuilder and AnalysisImpl only
    @NotNull
    DV getPropertyFromMapDelayWhenAbsent(Property property);

    /**
     * internal use, with obvious implementations in AbstractAnalysisBuilder and AnalysisImpl only.
     * Reverts to <code>variableProperty.valueWhenAbsent</code> when no value present in map.
     */
    @NotNull
    DV getPropertyFromMapNeverDelay(Property property);

    @NotNull
    Location location(Stage stage);

    @NotNull
    @Modified
    Messages fromAnnotationsIntoProperties(
            Analyser.AnalyserIdentification analyserIdentification,
            boolean acceptVerifyAsContracted,
            Collection<AnnotationExpression> annotations,
            E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions);

    default Analysis build() {
        throw new UnsupportedOperationException();
    }

    /*
    Has to return a (new) modifiable map for efficiency reasons
     */
    default Map<Property, Integer> getProperties(Set<Property> forwardPropertiesOnParameters) {
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
    @NotNull
    default AnalysisMode analysisMode() {
        return AnalysisMode.CONTRACTED;
    }

    default boolean isComputed() {
        return analysisMode() == AnalysisMode.COMPUTED;
    }

    default boolean isNotContracted() {
        return analysisMode() != AnalysisMode.CONTRACTED;
    }

    default String markLabelFromType() {
        throw new UnsupportedOperationException();
    }
}
