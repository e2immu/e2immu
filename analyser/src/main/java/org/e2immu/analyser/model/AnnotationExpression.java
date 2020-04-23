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
import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.expression.FieldAccess;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@E2Immutable
@NotNull
public class AnnotationExpression {

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
        if (expressions.get().isEmpty()) return defaultValue;
        for (Expression expression : expressions.get()) {
            if (expression instanceof MemberValuePair) {
                MemberValuePair mvp = (MemberValuePair) expression;
                if (mvp.name.equals(fieldName)) {
                    return (T) returnValueOfAnnotationExpression(mvp.value);
                }
            } else if ("value".equals(fieldName)) {
                return (T) returnValueOfAnnotationExpression(expression);
            }
        }
        return defaultValue;
    }

    private static Object returnValueOfAnnotationExpression(Expression expression) {
        // normal "constant" or 123
        if (expression instanceof Constant) return ((Constant) expression).getValue();

        // VERIFY_ABSENT -> direct reference with import static AnnotationType.VERIFY_ABSENT
        if (expression instanceof VariableExpression && ((VariableExpression) expression).variable instanceof FieldReference) {
            FieldInfo fieldInfo = ((FieldReference) (((VariableExpression) expression).variable)).fieldInfo;
            if (AnnotationType.class.getCanonicalName().equals(fieldInfo.owner.fullyQualifiedName)) {
                return AnnotationType.valueOf(fieldInfo.name);
            }
        }
        // AnnotationType.VERIFY_ABSENT
        if (expression instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) expression;
            if (fieldAccess.expression instanceof TypeExpression) {
                TypeExpression typeExpression = (TypeExpression) fieldAccess.expression;
                if (AnnotationType.class.getCanonicalName().equals(typeExpression.parameterizedType.typeInfo.fullyQualifiedName)) {
                    return AnnotationType.valueOf(fieldAccess.variable.name());
                }
            }
        }
        // or...?
        throw new UnsupportedOperationException("Not implemented: " + expression.getClass());
    }

    public boolean isVerifyAbsent() {
        AnnotationType annotationType = extract("type", null);
        return annotationType == AnnotationType.VERIFY_ABSENT;
    }

    public boolean test() {
        return extract("test", false);
    }

    public static class AnnotationExpressionBuilder {
        private final TypeInfo typeInfo;
        private final List<Expression> expressions = new ArrayList<>();

        public AnnotationExpressionBuilder(TypeInfo typeInfo) {
            this.typeInfo = typeInfo;
        }

        public AnnotationExpressionBuilder addExpression(Expression expression) {
            expressions.add(expression);
            return this;
        }

        public AnnotationExpression build() {
            return AnnotationExpression.fromAnalyserExpressions(typeInfo, ImmutableList.copyOf(expressions));
        }
    }
}
