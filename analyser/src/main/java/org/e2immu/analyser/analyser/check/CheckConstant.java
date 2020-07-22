package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.Optional;

public class CheckConstant {

    private static class ToTest {
        Value valueToTest; // can be null, then we will not test a value, but test Constant
        boolean verifyAbsent;
    }

    private static ToTest constantAnnotationToVerify(List<AnnotationExpression> annotationExpressions) {
        Optional<AnnotationExpression> oConstant = annotationExpressions.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        return oConstant.map(constant -> {
            ToTest toTest = new ToTest();
            toTest.verifyAbsent = constant.isVerifyAbsent();
            boolean testExplicitly = constant.test();
            Boolean testBoolean = constant.extract("boolValue", null);
            if (testBoolean != null) {
                toTest.valueToTest = new BoolValue(testBoolean);
                if (testBoolean) testExplicitly = true;
            } else {
                Integer testInteger = constant.extract("intValue", null);
                if (testInteger != null) {
                    toTest.valueToTest = new IntValue(testInteger);
                    if (testInteger != 0) testExplicitly = true;
                } else {
                    String testString = constant.extract("stringValue", null);
                    if (testString != null) {
                        toTest.valueToTest = new StringValue(testString);
                        if (!testString.isEmpty()) testExplicitly = true;
                    }
                }
            }
            if (!testExplicitly) toTest.valueToTest = null;
            return toTest;
        }).orElse(null);
    }


    public static void checkConstantForFields(Messages messages, FieldInfo fieldInfo) {
        FieldAnalysis fieldAnalysis = fieldInfo.fieldAnalysis.get();
        Value singleReturnValue = fieldAnalysis.effectivelyFinalValue.isSet() ?
                fieldAnalysis.effectivelyFinalValue.get() : UnknownValue.NO_VALUE;
        checkConstant(messages,
                singleReturnValue,
                fieldInfo.fieldInspection.get().annotations,
                new Location(fieldInfo));
    }

    public static void checkConstantForMethods(Messages messages, MethodInfo methodInfo) {
        Value singleReturnValue = methodInfo.methodAnalysis.get().singleReturnValue.isSet() ?
                methodInfo.methodAnalysis.get().singleReturnValue.get() : UnknownValue.NO_VALUE;
        checkConstant(messages,
                singleReturnValue,
                methodInfo.methodInspection.get().annotations,
                new Location(methodInfo));
    }

    private static void checkConstant(Messages messages, Value singleReturnValue, List<AnnotationExpression> annotations, Location where) {

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
        if (toTest.valueToTest != null && !toTest.valueToTest.equals(singleReturnValue)) {
            messages.add(Message.newMessage(where, Message.WRONG_CONSTANT, "required " +
                    toTest.valueToTest + "' of type " + toTest.valueToTest.getClass().getSimpleName() +
                    ", found " + singleReturnValue));
        }
    }


    public static AnnotationExpression createConstantAnnotation(E2ImmuAnnotationExpressions typeContext, Value value) {
        Expression test;
        Expression valueExpression;
        Expression computed = typeContext.constant.get().expressions.get().get(0);
        if (value instanceof NumericValue || value instanceof StringConstant || value instanceof BoolValue) {
            test = new MemberValuePair("test", BooleanConstant.TRUE);
            if (value instanceof NumericValue) {
                int constant = value.toInt().value;
                valueExpression = new MemberValuePair("intValue", new IntConstant(constant));
            } else if (value instanceof BoolValue) {
                boolean constant = ((BoolValue) value).value;
                valueExpression = new MemberValuePair("boolValue", new BooleanConstant(constant));
            } else {
                String constant = ((StringConstant) value).getValue();
                valueExpression = new MemberValuePair("stringValue", new StringConstant(constant));
            }
        } else {
            test = null;
            valueExpression = null;
        }
        List<Expression> expressions = test == null ? List.of(computed) : List.of(computed, test, valueExpression);
        return AnnotationExpression.fromAnalyserExpressions(typeContext.constant.get().typeInfo, expressions);
    }
}
