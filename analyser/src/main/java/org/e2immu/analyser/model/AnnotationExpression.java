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

import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.*;

/*
 Eventually E2Immutable (TypeInfo is definitely @E1Immutable, FirstThen is eventually @E1Immutable)
 */
@E2Immutable(after = "expressions")
public class AnnotationExpression {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationExpression.class);

    public final TypeInfo typeInfo;
    public final FirstThen<com.github.javaparser.ast.expr.Expression, List<Expression>> expressions;

    public static AnnotationExpression fromAnalyserExpressions(TypeInfo annotationType,
                                                               List<Expression> expressions) {
        FirstThen<com.github.javaparser.ast.expr.Expression, List<Expression>> firstThen = new FirstThen<>(FieldInspection.EMPTY);
        firstThen.set(ImmutableList.copyOf(expressions));
        return new AnnotationExpression(annotationType, firstThen);
    }

    public static AnnotationExpression fromJavaParserExpression(TypeInfo annotation,
                                                                com.github.javaparser.ast.expr.Expression expression) {
        return new AnnotationExpression(annotation, new FirstThen<>(expression));
    }

    private AnnotationExpression(TypeInfo annotation, FirstThen<com.github.javaparser.ast.expr.Expression, List<Expression>> expressions) {
        Objects.requireNonNull(annotation);
        Objects.requireNonNull(expressions);

        this.typeInfo = annotation;
        this.expressions = expressions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnnotationExpression that = (AnnotationExpression) o;
        return typeInfo.equals(that.typeInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeInfo);
    }

    public static AnnotationExpression from(@NotModified AnnotationExpr ae, ExpressionContext expressionContext) {
        NamedType namedType = expressionContext.typeContext.get(ae.getNameAsString(), true);
        if (!(namedType instanceof TypeInfo)) {
            throw new UnsupportedOperationException("??");
        }
        TypeInfo ti = (TypeInfo) namedType;
        return AnnotationExpression.fromJavaParserExpression(ti, ae);
    }

    public void resolve(ExpressionContext expressionContext) {
        if (expressions.isSet()) throw new UnsupportedOperationException();
        com.github.javaparser.ast.expr.Expression ae = expressions.getFirst();
        List<Expression> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : ((NormalAnnotationExpr) ae).getPairs()) {
                Expression value = expressionContext.parseExpression(mvp.getValue());
                analyserExpressions.add(new org.e2immu.analyser.model.expression.MemberValuePair(mvp.getName().asString(), value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr) {
            Expression value = expressionContext.parseExpression(ae.asSingleMemberAnnotationExpr().getMemberValue());
            analyserExpressions = List.of(new MemberValuePair("value", value));
        } else analyserExpressions = List.of();
        expressions.set(analyserExpressions);
    }

    public String stream() {
        StringBuilder sb = new StringBuilder("@" + typeInfo.simpleName);
        if (expressions.isSet() && !expressions.get().isEmpty()) {
            sb.append("(");
            boolean first = true;
            for (Expression expression : expressions.get()) {
                if (first) first = false;
                else sb.append(", ");
                if (expression instanceof Constant) {
                    sb.append(expression.expressionString(0));
                } else if (expression instanceof MemberValuePair) {
                    MemberValuePair memberValuePair = (MemberValuePair) expression;
                    if (!memberValuePair.name.equals("value")) {
                        sb.append(memberValuePair.name);
                        sb.append("=");
                    }
                    sb.append(memberValuePair.value.expressionString(0));
                }
            }
            sb.append(")");
        }
        return sb.toString();
    }

    public Set<String> imports() {
        if (!typeInfo.isJavaLang()) return Set.of(typeInfo.fullyQualifiedName);
        return Set.of();
    }

    public <T> T extract(String fieldName, T defaultValue) {
        if (!expressions.isSet()) throw new UnsupportedOperationException("??");
        if (expressions.get().isEmpty()) return defaultValue;
        for (Expression expression : expressions.get()) {
            if (typeInfo.typeInspection.isSetPotentiallyRun()) {
                ParameterizedType returnType = typeInfo.typeInspection.getPotentiallyRun().methodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM)
                        .filter(m -> m.name.equals(fieldName))
                        .findFirst()
                        .map(MethodInfo::returnType).orElseThrow();
                if (expression instanceof MemberValuePair) {
                    MemberValuePair mvp = (MemberValuePair) expression;
                    if (mvp.name.equals(fieldName)) {
                        return (T) returnValueOfAnnotationExpression(returnType, mvp.value);
                    }
                } else if ("value".equals(fieldName)) {
                    return (T) returnValueOfAnnotationExpression(returnType, expression);
                }
            } else {
                LOGGER.warn("Type has not been inspected yet: " + typeInfo.fullyQualifiedName);
            }
        }
        return defaultValue;
    }

    private static Object returnValueOfAnnotationExpression(ParameterizedType returnType, Expression expression) {
        // it is always possible that the return type is an array, but only one value is present...

        if (expression instanceof ArrayInitializer) {
            ArrayInitializer arrayInitializer = (ArrayInitializer) expression;
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

        // VERIFY_ABSENT -> direct reference with import static AnnotationType.VERIFY_ABSENT
        if (expression instanceof VariableExpression && ((VariableExpression) expression).variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) (((VariableExpression) expression).variable)).fieldInfo;
            return enumInstance(returnType, fieldInfo.owner, fieldInfo.name);
        }

        // AnnotationType.VERIFY_ABSENT
        if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            if (fieldAccess.expression instanceof TypeExpression) {
                TypeExpression typeExpression = (TypeExpression) fieldAccess.expression;
                return enumInstance(returnType, typeExpression.parameterizedType.typeInfo, fieldAccess.variable.name());
            } else throw new UnsupportedOperationException("? did not expect " + fieldAccess.expression.getClass());
        }
        if (expression instanceof UnaryOperator) {
            UnaryOperator unaryOperator = ((UnaryOperator) expression);
            if (unaryOperator.operator == Primitives.PRIMITIVES.unaryMinusOperatorInt && unaryOperator.expression instanceof IntConstant) {
                IntConstant intConstant = (IntConstant) unaryOperator.expression;
                return -intConstant.getValue();
            }
        }
        throw new UnsupportedOperationException("Not implemented: " + expression.getClass());
    }

    private static Object enumInstance(ParameterizedType type, TypeInfo observedType, String name) {
        if (type.typeInfo.typeInspection.getPotentiallyRun().typeNature != TypeNature.ENUM) {
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

    public boolean isVerifyAbsent() {
        AnnotationType annotationType = extract("type", null);
        return annotationType == AnnotationType.VERIFY_ABSENT;
    }

    public boolean test() {
        return extract("test", false);
    }

    public AnnotationExpression copyWith(String parameter, int value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new IntConstant(value));
        return AnnotationExpression.fromAnalyserExpressions(typeInfo, List.of(memberValuePair));
    }

    public AnnotationExpression copyWith(String parameter, boolean value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new BooleanConstant(value));
        return AnnotationExpression.fromAnalyserExpressions(typeInfo, List.of(memberValuePair));
    }

    public AnnotationExpression copyWith(String parameter, String value) {
        MemberValuePair memberValuePair = new MemberValuePair(parameter, new StringConstant(value));
        return AnnotationExpression.fromAnalyserExpressions(typeInfo, List.of(memberValuePair));
    }

    public Set<TypeInfo> typesReferenced() {
        return Set.of(typeInfo);
    }

    @Container(builds = AnnotationExpression.class)
    public static class AnnotationExpressionBuilder {
        private final TypeInfo typeInfo;
        private final List<Expression> expressions = new ArrayList<>();

        public AnnotationExpressionBuilder(TypeInfo typeInfo) {
            this.typeInfo = typeInfo;
        }

        @Fluent
        @Modified
        public AnnotationExpressionBuilder addExpression(Expression expression) {
            expressions.add(expression);
            return this;
        }

        @NotModified
        public AnnotationExpression build() {
            return AnnotationExpression.fromAnalyserExpressions(typeInfo, ImmutableList.copyOf(expressions));
        }
    }

}
