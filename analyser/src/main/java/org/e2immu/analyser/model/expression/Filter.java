/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * All filtering operations on values have been moved to this class; they were littering the Value hierarchy,
 * making it all pretty complex to understand.
 */
public class Filter {

    /**
     * The result of filtering -> some clauses have been picked up, and put in <code>accepted</code>.
     * The remaining clauses are stored in <code>rest</code>.
     *
     * @param <X> depends on the filter method. Sometimes we filter on variables, sometimes on values.
     */
    public record FilterResult<X>(Map<X, Expression> accepted, Expression rest) {
    }


    @FunctionalInterface
    public interface FilterMethod<X> {
        FilterResult<X> apply(Expression value);
    }

    public enum FilterMode {
        ALL,
        ACCEPT, // normal state of the variable AFTER the escape; independent = AND; does not recurse into OrValues
        REJECT, // condition for escaping; independent = OR; does not recurse into AndValues
    }

    public static <X> FilterResult<X> filter(EvaluationContext evaluationContext, Expression value, FilterMode filterMode, FilterMethod<X> filterMethod) {
        FilterResult<X> result = internalFilter(evaluationContext, value, filterMode, List.of(filterMethod));
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    public static <X> FilterResult<X> filter(EvaluationContext evaluationContext, Expression value, FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        FilterResult<X> result = internalFilter(evaluationContext, value, filterMode, filterMethods);
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    private static <X> FilterResult<X> internalFilter(EvaluationContext evaluationContext, Expression value, FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        AtomicReference<FilterResult<X>> filterResult = new AtomicReference<>();
        value.visit(v -> {
            if (v != EmptyExpression.EMPTY_EXPRESSION) {
                if (v instanceof NegatedExpression negatedValue) {
                    FilterResult<X> resultOfNegated = internalFilter(evaluationContext, negatedValue.expression, filterMode, filterMethods);
                    if (resultOfNegated != null) {
                        FilterResult<X> negatedResult = new FilterResult<>(resultOfNegated.accepted.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        e -> NegatedExpression.negate(evaluationContext, e.getValue()), (v1, v2) -> v1)),
                                NegatedExpression.negate(evaluationContext, resultOfNegated.rest));
                        filterResult.set(negatedResult);
                    }
                } else if (v instanceof AndExpression andValue) {
                    if (filterMode == FilterMode.ACCEPT || filterMode == FilterMode.ALL) {
                        filterResult.set(processAndOr(evaluationContext, andValue.expressions(), filterMode, filterMethods));
                    }
                } else if (v instanceof OrExpression orValue) {
                    if (filterMode == FilterMode.REJECT || filterMode == FilterMode.ALL) {
                        filterResult.set(processAndOr(evaluationContext, orValue.expressions(), filterMode, filterMethods));
                    }
                } else {
                    for (FilterMethod<X> filterMethod : filterMethods) {
                        FilterResult<X> res = filterMethod.apply(v);
                        if (res != null) {
                            // we have a hit
                            filterResult.set(res);
                            break;
                        }
                    }
                }
            }
            return false; // do not go deeper
        });
        return filterResult.get();
    }

    private static <X> FilterResult<X> processAndOr(EvaluationContext evaluationContext, List<Expression> values,
                                                    FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        List<FilterResult<X>> results = values.stream().map(v -> {
            FilterResult<X> sub = Filter.internalFilter(evaluationContext, v, filterMode, filterMethods);
            return sub == null ? new FilterResult<X>(Map.of(), v) : sub;
        }).collect(Collectors.toList());

        List<Expression> restList = results.stream().map(r -> r.rest).filter(r -> r != EmptyExpression.EMPTY_EXPRESSION).collect(Collectors.toList());
        Map<X, Expression> acceptedCombined = results.stream().flatMap(r -> r.accepted.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));

