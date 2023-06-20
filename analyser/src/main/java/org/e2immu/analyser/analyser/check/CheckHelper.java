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

package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.e2immu.analyser.analysis.Analysis.AnnotationCheck.*;

public record CheckHelper(InspectionProvider inspectionProvider, E2ImmuAnnotationExpressions e2) {

    public static Message checkAnnotationWithValue(Analysis analysis,
                                                   AnnotationExpression annotationKey,
                                                   String parameterName,
                                                   Function<AnnotationExpression, String> extractInspected,
                                                   String computedValue,
                                                   List<AnnotationExpression> annotations,
                                                   Location where) {
        return checkAnnotationWithValue(analysis, annotationKey,
                List.of(new AnnotationKV(parameterName, extractInspected, computedValue)), annotations, where);
    }

    public record AnnotationKV(String parameterName,
                               Function<AnnotationExpression, String> extractInspected,
                               String computedValue,
                               boolean haveComputedValueCheckPresence) {
        public AnnotationKV(String parameterName,
                            Function<AnnotationExpression, String> extractInspected,
                            String computedValue) {
            this(parameterName, extractInspected, computedValue,
                    computedValue != null && !computedValue.isBlank());
        }
    }

    // also used by @Constant in CheckConstant, by @E1Immutable, @E2Immutable etc. in CheckEventual
    public static Message checkAnnotationWithValue(Analysis analysis,
                                                   AnnotationExpression annotationKey,
                                                   List<AnnotationKV> annotationKVs,
                                                   List<AnnotationExpression> annotations,
                                                   Location where) {
        AnnotationExpression inAnalysis = analysis.annotationGetOrDefaultNull(annotationKey);
        boolean implied = inAnalysis != null && inAnalysis.e2ImmuAnnotationParameters().implied();

        String annotationSimpleName = "@" + annotationKey.typeInfo().simpleName;
        Optional<AnnotationExpression> optInInspection = annotations.stream()
                .filter(ae -> ae.typeInfo().equals(annotationKey.typeInfo())).findFirst();

        // annotation is not present in inspection
        if (optInInspection.isEmpty()) {
            if (inAnalysis != null && !implied) {
                analysis.putAnnotationCheck(inAnalysis, COMPUTED);
            }
            return null;
        }

        // annotation is present in inspection
        AnnotationExpression annotation = optInInspection.get();
        boolean verifyAbsent = annotation.e2ImmuAnnotationParameters().isVerifyAbsent();

        if (verifyAbsent) {
            // annotation has to be absent
            if (inAnalysis != null) {
                // but it is there
                if (inAnalysis.e2ImmuAnnotationParameters().absent()) {
                    // but even in analysis, it says ABSENT...
                    analysis.putAnnotationCheck(inAnalysis, OK_ABSENT);
                    return null;
                }
                analysis.putAnnotationCheck(inAnalysis, Analysis.AnnotationCheck.PRESENT);
                return Message.newMessage(where, Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, annotationSimpleName);
            }

            // we'll have to add the value to the annotationChecks
            analysis.putAnnotationCheck(new AnnotationExpressionImpl(annotationKey.typeInfo(), List.of()),
                    Analysis.AnnotationCheck.OK_ABSENT);
            return null;
        }

        // annotation has not been computed
        if (inAnalysis == null) {
            analysis.putAnnotationCheck(new AnnotationExpressionImpl(annotationKey.typeInfo(), List.of()),
                    Analysis.AnnotationCheck.MISSING);
            return Message.newMessage(where, Message.Label.ANNOTATION_ABSENT, annotationSimpleName);
        }

        for (AnnotationKV kv : annotationKVs) {
            String requiredValue = kv.extractInspected.apply(optInInspection.get());
            if ((kv.computedValue == null) != (requiredValue == null) ||
                    kv.computedValue != null && !kv.computedValue.equals(requiredValue)) {
                analysis.putAnnotationCheck(inAnalysis, WRONG);
                return Message.newMessage(where, Message.Label.WRONG_ANNOTATION_PARAMETER,
                        "Annotation " + annotationSimpleName + ", parameter " + kv.parameterName + ", required "
                                + requiredValue + ", found " + kv.computedValue);
            }
        }
        analysis.putAnnotationCheck(inAnalysis, implied ? IMPLIED : OK);
        return null;
    }
}
