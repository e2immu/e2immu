package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.Optional;

public class CheckConstant {

    private final Primitives primitives;

    public CheckConstant(Primitives primitives) {
        this.primitives = primitives;
    }

    public void checkConstantForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Expression singleReturnValue = fieldAnalysis.getEffectivelyFinalValue() != null ?
                fieldAnalysis.getEffectivelyFinalValue() : EmptyExpression.NO_VALUE;
        checkConstant(messages,
                singleReturnValue,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }

    public void checkConstantForMethods(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Expression singleReturnValue = methodAnalysis.getSingleReturnValue();
        checkConstant(messages,
                singleReturnValue,
                methodInfo.methodInspection.get().getAnnotations(),
                new Location(methodInfo));
    }

    private void checkConstant(Messages messages, Expression singleReturnValue, List<AnnotationExpression> annotations, Location where) {
        Optional<AnnotationExpression> oConstant = annotations.stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        if (oConstant.isEmpty()) {
            return;
        }
        AnnotationExpression constant = oConstant.get();
        boolean verifyAbsent = constant.e2ImmuAnnotationParameters().isVerifyAbsent();
        String value = constant.extract("value", "");

        boolean haveConstantValue = singleReturnValue instanceof org.e2immu.analyser.model.Constant;
        if (verifyAbsent) {
            if (haveConstantValue) {
                messages.add(Message.newMessage(where, Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Constant"));
            }
            return;
        }
        if (!haveConstantValue) {
            messages.add(Message.newMessage(where, Message.ANNOTATION_ABSENT, "Constant"));
            return;
        }
        if (!value.equals(singleReturnValue.toString())) {
            messages.add(Message.newMessage(where, Message.WRONG_CONSTANT, "required " +
                    value + ", found " + singleReturnValue));
        }
    }


    public AnnotationExpression createConstantAnnotation(E2ImmuAnnotationExpressions typeContext, Expression value) {
        String constant = value.minimalOutput();
        Expression valueExpression = new MemberValuePair("stringValue", new StringConstant(primitives, constant));
        List<Expression> expressions = List.of(valueExpression);
        return new AnnotationExpressionImpl(typeContext.constant.typeInfo(), expressions);
    }
}
