package org.e2immu.analyser.analyser.check;

import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.StringConstant;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.Constant;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.CONSTANT;
import static org.e2immu.analyser.util.Logger.log;

public class CheckConstant {

    /**
     * @param computedValue         the value as computed from the expression; null if there is no value
     *                              (a field without initialiser) in which case this method does not do anything
     *                              but returning whether the annotation was present or not.
     * @param type                  the type of the field or method return type
     * @param annotationExpressions the annotations on the field or method
     * @return if an @Constant annotation was present
     */
    public static boolean checkConstant(
            Value computedValue,
            ParameterizedType type,
            List<AnnotationExpression> annotationExpressions,
            BiConsumer<Value, String> onDifference) {

        Optional<AnnotationExpression> oConstant = annotationExpressions.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Constant.class.getName())).findFirst();
        return oConstant.map(constant -> {
            boolean testValue = constant.test();
            Value valueToTest = null;
            String typeMsg = "";
            if (isIntType(type)) {
                int value = constant.intValue();
                if (value != 0) testValue = true;
                valueToTest = new IntValue(value);
                typeMsg = "int";
            } else if (isBoolType(type)) {
                boolean value = constant.boolValue();
                if (value) testValue = true;
                valueToTest = BoolValue.of(value);
                typeMsg = "boolean";
            } else if (isStringType(type)) {
                String value = constant.stringValue();
                if (!"".equals(value)) testValue = true;
                valueToTest = value == null ? NullValue.NULL_VALUE : new StringValue(value);
                typeMsg = "string";
            } else throw new UnsupportedOperationException("TODO need more types in @Constant checks");
            if (computedValue != null && computedValue != UnknownValue.NO_VALUE) {
                if (testValue) {
                    log(CONSTANT, "Checking value {}, type {}", valueToTest, typeMsg);
                    if (!computedValue.equals(valueToTest)) {
                        onDifference.accept(valueToTest, typeMsg);
                    }
                }
            }

            return true;
        }).orElse(false);
    }

    private static boolean isType(ParameterizedType type, Set<String> typeNames) {
        return type.typeInfo != null && typeNames.contains(type.typeInfo.fullyQualifiedName);
    }

    private static boolean isIntType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.Integer", "int"));
    }

    private static boolean isBoolType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.Boolean", "boolean"));
    }

    private static boolean isStringType(ParameterizedType parameterizedType) {
        return isType(parameterizedType, Set.of("java.lang.String"));
    }

    public static AnnotationExpression createConstantAnnotation(TypeContext typeContext, Value value) {
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