        Expression rest;
        if (restList.isEmpty()) rest = EmptyExpression.EMPTY_EXPRESSION;
        else if (restList.size() == 1) rest = restList.get(0);
        else {
            rest = new AndExpression(evaluationContext.getPrimitives()).append(evaluationContext, restList.toArray(Expression[]::new));
        }
        return new FilterResult<>(acceptedCombined, rest);
    }

    // some filter methods


    // EXAMPLE: field == null, field == constant, ...

    public static final FilterMethod<FieldReference> INDIVIDUAL_FIELD_CLAUSE = value -> {
        if (value instanceof EqualsExpression equalsValue) {
            FieldReference l = extractFieldReference(equalsValue.lhs);
            FieldReference r = extractFieldReference(equalsValue.rhs);
            if (l != null && r == null)
                return new FilterResult<FieldReference>(Map.of(l, value), EmptyExpression.EMPTY_EXPRESSION);
            if (r != null && l == null)
                return new FilterResult<FieldReference>(Map.of(r, value), EmptyExpression.EMPTY_EXPRESSION);
        }
        return null;
    };

    private static FieldReference extractFieldReference(Expression value) {
        return value instanceof VariableExpression variableValue &&
                variableValue.variable() instanceof FieldReference fieldReference ? fieldReference : null;
    }

    // EXAMPLE: p == null, field != null


    public static final FilterMethod<Variable> INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE = value -> {
        if (value instanceof EqualsExpression equalsValue) {
            boolean lhsIsNull = equalsValue.lhs.isNull();
            boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
            if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof VariableExpression v) {
                return new FilterResult<Variable>(Map.of(v.variable(), value), EmptyExpression.EMPTY_EXPRESSION);
            }
        }
        return null;
    };


    // EXAMPLE: p == null, p != null

    public static final FilterMethod<ParameterInfo> INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER = value -> {
        if (value instanceof EqualsExpression equalsValue) {
            boolean lhsIsNull = equalsValue.lhs.isNull();
            boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
            if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof VariableExpression v && v.variable() instanceof ParameterInfo p) {
                return new FilterResult<ParameterInfo>(Map.of(p, value), EmptyExpression.EMPTY_EXPRESSION);
            }
        }
        return null;
    };

    // EXAMPLE: 0 == java.util.Collection.this.size()  --> map java.util.Collection.this.size() onto 0
    // EXAMPLE: java.util.Collection.this.size() == o.e.a.t.BasicCompanionMethods_6.test(Set<java.lang.String>):0:strings.size() --> copy lhs onto rhs
    public static record ValueEqualsMethodCallNoParameters(MethodInfo methodInfo) implements FilterMethod<MethodCall> {

        @Override
        public FilterResult<MethodCall> apply(Expression value) {
            if (value instanceof EqualsExpression equalsValue) {
                MethodCall r = compatibleMethodValue(equalsValue.rhs);
                if (r != null) {
                    return new FilterResult<>(Map.of(r, equalsValue.lhs), EmptyExpression.EMPTY_EXPRESSION);
                }
                MethodCall l = compatibleMethodValue(equalsValue.lhs);
                if (l != null) {
                    return new FilterResult<>(Map.of(l, equalsValue.rhs), EmptyExpression.EMPTY_EXPRESSION);
                }
            }
            return null;
        }

        private MethodCall compatibleMethodValue(Expression value) {
            if (value instanceof MethodCall methodValue &&
                    methodValue.object instanceof VariableExpression vv &&
                    vv.variable() instanceof This &&
                    compatibleMethod(methodInfo, methodValue.methodInfo)) {
                return methodValue;
            }
            return null;
        }
    }

    private static boolean compatibleParameters(List<Expression> parameters, List<Expression> parametersInClause) {
        return ListUtil.joinLists(parameters, parametersInClause).allMatch(pair -> pair.k.equals(pair.v));
    }

    private static boolean compatibleMethod(MethodInfo methodInfo, MethodInfo methodInClause) {
        if (methodInClause == methodInfo) return true;
        return methodInfo.methodResolution.get().overrides().contains(methodInClause);
    }

    // EXAMPLE: java.util.List.contains("a")

    public static record MethodCallBooleanResult(MethodInfo methodInfo, List<Expression> parameterValues,
                                                 Expression boolValueTrue) implements FilterMethod<MethodCall> {

        @Override
        public FilterResult<MethodCall> apply(Expression value) {
            if (value instanceof MethodCall methodValue && compatible(methodValue)) {
                return new FilterResult<>(Map.of(methodValue, boolValueTrue), EmptyExpression.EMPTY_EXPRESSION);
            }
            return null;
        }

        private boolean compatible(MethodCall methodValue) {
            return compatibleMethod(methodInfo, methodValue.methodInfo) &&
                    compatibleParameters(parameterValues, methodValue.getParameterExpressions());
        }
    }

    public static record ExactValue(Expression value) implements FilterMethod<Expression> {

        @Override
        public FilterResult<Expression> apply(Expression value) {
            if (this.value.equals(value))
                return new FilterResult<>(Map.of(value, value), EmptyExpression.EMPTY_EXPRESSION);
            return null;
        }
    }
}
