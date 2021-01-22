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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.TypeName;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.lang.reflect.Array;
import java.util.*;


public record AnnotationExpressionImpl(TypeInfo typeInfo,
                                       List<Expression> expressions) implements AnnotationExpression {

    public static final String ORG_E_2_IMMU_ANNOTATION = "org.e2immu.annotation";

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

    @Override
    public OutputBuilder output() {
        OutputBuilder outputBuilder = new OutputBuilder().add(Symbol.AT).add(new TypeName(typeInfo));
        if (!expressions.isEmpty()) {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(expressions.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        return outputBuilder;
    }

    @Override
    public String toString() {
        return output().toString();
    }

    @Override
    public Set<String> imports() {
        if (Primitives.isNotJavaLang(typeInfo)) return Set.of(typeInfo.fullyQualifiedName);
        return Set.of();
    }

    @Override
    public <T> T extract(String fieldName, T defaultValue) {
        for (Expression expression : expressions) {
            if (expression instanceof MemberValuePair mvp) {
                if (mvp.name().equals(fieldName)) {
                    return (T) returnValueOfAnnotationExpression(fieldName, mvp.value());
                }
            } else if ("value".equals(fieldName)) {
                return (T) returnValueOfAnnotationExpression(fieldName, expression);
            }
        }
        return defaultValue;
    }

    private Object returnValueOfAnnotationExpression(String fieldName, Expression expression) {
        // important: the constant expression situation is the most common case
        // there'll be trouble with other situations when the typeInfo has not been inspected yet,
        // as we don't have an inspection provider here
        if (expression instanceof ConstantExpression<?> ce) {
            return ce.getValue();
        }

        // it is always possible that the return type is an array, but only one value is present...
        ParameterizedType returnType = returnType(fieldName);
        if (expression instanceof ArrayInitializer arrayInitializer) {
            Object[] array = createArray(returnType, arrayInitializer.multiExpression.expressions().length);
            int i = 0;
            for (Expression element : arrayInitializer.multiExpression.expressions()) {
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

    private ParameterizedType returnType(String fieldName) {
        return typeInfo.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(fieldName))
                .findFirst()
                .map(MethodInfo::returnType).orElseThrow();
    }

    private static Object returnValueOfNonArrayExpression(ParameterizedType returnType, Expression expression) {

        // normal "constant" or 123
        if (expression instanceof ConstantExpression<?> ce) {
            return ce.getValue();
        }

        // direct reference with import static
        if (expression instanceof VariableExpression ve && ve.variable() instanceof FieldReference fieldReference) {
            return enumInstance(returnType, fieldReference.fieldInfo.owner, fieldReference.fieldInfo.name);
        }

        // Type.CONSTANT
        if (expression instanceof FieldAccess fieldAccess) {
            if (fieldAccess.expression() instanceof TypeExpression typeExpression) {
                return enumInstance(returnType, typeExpression.parameterizedType.typeInfo, fieldAccess.variable().simpleName());
            } else throw new UnsupportedOperationException("? did not expect " + fieldAccess.expression().getClass());
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
    public AnnotationExpression copyWith(Primitives primitives, String parameter, String value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new StringConstant(primitives, value));
        return new AnnotationExpressionImpl(typeInfo, List.of(memberValuePair));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(typeInfo, true);
    }

    @Override
    public AnnotationParameters e2ImmuAnnotationParameters() {
        if (!typeInfo.fullyQualifiedName.startsWith(ORG_E_2_IMMU_ANNOTATION)) return null;
        boolean absent = extract("absent", false);
        boolean contract = extract("contract", false);
        return new AnnotationParameters(absent, contract);
    }

}
