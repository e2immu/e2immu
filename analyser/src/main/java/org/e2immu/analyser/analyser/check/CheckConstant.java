package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.NumericValue;
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

    private static record ToTest(String value, boolean verifyAbsent) {
    }

    private ToTest constantAnnotationToVerify(List<AnnotationExpression> annotationExpressions) {
        Optional<AnnotationExpression> oConstant = annotationExpressions.stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        return oConstant.map(constant -> {
            boolean verifyAbsent = constant.e2ImmuAnnotationParameters().isVerifyAbsent();
            String value = constant.extract("value", "");
            return new ToTest(value, verifyAbsent);
        }).orElse(null);
    }


    public void checkConstantForFields(Messages messages, FieldInfo fieldInfo, FieldAnalysis fieldAnalysis) {
        Value singleReturnValue = fieldAnalysis.getEffectivelyFinalValue() != null ?
                fieldAnalysis.getEffectivelyFinalValue() : UnknownValue.NO_VALUE;
        checkConstant(messages,
                singleReturnValue,
                fieldInfo.fieldInspection.get().getAnnotations(),
                new Location(fieldInfo));
    }

    public void checkConstantForMethods(Messages messages, MethodInfo methodInfo, MethodAnalysis methodAnalysis) {
        Value singleReturnValue = methodAnalysis.getSingleReturnValue();
        checkConstant(messages,
                singleReturnValue,
                methodInfo.methodInspection.get().getAnnotations(),
                new Location(methodInfo));
    }

    private void checkConstant(Messages messages, Value singleReturnValue, List<AnnotationExpression> annotations, Location where) {

        // NOTE: the reason we do not check @Constant in the same way is that there can be many types
        // of constants, and we have not yet provided them all in @Constant. At the same time,
        // singleReturnValue is used in expressions; this is faster and more reliable
        ToTest toTest = constantAnnotationToVerify(annotations);
        if (toTest == null) return;

        boolean haveConstantValue = singleReturnValue instanceof org.e2immu.analyser.model.Constant;
        if (toTest.verifyAbsent) {
            if (haveConstantValue) {
                messages.add(Message.newMessage(where, Message.ANNOTATION_UNEXPECTEDLY_PRESENT, "Constant"));
            }
            return;
        }
        if (!haveConstantValue) {
            messages.add(Message.newMessage(where, Message.ANNOTATION_ABSENT, "Constant"));
            return;
        }
        if (toTest.value.equals(singleReturnValue.toString())) {
            messages.add(Message.newMessage(where, Message.WRONG_CONSTANT, "required " +
                    toTest.value + ", found " + singleReturnValue));
        }
    }


    public AnnotationExpression createConstantAnnotation(E2ImmuAnnotationExpressions typeContext, Value value) {
        Expression test;
        Expression valueExpression;
        Expression computed = typeContext.constant.expressions().get(0);
        if (value instanceof NumericValue || value instanceof StringConstant || value instanceof BoolValue) {
            test = new MemberValuePair("test", new BooleanConstant(primitives, true));
            if (value instanceof NumericValue) {
                int constant = value.toInt().value;
                valueExpression = new MemberValuePair("intValue", new IntConstant(primitives, constant));
            } else if (value instanceof BoolValue) {
                boolean constant = ((BoolValue) value).value;
                valueExpression = new MemberValuePair("boolValue", new BooleanConstant(primitives, constant));
            } else {
                String constant = ((StringConstant) value).getValue();
                valueExpression = new MemberValuePair("stringValue", new StringConstant(primitives, constant));
            }
        } else {
            test = null;
            valueExpression = null;
        }
        List<Expression> expressions = test == null ? List.of(computed) : List.of(computed, test, valueExpression);
        return new AnnotationExpressionImpl(typeContext.constant.typeInfo(), expressions);
    }
}
