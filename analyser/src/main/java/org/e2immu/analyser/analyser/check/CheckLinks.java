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

import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.annotation.Linked;
import org.e2immu.annotation.Linked1;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record CheckLinks(InspectionProvider inspectionProvider, E2ImmuAnnotationExpressions e2) {

    public AnnotationExpression createLinkAnnotation(TypeInfo typeInfo, Set<Variable> links) {
        List<Expression> linkNameList = links.stream().map(variable -> new StringConstant(inspectionProvider.getPrimitives(),
                variable.nameInLinkedAnnotation())).collect(Collectors.toList());
        MemberValuePair linksStringArray = new MemberValuePair("to",
                new ArrayInitializer(Identifier.generate("link annot"),
                        inspectionProvider, linkNameList, inspectionProvider.getPrimitives().stringParameterizedType()));
        List<MemberValuePair> expressions = List.of(linksStringArray);
        return new AnnotationExpressionImpl(typeInfo, expressions);
    }

    public Message checkLinksForFields(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String[] inspected = ae.extract("to", new String[]{});
            return Arrays.stream(inspected).sorted().collect(Collectors.joining(","));
        };
        LinkedVariables linkedVariables = fieldAnalysis.getLinkedVariables();
        String computedString = linkedVariables.variablesAssignedOrDependent()
                .map(Variable::nameInLinkedAnnotation)
                .sorted().collect(Collectors.joining(","));

        return checkAnnotationWithValue(
                fieldAnalysis,
                Linked.class.getName(),
                "@Linked",
                e2.linked.typeInfo(),
                extractInspected,
                computedString,
                fieldInfo.fieldInspection.get().getAnnotations(),
                fieldInfo.newLocation());
    }


    public Message checkLink1sForFields(FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String[] inspected = ae.extract("to", new String[]{});
            return Arrays.stream(inspected).sorted().collect(Collectors.joining(","));
        };
        LinkedVariables linkedVariables = fieldAnalysis.getLinkedVariables();
        String computedString = linkedVariables.independent1Variables()
                .map(Variable::nameInLinkedAnnotation)
                .sorted().collect(Collectors.joining(","));

        return checkAnnotationWithValue(
                fieldAnalysis,
                Linked1.class.getName(),
                "@Linked1",
                e2.linked1.typeInfo(),
                extractInspected,
                computedString.isEmpty() ? null : computedString,
                fieldInfo.fieldInspection.get().getAnnotations(),
                fieldInfo.newLocation());
    }

    public static Message checkAnnotationWithValue(Analysis analysis,
                                                   String annotationFqn,
                                                   String annotationSimpleName,
                                                   TypeInfo annotationTypeInfo,
                                                   Function<AnnotationExpression, String> extractInspected,
                                                   String computedValue,
                                                   List<AnnotationExpression> annotations,
                                                   Location where) {
        return checkAnnotationWithValue(analysis, annotationFqn, annotationSimpleName, annotationTypeInfo,
                List.of(new AnnotationKV(extractInspected, computedValue)), annotations, where);
    }

    public record AnnotationKV(Function<AnnotationExpression, String> extractInspected,
                               String computedValue,
                               boolean haveComputedValueCheckPresence) {
        public AnnotationKV(Function<AnnotationExpression, String> extractInspected,
                            String computedValue) {
            this(extractInspected, computedValue, computedValue != null && !computedValue.isBlank());
        }
    }

    // also used by @Constant in CheckConstant, by @E1Immutable, @E2Immutable etc. in CheckEventual
    public static Message checkAnnotationWithValue(Analysis analysis,
                                                   String annotationFqn,
                                                   String annotationSimpleName,
                                                   TypeInfo annotationTypeInfo,
                                                   List<AnnotationKV> annotationKVs,
                                                   List<AnnotationExpression> annotations,
                                                   Location where) {
        Map.Entry<AnnotationExpression, Boolean> inAnalysis = analysis.findAnnotation(annotationFqn);

        Optional<AnnotationExpression> optAnnotationInInspection = annotations.stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFqn)).findFirst();
        if (optAnnotationInInspection.isEmpty()) {
            if (inAnalysis != null) {
                analysis.putAnnotationCheck(inAnalysis.getKey(), inAnalysis.getValue() == Boolean.TRUE ?
                        Analysis.AnnotationCheck.COMPUTED : Analysis.AnnotationCheck.ABSENT);
            }
            return null;
        }
        AnnotationExpression annotation = optAnnotationInInspection.get();
        boolean verifyAbsent = annotation.e2ImmuAnnotationParameters().isVerifyAbsent();

        if (verifyAbsent) {
            boolean haveComputedValue = annotationKVs.stream().anyMatch(kv -> kv.haveComputedValueCheckPresence);

            if (haveComputedValue || inAnalysis != null && inAnalysis.getValue() == Boolean.TRUE) {
                assert inAnalysis != null;
                analysis.putAnnotationCheck(inAnalysis.getKey(), Analysis.AnnotationCheck.PRESENT);
                return Message.newMessage(where, Message.Label.ANNOTATION_UNEXPECTEDLY_PRESENT, annotationSimpleName);
            } else if (inAnalysis != null) {
                analysis.putAnnotationCheck(inAnalysis.getKey(), Analysis.AnnotationCheck.OK_ABSENT);
                assert inAnalysis.getValue() != Boolean.TRUE;
            } else {
                // we'll have to add the value to the annotationChecks
                analysis.putAnnotationCheck(new AnnotationExpressionImpl(annotationTypeInfo, List.of()),
                        Analysis.AnnotationCheck.OK_ABSENT);
            }
            return null;
        }
        if (inAnalysis == null) {
            analysis.putAnnotationCheck(new AnnotationExpressionImpl(annotationTypeInfo, List.of()),
                    Analysis.AnnotationCheck.MISSING);
            return Message.newMessage(where, Message.Label.ANNOTATION_ABSENT, annotationSimpleName);
        }

        for (AnnotationKV kv : annotationKVs) {
            String requiredValue = kv.extractInspected.apply(optAnnotationInInspection.get());
            if ((kv.computedValue == null) != (requiredValue == null) ||
                    kv.computedValue != null && !kv.computedValue.equals(requiredValue)) {
                analysis.putAnnotationCheck(inAnalysis.getKey(), Analysis.AnnotationCheck.WRONG);
                return Message.newMessage(where, Message.Label.WRONG_ANNOTATION_PARAMETER,
                        "Annotation " + annotationSimpleName + ", required " +
                                requiredValue + ", found " + kv.computedValue);
            }
        }
        analysis.putAnnotationCheck(inAnalysis.getKey(), Analysis.AnnotationCheck.OK);
        return null;
    }
}
