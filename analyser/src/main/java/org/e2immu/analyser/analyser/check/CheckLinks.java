package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Linked;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record CheckLinks(Primitives primitives, E2ImmuAnnotationExpressions e2) {

    public AnnotationExpression createLinkAnnotation(E2ImmuAnnotationExpressions typeContext, Set<Variable> links) {
        List<Expression> linkNameList = links.stream().map(variable -> new StringConstant(primitives,
                variable.nameInLinkedAnnotation())).collect(Collectors.toList());
        Expression linksStringArray = new MemberValuePair("to",
                new ArrayInitializer(primitives, ObjectFlow.NO_FLOW, linkNameList, primitives.stringParameterizedType));
        List<Expression> expressions = List.of(linksStringArray);
        return new AnnotationExpressionImpl(typeContext.linked.typeInfo(), expressions);
    }

    public void checkLinksForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysisImpl.Builder fieldAnalysis) {
        Function<AnnotationExpression, String> extractInspected = ae -> {
            String[] inspected = ae.extract("to", new String[]{});
            return Arrays.stream(inspected).sorted().collect(Collectors.joining(","));
        };
        LinkedVariables linkedVariables = fieldAnalysis.getLinkedVariables();
        String computedString = linkedVariables.isEmpty() ? null : linkedVariables.variables().stream()
                .map(Variable::nameInLinkedAnnotation)
                .sorted().collect(Collectors.joining(","));

        checkAnnotationWithValue(messages,
                fieldAnalysis,
                Linked.class.getName(),
                "@Linked",
                e2.linked.typeInfo(),
                extractInspected,
                computedString,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }

    // also used by @Constant in CheckConstant
    public static void checkAnnotationWithValue(Messages messages,
                                                AbstractAnalysisBuilder analysis,
                                                String annotationFqn,
                                                String annotationSimpleName,
                                                TypeInfo annotationTypeInfo,
                                                Function<AnnotationExpression, String> extractInspected,
                                                String computedValue,
                                                List<AnnotationExpression> annotations,
                                                Location where) {
        Map.Entry<AnnotationExpression, Boolean> inAnalysis = analysis.annotations.stream()
                .filter(e -> e.getKey().typeInfo().fullyQualifiedName.equals(annotationFqn)
                        && e.getValue() == Boolean.TRUE).findFirst().orElse(null);

        Optional<AnnotationExpression> optAnnotationInInspection = annotations.stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFqn)).findFirst();
        if (optAnnotationInInspection.isEmpty()) {
            if (inAnalysis != null) {
                analysis.annotationChecks.put(inAnalysis.getKey(), inAnalysis.getValue() == Boolean.TRUE ?
                        Analysis.AnnotationCheck.COMPUTED : Analysis.AnnotationCheck.ABSENT);
            }
            return;
        }
        AnnotationExpression annotation = optAnnotationInInspection.get();
        boolean verifyAbsent = annotation.e2ImmuAnnotationParameters().isVerifyAbsent();

        if (verifyAbsent) {
            if (computedValue != null) {
                messages.add(Message.newMessage(where, Message.ANNOTATION_UNEXPECTEDLY_PRESENT, annotationSimpleName));
                assert inAnalysis != null;
                analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.PRESENT);
            } else if (inAnalysis != null) {
                analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.OK_ABSENT);
                assert inAnalysis.getValue() != Boolean.TRUE;
            } else {
                // we'll have to add the value to the annotationChecks
                analysis.annotationChecks.put(new AnnotationExpressionImpl(annotationTypeInfo, List.of()),
                        Analysis.AnnotationCheck.OK_ABSENT);
            }
            return;
        }
        if (computedValue == null) {
            messages.add(Message.newMessage(where, Message.ANNOTATION_ABSENT, annotationSimpleName));
            analysis.annotationChecks.put(new AnnotationExpressionImpl(annotationTypeInfo, List.of()),
                    Analysis.AnnotationCheck.MISSING);
            return;
        }
        assert inAnalysis != null;

        String requiredValue = extractInspected.apply(optAnnotationInInspection.get());

        if (!computedValue.equals(requiredValue)) {
            messages.add(Message.newMessage(where, Message.WRONG_ANNOTATION_PARAMETER,
                    "Annotation " + annotationSimpleName + ", required " +
                            requiredValue + ", found " + computedValue));
            analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.WRONG);
        } else {
            analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.OK);
        }
    }
}
