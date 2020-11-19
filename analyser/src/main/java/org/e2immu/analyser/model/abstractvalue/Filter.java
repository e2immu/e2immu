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

package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public record FilterResult<X>(Map<X, Value> accepted, Value rest) {
    }


    @FunctionalInterface
    public interface FilterMethod<X> {
        FilterResult<X> apply(Value value);
    }

    public enum FilterMode {
        ALL,
        ACCEPT, // normal state of the variable AFTER the escape; independent = AND; does not recurse into OrValues
        REJECT, // condition for escaping; independent = OR; does not recurse into AndValues
    }

    public static <X> FilterResult<X> filter(EvaluationContext evaluationContext, Value value, FilterMode filterMode, FilterMethod<X> filterMethod) {
        FilterResult<X> result = internalFilter(evaluationContext, value, filterMode, List.of(filterMethod));
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    public static <X> FilterResult<X> filter(EvaluationContext evaluationContext, Value value, FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        FilterResult<X> result = internalFilter(evaluationContext, value, filterMode, filterMethods);
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    private static <X> FilterResult<X> internalFilter(EvaluationContext evaluationContext, Value value, FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        AtomicReference<FilterResult<X>> filterResult = new AtomicReference<>();
        value.visit(v -> {
            if (v instanceof NegatedValue negatedValue) {
                FilterResult<X> resultOfNegated = internalFilter(evaluationContext, negatedValue.value, filterMode, filterMethods);
                if (resultOfNegated != null) {
                    FilterResult<X> negatedResult = new FilterResult<X>(resultOfNegated.accepted.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    e -> NegatedValue.negate(evaluationContext, e.getValue()), (v1, v2) -> v1)),
                            NegatedValue.negate(evaluationContext, resultOfNegated.rest));
                    filterResult.set(negatedResult);
                }
                return false;
            }
            if (v instanceof AndValue andValue) {
                if (filterMode == FilterMode.ACCEPT || filterMode == FilterMode.ALL) {
                    filterResult.set(processAndOr(evaluationContext, andValue.values, filterMode, filterMethods));
                }
                return false; // do not go deeper
            }
            if (v instanceof OrValue orValue) {
                if (filterMode == FilterMode.REJECT || filterMode == FilterMode.ALL) {
                    filterResult.set(processAndOr(evaluationContext, orValue.values, filterMode, filterMethods));
                }
                return false;
            }
            for (FilterMethod<X> filterMethod : filterMethods) {
                FilterResult<X> res = filterMethod.apply(v);
                if (res != null) {
                    // we have a hit
                    filterResult.set(res);
                    return false; // no need to go deeper
                }
            }
            return true; // go deeper
        });
        return filterResult.get();
    }

    private static <X> FilterResult<X> processAndOr(EvaluationContext evaluationContext, List<Value> values,
                                                    FilterMode filterMode, List<FilterMethod<X>> filterMethods) {
        List<FilterResult<X>> results = values.stream().map(v -> {
            FilterResult<X> sub = Filter.internalFilter(evaluationContext, v, filterMode, filterMethods);
            return sub == null ? new FilterResult<X>(Map.of(), v) : sub;
        }).collect(Collectors.toList());

        List<Value> restList = results.stream().map(r -> r.rest).filter(r -> r != UnknownValue.EMPTY).collect(Collectors.toList());
        Map<X, Value> acceptedCombined = results.stream().flatMap(r -> r.accepted.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));

        Value rest;
        if (restList.isEmpty()) rest = UnknownValue.EMPTY;
        else if (restList.size() == 1) rest = restList.get(0);
        else {
            rest = new AndValue(evaluationContext.getPrimitives()).append(evaluationContext, restList.toArray(Value[]::new));
        }
        return new FilterResult<X>(acceptedCombined, rest);
    }

    // some filter methods


    // EXAMPLE: field == null, field == constant, ...

    public static final FilterMethod<FieldReference> INDIVIDUAL_FIELD_CLAUSE = value -> {
        if (value instanceof EqualsValue equalsValue) {
            FieldReference l = extractFieldReference(equalsValue.lhs);
            FieldReference r = extractFieldReference(equalsValue.rhs);
            if (l != null && r == null)
                return new FilterResult<FieldReference>(Map.of(l, value), UnknownValue.EMPTY);
            if (r != null && l == null)
                return new FilterResult<FieldReference>(Map.of(r, value), UnknownValue.EMPTY);
        }
        return null;
    };

    private static FieldReference extractFieldReference(Value value) {
        return value instanceof VariableValue variableValue && variableValue.variable instanceof FieldReference fieldReference ? fieldReference : null;
    }

    private static <X> FilterResult<X> negated(EvaluationContext evaluationContext, FilterResult<X> filterResult) {
        if (filterResult == null) return null;
        return new FilterResult<X>(filterResult.accepted.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> NegatedValue.negate(evaluationContext, e.getValue()), (v1, v2) -> v1)),
                NegatedValue.negate(evaluationContext, filterResult.rest));
    }

    // EXAMPLE: p == null, field != null


    public static final FilterMethod<Variable> INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE = value -> {
        if (value instanceof EqualsValue equalsValue) {
            boolean lhsIsNull = equalsValue.lhs.isNull();
            boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
            if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof VariableValue v) {
                Value clause = lhsIsNull ? NullValue.NULL_VALUE : NullValue.NOT_NULL_VALUE;
                return new FilterResult<Variable>(Map.of(v.variable, value), UnknownValue.EMPTY);
            }
        }
        return null;
    };


    // EXAMPLE: p == null, p != null

    public static final FilterMethod<ParameterInfo> INDIVIDUAL_NULL_OR_NOT_NULL_CLAUSE_ON_PARAMETER = value -> {
        if (value instanceof EqualsValue equalsValue) {
            boolean lhsIsNull = equalsValue.lhs.isNull();
            boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
            if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof VariableValue v && v.variable instanceof ParameterInfo p) {
                Value clause = lhsIsNull ? NullValue.NULL_VALUE : NullValue.NOT_NULL_VALUE;
                return new FilterResult<ParameterInfo>(Map.of(p, value), UnknownValue.EMPTY);
            }
        }
        return null;
    };

    // EXAMPLE: 0 == java.util.List.this.size()

    public static record ValueEqualsMethodCallNoParameters(MethodInfo methodInfo) implements FilterMethod<MethodValue> {

        @Override
        public FilterResult<MethodValue> apply(Value value) {
            if (value instanceof EqualsValue equalsValue) {
                MethodValue r = compatibleMethodValue(equalsValue.rhs);
                if (r != null) {
                    return new FilterResult<MethodValue>(Map.of(r, equalsValue.lhs), UnknownValue.EMPTY);
                }
                MethodValue l = compatibleMethodValue(equalsValue.lhs);
                if (l != null) {
                    return new FilterResult<MethodValue>(Map.of(l, equalsValue.rhs), UnknownValue.EMPTY);
                }
            }
            return null;
        }

        private MethodValue compatibleMethodValue(Value value) {
            if (value instanceof MethodValue methodValue && compatibleMethod(methodInfo, methodValue.methodInfo)) {
                return methodValue;
            }
            return null;
        }
    }

    private static boolean compatibleParameters(List<Value> parameters, List<Value> parametersInClause) {
        return ListUtil.joinLists(parameters, parametersInClause).allMatch(pair -> pair.k.equals(pair.v));
    }

    private static boolean compatibleMethod(MethodInfo methodInfo, MethodInfo methodInClause) {
        if (methodInClause == methodInfo) return true;
        if (methodInClause.methodInspection.get().parameters.size() != methodInfo.methodInspection.get().parameters.size())
            return false;
        Set<MethodInfo> overrides = methodInClause.typeInfo.overrides(methodInClause, true);
        return overrides.contains(methodInfo);
    }

    // EXAMPLE: java.util.List.contains("a")

    public static record MethodCallBooleanResult(MethodInfo methodInfo, List<Value> parameterValues,
                                                 Value boolValueTrue) implements FilterMethod<MethodValue> {

        @Override
        public FilterResult<MethodValue> apply(Value value) {
            if (value instanceof MethodValue methodValue && compatible(methodValue)) {
                return new FilterResult<MethodValue>(Map.of(methodValue, boolValueTrue), UnknownValue.EMPTY);
            }
            return null;
        }

        private boolean compatible(MethodValue methodValue) {
            return compatibleMethod(methodInfo, methodValue.methodInfo) &&
                    compatibleParameters(parameterValues, methodValue.parameters);
        }
    }

}
