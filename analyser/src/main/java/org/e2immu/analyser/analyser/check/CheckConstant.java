package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.analyser.AbstractAnalysisBuilder;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CheckConstant(Primitives primitives, E2ImmuAnnotationExpressions e2) {

    public void checkConstantForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Expression singleReturnValue = fieldAnalysis.getEffectivelyFinalValue() != null ?
                fieldAnalysis.getEffectivelyFinalValue() : EmptyExpression.NO_VALUE;
        checkConstant(messages, (AbstractAnalysisBuilder) fieldAnalysis,
                singleReturnValue,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }

    public void checkConstantForMethods(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Expression singleReturnValue = methodAnalysis.getSingleReturnValue();
        checkConstant(messages, (AbstractAnalysisBuilder) methodAnalysis,
                singleReturnValue,
                methodInfo.methodInspection.get().getAnnotations(),
                new Location(methodInfo));
    }

    private void checkConstant(Messages messages,
                               AbstractAnalysisBuilder analysis,
                               Expression singleReturnValue,
                               List<AnnotationExpression> annotations,
                               Location where) {
        Map.Entry<AnnotationExpression, Boolean> inAnalysis = analysis.annotations.stream()
                .filter(e -> e.getKey().typeInfo().fullyQualifiedName.equals(Constant.class.getName())
                        && e.getValue() == Boolean.TRUE).findFirst().orElse(null);

        Optional<AnnotationExpression> oConstant = annotations.stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        if (oConstant.isEmpty()) {
            if (inAnalysis != null) {
                analysis.annotationChecks.put(inAnalysis.getKey(), inAnalysis.getValue() == Boolean.TRUE ?
                        Analysis.AnnotationCheck.COMPUTED : Analysis.AnnotationCheck.ABSENT);
            }
            return;
        }
        AnnotationExpression constant = oConstant.get();
        boolean verifyAbsent = constant.e2ImmuAnnotationParameters().isVerifyAbsent();

        boolean haveConstantValue = singleReturnValue instanceof ConstantExpression;

        if (verifyAbsent) {
            if (haveConstantValue) {
                messages.add(Message.newMessage(where, Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Constant"));
                assert inAnalysis != null;
                analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.PRESENT);
            } else if (inAnalysis != null) {
                analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.OK_ABSENT);
                assert inAnalysis.getValue() != Boolean.TRUE;
            } else {
                // we'll have to add the value to the annotationChecks
                analysis.annotationChecks.put(new AnnotationExpressionImpl(e2.constant.typeInfo(), List.of()),
                        Analysis.AnnotationCheck.OK_ABSENT);
            }
            return;
        }
        if (!haveConstantValue) {
            messages.add(Message.newMessage(where, Message.ANNOTATION_ABSENT, "Constant"));
            analysis.annotationChecks.put(new AnnotationExpressionImpl(e2.constant.typeInfo(), List.of()),
                    Analysis.AnnotationCheck.MISSING);
            return;
        }
        assert inAnalysis != null;

        String value = constant.extract("value", "");
        String computed = singleReturnValue.minimalOutput();
        String required = singleReturnValue instanceof StringConstant ? StringUtil.quote(value) : value;
        if (!computed.equals(required)) {
            messages.add(Message.newMessage(where, Message.WRONG_CONSTANT, "required " +
                    required + ", found " + computed));
            analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.WRONG);
        } else {
            analysis.annotationChecks.put(inAnalysis.getKey(), Analysis.AnnotationCheck.OK);
        }
    }


    public AnnotationExpression createConstantAnnotation(E2ImmuAnnotationExpressions e2, Expression value) {
        // we want to avoid double ""
        String constant = value instanceof StringConstant stringConstant ? stringConstant.constant() : value.minimalOutput();
        Expression valueExpression = new MemberValuePair(new StringConstant(primitives(), constant));
        List<Expression> expressions = List.of(valueExpression);
        return new AnnotationExpressionImpl(e2.constant.typeInfo(), expressions);
    }
}
