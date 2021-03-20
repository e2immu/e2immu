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

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.output.TypeName;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.*;
import java.util.stream.Stream;

public interface WithInspectionAndAnalysis {

    Inspection getInspection();

    boolean hasBeenInspected();

    String name();

    Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation);

    UpgradableBooleanMap<TypeInfo> typesReferenced();

    TypeInfo primaryType();

    // byte code inspection + annotated APIs: hasBeenDefined on type == false
    // classes and enumerations with at least one field with initialiser or method with code block: hasBeenDefined == true
    // annotation classes: hasBeenDefined == false
    // interfaces: only for methods with code block, and initialisers, if the type has been defined

    default Boolean annotatedWith(Analysis analysis, AnnotationExpression annotation) {
        if (primaryType().shallowAnalysis()) {
            return getInspection().getAnnotations().stream()
                    .anyMatch(ae -> ae.typeInfo().fullyQualifiedName.equals(annotation.typeInfo().fullyQualifiedName));
        }
        return analysis.getAnnotation(annotation).isPresent();
    }

    default Optional<Boolean> error(AbstractAnalysisBuilder analysisBuilder, Class<?> annotation, AnnotationExpression expression) {
        Optional<Boolean> mustBeAbsent = hasInspectedAnnotation(annotation).map(AnnotationExpression::e2ImmuAnnotationParameters).map(AnnotationParameters::isVerifyAbsent);
        Boolean actual = analysisBuilder.annotations.getOtherwiseNull(expression);
        if (mustBeAbsent.isEmpty()) {
            analysisBuilder.annotationChecks.put(expression, actual == Boolean.TRUE ? Analysis.AnnotationCheck.COMPUTED :
                    Analysis.AnnotationCheck.OK_ABSENT);
            return Optional.empty(); // no error, because no check!
        }
        if (!mustBeAbsent.get()) {
            // we expect the annotation to be present
            if (actual == null || !actual) {
                analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.MISSING);
                return mustBeAbsent; // error!!!
            }
            analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.OK);
            return Optional.empty(); // no error
        }
        // we expect the annotation to be absent
        if (actual == Boolean.TRUE) {
            analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.PRESENT);
            return mustBeAbsent; // error
        }
        analysisBuilder.annotationChecks.put(expression, Analysis.AnnotationCheck.OK_ABSENT);
        return Optional.empty(); // no error, annotation is not there
    }

    String fullyQualifiedName();

    void setAnalysis(Analysis analysis);

    Analysis getAnalysis();

    boolean hasBeenAnalysed();

    default Stream<OutputBuilder> buildAnnotationOutput(Qualification qualification) {
        Set<AnnotationExpression> annotationDuplicateChecks = new HashSet<>();
        List<AnnotationExpression> annotations = new LinkedList<>();
        List<OutputBuilder> perAnnotation = new ArrayList<>();

        if (hasBeenAnalysed()) {
            // computed annotations get priority; they'll be commented on
            getAnalysis().getAnnotationStream()
                    .filter(e -> e.getValue().hasBeenComputed())
                    .forEach(e -> annotations.add(e.getKey()));
            annotationDuplicateChecks.addAll(annotations);
        }
        if (hasBeenInspected()) {
            // ignoring those that are already present; note that in the inspection, there can be multiple
            // annotations of the same type; they are definitely not ours, not computed.
            for (AnnotationExpression annotation : getInspection().getAnnotations()) {
                if (!annotationDuplicateChecks.contains(annotation)) {
                    annotations.add(annotation);
                }
            }
        }
        if(hasBeenAnalysed()) {
            for (AnnotationExpression annotation : annotations) {
                Analysis.AnnotationCheck annotationCheck = getAnalysis().getAnnotation(annotation);
                if (annotationCheck != Analysis.AnnotationCheck.ABSENT) {
                    OutputBuilder outputBuilder = new OutputBuilder().add(annotation.output(qualification));
                    if (annotationCheck.writeComment()) {
                        outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT).add(new Text(annotationCheck.toString()))
                                .add(Symbol.RIGHT_BLOCK_COMMENT);
                    }
                    perAnnotation.add(outputBuilder);
                }
            }
        }
        if (perAnnotation.size() > 1) {
            perAnnotation.sort(Comparator.comparing(a -> ((TypeName) a.get(1)).simpleName()));
        }
        return perAnnotation.stream();
    }
}
