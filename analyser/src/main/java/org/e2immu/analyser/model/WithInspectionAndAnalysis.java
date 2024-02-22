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
import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util2.PackedIntMap;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.analysis.Analysis.AnnotationCheck.*;

/*
Cyclic dependency between TypeInfo and WithInspectionAndAnalysis, and between AnnotationExpression and WIAA.

 */
public interface WithInspectionAndAnalysis {

    @NotNull
    Identifier getIdentifier();

    @NotNull
    Inspection getInspection();

    boolean hasBeenInspected();

    @NotNull
    String name();

    @NotNull(content = true)
    default Optional<AnnotationExpression> hasInspectedAnnotation(AnnotationExpression annotationKey) {
        if (!hasBeenInspected()) return Optional.empty();
        String annotationFQN = annotationKey.typeInfo().fullyQualifiedName;
        return getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN)).findFirst();
    }

    @NotNull
    UpgradableBooleanMap<TypeInfo> typesReferenced();

    @NotNull
    PackedIntMap<TypeInfo> typesReferenced2();

    @NotNull
    TypeInfo primaryType();

    default Optional<Boolean> error(Analysis analysisBuilder, AnnotationExpression annotationKey) {
        Optional<AnnotationParameters> optAp = hasInspectedAnnotation(annotationKey)
                .map(AnnotationExpression::e2ImmuAnnotationParameters);
        if (optAp.isPresent()) {
            AnnotationParameters ap = optAp.get();
            if (ap.contract()) return Optional.empty(); // DO NOTHING, CONTRACTED
        }
        Optional<Boolean> mustBeAbsent = optAp.map(AnnotationParameters::isVerifyAbsent);
        AnnotationExpression actual = analysisBuilder.annotationGetOrDefaultNull(annotationKey);
        boolean implied = actual != null && actual.e2ImmuAnnotationParameters().implied();

        if (mustBeAbsent.isEmpty()) {
            // not in inspection
            if (!implied && actual != null) {
                analysisBuilder.putAnnotationCheck(annotationKey, COMPUTED);
            }
            return Optional.empty(); // no error, because no check!
        }
        if (!mustBeAbsent.get()) {
            // we expect the annotation to be present
            if (actual == null || actual.e2ImmuAnnotationParameters().absent()) {
                analysisBuilder.putAnnotationCheck(annotationKey, MISSING);
                return mustBeAbsent; // error!!!
            }
            // annotation is there
            analysisBuilder.putAnnotationCheck(annotationKey, implied ? IMPLIED : OK);
            return Optional.empty(); // no error
        }
        // we expect the annotation to be absent
        if (actual != null && !actual.e2ImmuAnnotationParameters().absent()) {
            analysisBuilder.putAnnotationCheck(annotationKey, PRESENT);
            return mustBeAbsent; // error
        }
        if (actual != null) {
            analysisBuilder.putAnnotationCheck(annotationKey, OK_ABSENT);
        } // else: no need to add anything
        return Optional.empty(); // no error, annotation is not there
    }

    @NotNull
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
        if (hasBeenAnalysed()) {
            for (AnnotationExpression annotation : annotations) {
                Analysis.AnnotationCheck annotationCheck = getAnalysis().getAnnotation(annotation);
                if (annotationCheck == MISSING) {
                    OutputBuilder outputBuilder = new OutputBuilder()
                            .add(Symbol.LEFT_BLOCK_COMMENT)
                            .add(annotation.output(qualification))
                            .add(Space.ONE_REQUIRED_EASY_SPLIT)
                            .add(new Text(annotationCheck.toString()))
                            .add(Symbol.RIGHT_BLOCK_COMMENT);
                    perAnnotation.add(outputBuilder);
                } else if (annotationCheck.isVisible()) {
                    OutputBuilder outputBuilder = new OutputBuilder().add(annotation.output(qualification));
                    if (annotationCheck.writeComment()) {
                        outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT).add(new Text(annotationCheck.toString()))
                                .add(Symbol.RIGHT_BLOCK_COMMENT);
                    }
                    perAnnotation.add(outputBuilder);
                }
            }
        }
        return perAnnotation.stream();
    }

    @NotNull
    TypeInfo getTypeInfo();

    default MethodInfo getMethod() {
        return null;
    }

    @NotNull
    Location newLocation();

    @NotNull
    CausesOfDelay delay(CauseOfDelay.Cause cause);
}
