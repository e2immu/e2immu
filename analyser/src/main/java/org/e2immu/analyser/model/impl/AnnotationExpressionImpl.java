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

package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.Finalizer;
import org.e2immu.annotation.IgnoreModifications;

import java.lang.reflect.Array;
import java.util.*;


public record AnnotationExpressionImpl(TypeInfo typeInfo,
                                       List<MemberValuePair> expressions) implements AnnotationExpression {

    public static final String ORG_E_2_IMMU_ANNOTATION = "org.e2immu.annotation";

    public AnnotationExpressionImpl {
        Objects.requireNonNull(typeInfo);
        Objects.requireNonNull(expressions);
    }

    // used by the byte code inspector, MyAnnotationVisitor
    public static class Builder {
        private TypeInfo typeInfo;
        private final List<MemberValuePair> expressions = new ArrayList<>();

        public Builder setTypeInfo(TypeInfo typeInfo) {
            this.typeInfo = typeInfo;
            return this;
        }

        public Builder addExpression(MemberValuePair expression) {
            this.expressions.add(expression);
            return this;
        }

        public AnnotationExpression build() {
            return new AnnotationExpressionImpl(typeInfo, List.copyOf(expressions));
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
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder().add(Symbol.AT)
                .add(typeInfo.typeName(qualification.qualifierRequired(typeInfo)));
        if (!expressions.isEmpty()) {
            outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                    .add(expressions.stream().map(expression -> expression.output(qualification))
                            .collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.RIGHT_PARENTHESIS);
        }
        return outputBuilder;
    }

    @Override
    public String toString() {
        return output(Qualification.EMPTY).toString();
    }

    @Override
    public Set<String> imports() {
        if (typeInfo.isNotJavaLang()) return Set.of(typeInfo.fullyQualifiedName);
        return Set.of();
    }

    @Override
    public int[] extractIntArray(String fieldName) {
        for (Expression expression : expressions) {
            if (expression instanceof MemberValuePair mvp) {
                if (mvp.name().equals(fieldName)) {
                    if (mvp.value().get() instanceof ArrayInitializer ai) {
                        return Arrays.stream(ai.multiExpression.expressions())
                                .mapToInt(e -> ((IntConstant) e).constant()).toArray();
                    } else throw new UnsupportedOperationException();
                }
            }
        }
        return new int[0];
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T extract(String fieldName, T defaultValue) {
        for (Expression expression : expressions) {
            if (expression instanceof MemberValuePair mvp) {
                if (mvp.name().equals(fieldName)) {
                    return (T) returnValueOfAnnotationExpression(fieldName, mvp.value().get());
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
        ConstantExpression<?> ce;
        if ((ce = expression.asInstanceOf(ConstantExpression.class)) != null) {
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
        ConstantExpression<?> ce;
        if ((ce = expression.asInstanceOf(ConstantExpression.class)) != null) {
            return ce.getValue();
        }

        // direct reference with import static, or local Enum or constant
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null
                && ve.variable() instanceof FieldReference fieldReference) {
            if (returnType.typeInfo.typeInspection.get().typeNature() == TypeNature.ENUM) {
                return enumField(returnType, fieldReference.fieldInfo.owner, fieldReference.fieldInfo.name);
            }
            return inspectFieldValue(returnType, fieldReference);
        }

        // -123
        if (expression instanceof UnaryOperator unaryOperator) {
            if (unaryOperator.operator.isUnaryMinusOperatorInt() &&
                    unaryOperator.expression instanceof IntConstant intConstant) {
                return -intConstant.getValue();
            }
        }
        throw new UnsupportedOperationException("Not implemented: " + expression.getClass());
    }

    private static Object inspectFieldValue(ParameterizedType parameterizedType, FieldReference fieldReference) {
        Expression initialiser = fieldReference.fieldInfo.fieldInspection.get().getFieldInitialiser().initialiser();
        return returnValueOfNonArrayExpression(parameterizedType, initialiser);
    }

    private static Object enumField(ParameterizedType type, TypeInfo observedType, String name) {
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

    private static final Set<String> FQN_ALWAYS_CONTRACT = Set.of(Finalizer.class.getCanonicalName(),
            IgnoreModifications.class.getCanonicalName());

    @Override
    public AnnotationParameters e2ImmuAnnotationParameters() {
        if (!typeInfo.fullyQualifiedName.startsWith(ORG_E_2_IMMU_ANNOTATION)) return null;
        boolean absent = extract("absent", false);
        boolean contract = extract("contract", false) || FQN_ALWAYS_CONTRACT.contains(typeInfo.fullyQualifiedName);
        return new AnnotationParameters(absent, contract);
    }

}
