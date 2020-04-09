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
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.value.UnknownValue;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// we cannot set @E2Immutable here... as 2 implementations are @Container s
// we will guarantee though that no expression changes its parameters
@Container // implying @NotModified on parameters
@NotNull
@NullNotAllowed
public interface Expression {

    @NotModified
    ParameterizedType returnType();

    @NotModified
    String expressionString(int indent);

    @NotModified
    int precedence();

    @NotModified
    default Value evaluate(EvaluationContext evaluationContext) {
        return UnknownValue.UNKNOWN_VALUE;
    }

    @NotNull
    @NotModified
    // TODO immutable
    default Set<String> imports() {
        return Set.of();
    }

    @NotModified
    // TODO immutable
    default List<Expression> subExpressions() {
        return List.of();
    }

    @NotModified
    default String bracketedExpressionString(int indent, Expression expression) {
        if (expression.precedence() < precedence()) {
            return "(" + expression.expressionString(indent) + ")";
        }
        return expression.expressionString(indent);
    }

    @NotModified
    // TODO immutable
    default List<Variable> variablesUsed() {
        return List.of();
    }

    class InScopeSide {
        public final Expression expression;
        public final boolean inScopeSide;

        public InScopeSide(Expression expression, boolean inScopeSide) {
            this.inScopeSide = inScopeSide;
            this.expression = Objects.requireNonNull(expression);
        }
    }

    // a.b() or a.b -> a is on the scope side
    // the default is to check your sub-expressions
    @NotModified
    default List<InScopeSide> expressionsInScopeSide() {
        return subExpressions().stream().map(expression -> new InScopeSide(expression, true)).collect(Collectors.toList());
    }

    // TODO @Immutable
    @NotModified
    default List<LocalVariableReference> newLocalVariables() {
        return List.of();
    }

    @NotModified
    default Optional<Variable> assignmentTarget() {
        throw new UnsupportedOperationException("Class is " + getClass());
    }

    @NotModified
    default Variable variableFromExpression() {
        throw new UnsupportedOperationException("Expecting a variable from expression " + getClass());
    }

    @NotModified
    default SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return subExpressions().stream()
                .map(e -> e.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
    }

    // *************************** methods that are not meant to be overridden *****************

    @NotModified
    default List<Variable> variablesInScopeSide(boolean inScopeSide) {
        List<Variable> result = new ArrayList<>();
        if (inScopeSide) {
            if (this instanceof VariableExpression) {
                VariableExpression variableExpression = (VariableExpression) this;
                result.add(variableExpression.variable);
            } else if (this instanceof FieldReference) {
                FieldReference fieldReference = (FieldReference) this;
                result.add(fieldReference);
            }
        }
        for (InScopeSide i : expressionsInScopeSide()) {
            result.addAll(i.expression.variablesInScopeSide(i.inScopeSide));
        }
        return result;
    }


    @NotModified
    // @Immutable
    default List<LocalVariableReference> allNewLocalVariables() {
        List<LocalVariableReference> result = new ArrayList<>();
        collect(Expression::newLocalVariables, result::addAll);
        return ImmutableList.copyOf(result);
    }

    @NotModified
    default <T> void collect(Function<Expression, List<T>> collector, Consumer<List<T>> resultHandler) {
        resultHandler.accept(collector.apply(this));
        for (Expression sub : subExpressions()) {
            sub.collect(collector, resultHandler);
        }
    }

    @NotModified
    default <T> void collectStream(Function<Expression, Stream<T>> collector, Consumer<Stream<T>> handler) {
        handler.accept(collector.apply(this));
        for (Expression sub : subExpressions()) {
            sub.collectStream(collector, handler);
        }
    }

    @NotModified
    // @Immutable
    default List<Variable> nonStaticVariablesUsed() {
        List<Variable> result = new ArrayList<>();
        collectStream(e -> e.variablesUsed().stream().filter(v -> !v.isStatic()), str -> str.forEach(result::add));
        return ImmutableList.copyOf(result);
    }

    // we want the variables on the left hand side of an assignment, but this could be complex, as in
    // object.field.field2[i++] --> field2
    @NotModified
    //@Immutable // unModifiableList
    default List<Variable> assignmentTargets() {
        List<Assignment> assignments = find(Assignment.class);
        return assignments.stream()
                .map(a -> a.target.assignmentTarget().orElseThrow())
                .collect(Collectors.toUnmodifiableList());
    }

    @NotModified
    default <E extends Expression> List<E> find(Class<E> clazz) {
        List<E> result = new ArrayList<>();
        collect(e -> {
            if (clazz.isAssignableFrom(e.getClass())) return List.of((E) e);
            return List.of();
        }, result::addAll);
        return ImmutableList.copyOf(result);
    }
}
