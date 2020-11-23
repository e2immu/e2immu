/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.lang.reflect.Array;
import java.util.*;


public record AnnotationExpressionImpl(TypeInfo typeInfo,
                                       List<Expression> expressions) implements AnnotationExpression {

    public AnnotationExpressionImpl {
        Objects.requireNonNull(typeInfo);
        Objects.requireNonNull(expressions);
    }

    // used by the byte code inspector, MyAnnotationVisitor
    public static class Builder {
        private TypeInfo typeInfo;
        private final List<Expression> expressions = new ArrayList<>();

        public Builder setTypeInfo(TypeInfo typeInfo) {
            this.typeInfo = typeInfo;
            return this;
        }

        public Builder addExpression(Expression expression) {
            this.expressions.add(expression);
            return this;
        }

        public AnnotationExpression build() {
            return new AnnotationExpressionImpl(typeInfo, ImmutableList.copyOf(expressions));
        }
    }

    public static AnnotationExpression inspect(ExpressionContext expressionContext, com.github.javaparser.ast.expr.AnnotationExpr ae) {
        List<Expression> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : ((NormalAnnotationExpr) ae).getPairs()) {
                Expression value = expressionContext.parseExpression(mvp.getValue());
                analyserExpressions.add(new MemberValuePair(mvp.getName().asString(), value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr) {
            Expression value = expressionContext.parseExpression(ae.asSingleMemberAnnotationExpr().getMemberValue());
            analyserExpressions = List.of(new MemberValuePair("value", value));
        } else analyserExpressions = List.of();
        TypeInfo typeInfo = (TypeInfo) expressionContext.typeContext.get(ae.getNameAsString(), true);
        return new AnnotationExpressionImpl(typeInfo, analyserExpressions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotationExpressionImpl that = (AnnotationExpressionImpl) o;
        return typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    public String stream() {
        StringBuilder sb = new StringBuilder("@" + typeInfo.simpleName);
        sb.append("(");
        boolean first = true;
        for (Expression expression : expressions) {
            if (first) first = false;
            else sb.append(", ");
            if (expression instanceof Constant) {
                sb.append(expression.expressionString(0));
            } else if (expression instanceof MemberValuePair memberValuePair) {
                if (!memberValuePair.name.equals("value")) {
                    sb.append(memberValuePair.name);
                    sb.append("=");
                }
                sb.append(memberValuePair.value.expressionString(0));
            }
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        if (!typeInfo.isJavaLang()) return Set.of(typeInfo.fullyQualifiedName);
        return Set.of();
    }

    @Override
    public <T> T extract(String fieldName, T defaultValue) {
        if (expressions.isEmpty()) return defaultValue;
        for (Expression expression : expressions) {
            ParameterizedType returnType = typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                    .filter(m -> m.name.equals(fieldName))
                    .findFirst()
                    .map(MethodInfo::returnType).orElseThrow();
            if (expression instanceof MemberValuePair mvp) {
                if (mvp.name.equals(fieldName)) {
                    return (T) returnValueOfAnnotationExpression(returnType, mvp.value);
                }
            } else if ("value".equals(fieldName)) {
                return (T) returnValueOfAnnotationExpression(returnType, expression);
            }
        }
        return defaultValue;
    }

    private static Object returnValueOfAnnotationExpression(ParameterizedType returnType, Expression expression) {
        // it is always possible that the return type is an array, but only one value is present...

        if (expression instanceof ArrayInitializer arrayInitializer) {
            Object[] array = createArray(arrayInitializer.returnType(), arrayInitializer.expressions.size());
            int i = 0;
            for (Expression element : arrayInitializer.expressions) {
                array[i++] = returnValueOfNonArrayExpression(arrayInitializer.returnType(), element);
            }
            return array;
        }

        Object value = returnValueOfNonArrayExpression(returnType, expression);
        if (returnType.arrays == 0) return value;
        Object[] array = createArray(returnType, 1);
        array[0] = value;
        return array;

    }

    private static Object returnValueOfNonArrayExpression(ParameterizedType returnType, Expression expression) {

        // normal "constant" or 123
        if (expression instanceof Constant) {
            return ((Constant<?>) expression).getValue();
        }

        // direct reference with import static
        if (expression instanceof VariableExpression && ((VariableExpression) expression).variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) (((VariableExpression) expression).variable)).fieldInfo;
            return enumInstance(returnType, fieldInfo.owner, fieldInfo.name);
        }

        // Type.CONSTANT
        if (expression instanceof FieldAccess fieldAccess) {
            if (fieldAccess.expression instanceof TypeExpression typeExpression) {
                return enumInstance(returnType, typeExpression.parameterizedType.typeInfo, fieldAccess.variable.simpleName());
            } else throw new UnsupportedOperationException("? did not expect " + fieldAccess.expression.getClass());
        }

        // -123
        if (expression instanceof UnaryOperator unaryOperator) {
            if (Primitives.isUnaryMinusOperatorInt(unaryOperator.operator) &&
                    unaryOperator.expression instanceof IntConstant intConstant) {
                return -intConstant.getValue();
            }
        }
        throw new UnsupportedOperationException("Not implemented: " + expression.getClass());
    }

    private static Object enumInstance(ParameterizedType type, TypeInfo observedType, String name) {
        if (type.typeInfo.typeInspection.get().typeNature() != TypeNature.ENUM) {
            throw new UnsupportedOperationException();
        }
        if (observedType != type.typeInfo) throw new UnsupportedOperationException("??");
        try {
            return Arrays.stream(Class.forName(observedType.fullyQualifiedName).getEnumConstants())
                    .filter(e -> e.toString().equals(name)).findFirst()
                    .orElseThrow(() -> new UnsupportedOperationException("Cannot find enum value " + name + " in type " + observedType.fullyQualifiedName));
        } catch (ClassNotFoundException e) {
            throw new UnsupportedOperationException("Cannot instantiate class " + observedType.fullyQualifiedName);
        }
    }

    private static Object[] createArray(ParameterizedType type, int size) {
        try {
            return (Object[]) Array.newInstance(Class.forName(type.typeInfo.fullyQualifiedName), size);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot instantiate class " + type.typeInfo.fullyQualifiedName);
        }
    }

    @Override
    public AnnotationExpression copyWith(Primitives primitives, String parameter, int value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new IntConstant(primitives, value));
        return new AnnotationExpressionImpl(typeInfo, List.of(memberValuePair));
    }

    @Override
    public AnnotationExpression copyWith(Primitives primitives, String parameter, boolean value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new BooleanConstant(primitives, value));
        return new AnnotationExpressionImpl(typeInfo, List.of(memberValuePair));
    }

    @Override
    public AnnotationExpression copyWith(Primitives primitives, String parameter, String value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new StringConstant(primitives, value));
        return new AnnotationExpressionImpl(typeInfo, List.of(memberValuePair));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(typeInfo, true);
    }

    @Override
    public AnnotationParameters parameters() {
        boolean absent = extract("absent", false);
        boolean contract = extract("contract", false);
        return new AnnotationParameters(absent, contract);
    }

}
