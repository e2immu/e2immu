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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.MethodAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
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
    private final EvaluationContext evaluationContext;
    private final BooleanConstant defaultRest;
    private final FilterMode filterMode;

    public Filter(EvaluationContext evaluationContext, FilterMode filterMode) {
        this.evaluationContext = evaluationContext;
        this.defaultRest = defaultRest(evaluationContext.getPrimitives(), filterMode);
        this.filterMode = filterMode;
    }

    public BooleanConstant getDefaultRest() {
        return defaultRest;
    }

    /**
     * The result of filtering -> some clauses have been picked up, and put in <code>accepted</code>.
     * The remaining clauses are stored in <code>rest</code>.
     *
     * @param <X> depends on the filter method. Sometimes we filter on variables, sometimes on values.
     */
    public record FilterResult<X>(Map<X, Expression> accepted, Expression rest) {
    }


    /*
    Return null on no match
     */
    @FunctionalInterface
    public interface FilterMethod<X> {
        FilterResult<X> apply(Expression value);
    }

    public enum FilterMode {
        ALL,    // default value = TRUE
        ACCEPT, // normal state of the variable AFTER the escape; independent = AND; does not recurse into OrValues; default value = TRUE
        REJECT, // condition for escaping; independent = OR; does not recurse into AndValues; default value = FALSE
    }

    public <X> FilterResult<X> filter(Expression value, FilterMethod<X> filterMethod) {
        FilterResult<X> result = internalFilter(value, List.of(filterMethod));
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    public <X> FilterResult<X> filter(Expression value, List<FilterMethod<X>> filterMethods) {
        FilterResult<X> result = internalFilter(value, filterMethods);
        return result == null ? new FilterResult<>(Map.of(), value) : result;
    }

    private <X> FilterResult<X> internalFilter(Expression value, List<FilterMethod<X>> filterMethods) {
        AtomicReference<FilterResult<X>> filterResult = new AtomicReference<>();
        value.visit(v -> {
            if (!v.isUnknown()) {
                if (v instanceof Negation negatedValue) {
                    FilterResult<X> resultOfNegated = internalFilter(negatedValue.expression, filterMethods);
                    if (resultOfNegated != null) {
                        FilterResult<X> negatedResult = new FilterResult<>(resultOfNegated.accepted.entrySet().stream()
                                .collect(Collectors.toMap(Map.Entry::getKey,
                                        e -> Negation.negate(evaluationContext, e.getValue()), (v1, v2) -> v1)),
                                negateRest(resultOfNegated.rest));
                        filterResult.set(negatedResult);
                    }
                } else if (v instanceof And andValue) {
                    if (filterMode == FilterMode.ACCEPT || filterMode == FilterMode.ALL) {
                        filterResult.set(processAndOr(andValue.expressions(), filterMethods));
                    }
                } else if (v instanceof Or orValue) {
                    if (filterMode == FilterMode.REJECT || filterMode == FilterMode.ALL) {
                        filterResult.set(processAndOr(orValue.expressions(), filterMethods));
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

    private Expression negateRest(Expression rest) {
        // if rest = default rest, we keep it that way (means: empty rest)
        if (rest.equals(defaultRest)) return rest;
        if (rest instanceof BooleanConstant) throw new UnsupportedOperationException(); // when is this possible?
        return Negation.negate(evaluationContext, rest);
    }

    private <X> FilterResult<X> processAndOr(List<Expression> values,
                                             List<FilterMethod<X>> filterMethods) {
        List<FilterResult<X>> results = values.stream().map(v -> {
            FilterResult<X> sub = internalFilter(v, filterMethods);
            return sub == null ? new FilterResult<X>(Map.of(), v) : sub;
        }).collect(Collectors.toList());

        List<Expression> restList = results.stream().map(r -> r.rest).collect(Collectors.toList());
        Map<X, Expression> acceptedCombined = results.stream().flatMap(r -> r.accepted.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));

        Expression rest;
        if (restList.isEmpty()) rest = defaultRest(evaluationContext.getPrimitives(), filterMode);
        else if (restList.size() == 1) rest = restList.get(0);
        else if (filterMode == FilterMode.REJECT) {
            rest = new Or(evaluationContext.getPrimitives()).append(evaluationContext, restList.toArray(Expression[]::new));
        } else {
            rest = new And(evaluationContext.getPrimitives()).append(evaluationContext, restList.toArray(Expression[]::new));
        }
        return new FilterResult<>(acceptedCombined, rest);
    }

    private static BooleanConstant defaultRest(Primitives primitives, FilterMode filterMode) {
        return new BooleanConstant(primitives, filterMode != FilterMode.REJECT);
    }
    // some filter methods

    public FilterMethod<FieldReference> individualFieldClause(AnalyserContext analyserContext) {
        return individualFieldClause(analyserContext, false);
    }

    // EXAMPLE: field == null, field == constant, ...
    // exclusively used for Eventual
    public FilterMethod<FieldReference> individualFieldClause(AnalyserContext analyserContext, boolean acceptAndRemapLocalCopy) {
        return value -> {
            if (value instanceof Equals equalsValue) {
                FieldReference l = extractFieldReference(equalsValue.lhs, acceptAndRemapLocalCopy);
                FieldReference r = extractFieldReference(equalsValue.rhs, acceptAndRemapLocalCopy);
                if (l != null && r == null) {
                    // must make a new one because we could have remapped a local copy to its field
                    Expression expr = acceptAndRemapLocalCopy ?
                            new Equals(equalsValue.primitives, new VariableExpression(l), equalsValue.rhs, equalsValue.objectFlow)
                            : value;
                    return new FilterResult<FieldReference>(Map.of(l, expr), defaultRest);
                }
                if (r != null && l == null) {
                    Expression expr = acceptAndRemapLocalCopy ?
                            new Equals(equalsValue.primitives, equalsValue.lhs, new VariableExpression(r), equalsValue.objectFlow)
                            : value;
                    return new FilterResult<FieldReference>(Map.of(r, expr), defaultRest);
                }
            } else if (value instanceof GreaterThanZero gt0) {
                Expression expression = gt0.expression();
                List<Variable> vars = expression.variables();
                if (vars.size() == 1 && vars.get(0) instanceof FieldReference fr && acceptScope(fr)) {
                    return new FilterResult<FieldReference>(Map.of(fr, gt0), defaultRest);
                }
            } else if (Primitives.isBoolean(value.returnType())) {
                FieldReference fieldReference = extractBooleanFieldReference(analyserContext, value, acceptAndRemapLocalCopy);
                if (fieldReference != null) {
                    return new FilterResult<FieldReference>(Map.of(fieldReference, value), defaultRest);
                }
            }
            return null;
        };
    }

    private static FieldReference extractBooleanFieldReference(AnalyserContext analyserContext,
                                                               Expression value,
                                                               boolean acceptAndRemapLocalCopy) {
        Expression v;
        if (value instanceof Negation negation) v = negation.expression;
        else v = value;
        // @NotModified method returning a boolean
        if(value instanceof MethodCall mc) {
            MethodAnalysis methodAnalysis = analyserContext.getMethodAnalysis(mc.methodInfo);
            if(methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD) != Level.FALSE) return null;
            v = mc.object;
        }
        if (v instanceof VariableExpression ve
                && ve.variable() instanceof FieldReference fr
                && acceptScope(fr.scope)) return fr;
        if (acceptAndRemapLocalCopy && v instanceof VariableExpression ve
                && ve.variable() instanceof LocalVariableReference lvr
                && lvr.variable.isLocalCopyOf() instanceof FieldReference fr && acceptScope(fr.scope)) return fr;
        return null;
    }

    // we do not allow parameters or local variables
    private static boolean acceptScope(Variable scope) {
        return scope instanceof This || (scope instanceof FieldReference fr && acceptScope(fr.scope));
    }

    private static FieldReference extractFieldReference(Expression value, boolean acceptAndRemapLocalCopy) {
        if (value instanceof IsVariableExpression variableValue) {
            if (variableValue.variable() instanceof FieldReference fieldReference &&
                    acceptScope(fieldReference.scope)) return fieldReference;
            if (acceptAndRemapLocalCopy &&
                    variableValue.variable() instanceof LocalVariableReference lvr &&
                    lvr.variable.isLocalCopyOf() instanceof FieldReference fieldReference &&
                    acceptScope(fieldReference.scope)) return fieldReference;
        }
        return null;
    }

    // EXAMPLE: p == null, field != null


    public FilterMethod<Variable> individualNullOrNotNullClause() {
        return value -> {
            if (value instanceof Equals equalsValue) {
                boolean lhsIsNull = equalsValue.lhs.isNull();
                boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
                if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof IsVariableExpression v) {
                    return new FilterResult<Variable>(Map.of(v.variable(), value), defaultRest);
                }
            }
            return null;
        };
    }

    // EXAMPLE: p == null, p != null

    public FilterMethod<ParameterInfo> individualNullOrNotNullClauseOnParameter() {
        return value -> {
            if (value instanceof Equals equalsValue) {
                boolean lhsIsNull = equalsValue.lhs.isNull();
                boolean lhsIsNotNull = equalsValue.lhs.isNotNull();
                if ((lhsIsNull || lhsIsNotNull) && equalsValue.rhs instanceof IsVariableExpression v && v.variable() instanceof ParameterInfo p) {
                    return new FilterResult<ParameterInfo>(Map.of(p, value), defaultRest);
                }
            }
            return null;
        };
    }

    // EXAMPLE: 0 == java.util.Collection.this.size()  --> map java.util.Collection.this.size() onto 0
    // EXAMPLE: java.util.Collection.this.size() == o.e.a.t.BasicCompanionMethods_6.test(Set<java.lang.String>):0:strings.size() --> copy lhs onto rhs
    public static record ValueEqualsMethodCallNoParameters(Expression defaultRest,
                                                           MethodInfo methodInfo) implements FilterMethod<MethodCall> {

        @Override
        public FilterResult<MethodCall> apply(Expression value) {
            if (value instanceof Equals equalsValue) {
                MethodCall r = compatibleMethodValue(equalsValue.rhs);
                if (r != null) {
                    return new FilterResult<>(Map.of(r, equalsValue.lhs), defaultRest);
                }
                MethodCall l = compatibleMethodValue(equalsValue.lhs);
                if (l != null) {
                    return new FilterResult<>(Map.of(l, equalsValue.rhs), defaultRest);
                }
            }
            return null;
        }

        private MethodCall compatibleMethodValue(Expression value) {
            if (value instanceof MethodCall methodValue &&
                    methodValue.object instanceof IsVariableExpression vv &&
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

    public static record MethodCallBooleanResult(Expression defaultRest,
                                                 MethodInfo methodInfo,
                                                 List<Expression> parameterValues,
                                                 Expression boolValueTrue) implements FilterMethod<MethodCall> {

        @Override
        public FilterResult<MethodCall> apply(Expression value) {
            if (value instanceof MethodCall methodValue && compatible(methodValue)) {
                return new FilterResult<>(Map.of(methodValue, boolValueTrue), defaultRest);
            }
            return null;
        }

        private boolean compatible(MethodCall methodValue) {
            return compatibleMethod(methodInfo, methodValue.methodInfo) &&
                    compatibleParameters(parameterValues, methodValue.getParameterExpressions());
        }
    }

    public static record ExactValue(Expression defaultRest, Expression value) implements FilterMethod<Expression> {

        @Override
        public FilterResult<Expression> apply(Expression value) {
            if (this.value.equals(value))
                return new FilterResult<>(Map.of(value, value), defaultRest);
            return null;
        }
    }
}
