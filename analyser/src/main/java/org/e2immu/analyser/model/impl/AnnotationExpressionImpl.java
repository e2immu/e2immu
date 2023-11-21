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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.util.GenerateAnnotationsImmutableAndContainer;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.PackedInt;
import org.e2immu.analyser.util.PackedIntMap;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.rare.Finalizer;
import org.e2immu.annotation.rare.IgnoreModifications;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.E2ImmuAnnotationExpressions.*;

public class AnnotationExpressionImpl extends BaseExpression implements AnnotationExpression {

    private final TypeInfo typeInfo;
    private final List<MemberValuePair> expressions;
    private final ParameterizedType parameterizedType;

    public AnnotationExpressionImpl(TypeInfo typeInfo,
                                    @ImmutableContainer
                                    List<MemberValuePair> expressions) {
        this(Identifier.CONSTANT, typeInfo, expressions);
    }

    public AnnotationExpressionImpl(Identifier identifier,
                                    TypeInfo typeInfo,
                                    @ImmutableContainer
                                    List<MemberValuePair> expressions) {
        super(identifier, expressions.stream().mapToInt(Expression::getComplexity).sum());
        this.typeInfo = Objects.requireNonNull(typeInfo);
        this.expressions = Objects.requireNonNull(expressions);
        this.parameterizedType = new ParameterizedType(typeInfo, List.of());
    }

    public static final String ORG_E_2_IMMU_ANNOTATION = "org.e2immu.annotation";

    public static AnnotationExpression from(Primitives primitives, TypeInfo typeInfo, Map<String, Object> map) {
        Stream<MemberValuePair> stream;
        if (map == GenerateAnnotationsImmutableAndContainer.NO_PARAMS) {
            stream = Stream.of();
        } else {
            stream = map.entrySet().stream().map(o -> new MemberValuePair(o.getKey(), create(primitives, o.getValue())));
        }
        return new AnnotationExpressionImpl(typeInfo, stream.toList());
    }

    private static Expression create(Primitives primitives, Object object) {
        if (object instanceof String string) return new StringConstant(primitives, Identifier.CONSTANT, string);
        if (object instanceof Boolean bool) return new BooleanConstant(primitives, Identifier.CONSTANT, bool);
        if (object instanceof Integer integer) return new IntConstant(primitives, Identifier.CONSTANT, integer);
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeInfo typeInfo() {
        return typeInfo;
    }

    @Override
    public List<MemberValuePair> expressions() {
        return expressions;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ANNOTATION_EXPRESSION;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        if (v instanceof AnnotationExpression ae) {
            int c = typeInfo.compareTo(ae.typeInfo());
            if (c != 0) return c;
            int pos = 0;
            for (Expression e : expressions) {
                if (pos >= ae.expressions().size()) return -1;
                int d = e.compareTo(ae.expressions().get(pos));
                if (d != 0) return d;
                pos++;
            }
            if (ae.expressions().size() > expressions.size()) return 1;
            return 0;
        } else throw new ExpressionComparator.InternalError();
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
                    .add(expressions.stream()
                            .map(expression -> expression.output(qualification))
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
            int length = arrayInitializer.multiExpression.expressions().length;
            if (returnType.typeInfo != null && "int".equals(returnType.typeInfo.fullyQualifiedName)) {
                int[] array = new int[length];
                int i = 0;
                for (Expression element : arrayInitializer.multiExpression.expressions()) {
                    array[i++] = (int) returnValueOfNonArrayExpression(arrayInitializer.returnType(), element);
                }
                return array;
            }
            Object[] array = createArray(returnType, length);
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
                return enumField(returnType, fieldReference.fieldInfo().owner, fieldReference.fieldInfo().name);
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
        Expression initialiser = fieldReference.fieldInfo().fieldInspection.get().getFieldInitialiser().initialiser();
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
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            for (Expression e : expressions) {
                e.visit(predicate);
            }
        }
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parameterizedType.typesReferenced(true),
                expressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return PackedIntMap.of(
                parameterizedType.typesReferenced2(weight),
                expressions.stream().flatMap(e -> e.typesReferenced2(weight).stream())
                        .collect(PackedIntMap.collector()));
    }

    private static final Set<String> FQN_ALWAYS_CONTRACT = Set.of(Finalizer.class.getCanonicalName(),
            IgnoreModifications.class.getCanonicalName());

    @Override
    public AnnotationParameters e2ImmuAnnotationParameters() {
        if (!typeInfo.fullyQualifiedName.startsWith(ORG_E_2_IMMU_ANNOTATION)) return null;
        boolean absent = extract(ABSENT, false);
        boolean contract = extract(CONTRACT, false) || FQN_ALWAYS_CONTRACT.contains(typeInfo.fullyQualifiedName);
        boolean implied = extract(IMPLIED, false);
        return new AnnotationParameters(absent, contract, implied);
    }

}
