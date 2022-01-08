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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PropertyWrapper extends BaseExpression implements Expression, ExpressionWrapper {
    private final Expression expression;
    private final Expression state;
    private final Map<Property, DV> properties;
    private final LinkedVariables linkedVariables;
    private final ParameterizedType castType;


    public PropertyWrapper(Expression expression, Expression state, Map<Property, DV> properties, LinkedVariables linkedVariables, ParameterizedType castType) {
        super(expression.getIdentifier());
        assert !(expression instanceof Negation) : "we always want the negation to be on the outside";
        this.expression = expression;
        this.state = state;
        this.properties = properties;
        this.linkedVariables = linkedVariables;
        this.castType = castType;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new PropertyWrapper(expression.translate(translationMap),
                state == null ? null : state.translate(translationMap),
                properties,
                linkedVariables == null ? null : linkedVariables.translate(translationMap),
                castType == null ? null : translationMap.translateType(castType));
    }

    @Override
    public Expression getExpression() {
        return expression;
    }

    @Override
    public int wrapperOrder() {
        return WRAPPER_ORDER_PROPERTY;
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        EvaluationResult reValue = expression.reEvaluate(evaluationContext, translation);
        return reEvaluated(evaluationContext, reValue);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult reValue = expression.evaluate(evaluationContext, forwardEvaluationInfo);
        return reEvaluated(evaluationContext, reValue);
    }

    private EvaluationResult reEvaluated(EvaluationContext evaluationContext, EvaluationResult reValue) {
        Expression newValue = reValue.value();
        Map<Property, DV> reduced = reduce(evaluationContext, newValue, properties);
        Expression result = reduced.isEmpty() ? newValue : PropertyWrapper.propertyWrapper(newValue, reduced);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);
        return builder.setExpression(result).build();
    }

    private static Map<Property, DV> reduce(EvaluationContext evaluationContext,
                                            Expression expression,
                                            Map<Property, DV> map) {
        return map.entrySet().stream()
                .filter(e -> {
                    DV v = evaluationContext.getProperty(expression, e.getKey(), true, false);
                    return !v.equals(e.getValue());
                })
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Expression propertyWrapper(Expression value, Map<Property, DV> properties) {
        return new PropertyWrapper(value, null, properties, null, null);
    }

    public static Expression propertyWrapper(Expression value, Map<Property, DV> properties, ParameterizedType castType) {
        return new PropertyWrapper(value, null, properties, null, castType);
    }

    public static Expression propertyWrapper(Expression value, LinkedVariables linkedVariables) {
        return new PropertyWrapper(value, null, Map.of(), linkedVariables, null);
    }

    public static Expression addState(Expression expression, Expression state) {
        assert state != null;
        return new PropertyWrapper(expression, state, Map.of(), null, null);
    }

    public static Expression addState(Expression expression, Expression state, Map<Property, DV> properties) {
        assert state != null;
        return new PropertyWrapper(expression, state, properties, null, null);
    }

    @Override
    public ParameterizedType returnType() {
        return castType != null ? castType : expression.returnType();
    }

    @Override
    public Precedence precedence() {
        return expression.precedence();
    }

    @Override
    public int order() {
        return expression.order();
    }

    @Override
    public int internalCompareTo(Expression v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String propertyString = properties.entrySet().stream()
                .filter(e -> e.getValue().gt(e.getKey().falseDv))
                .map(PropertyWrapper::stringValue).sorted().collect(Collectors.joining(","));
        OutputBuilder outputBuilder = new OutputBuilder().add(expression.output(qualification));
        boolean haveComment = !propertyString.isBlank() || castType != null || linkedVariables != null || state != null;
        if (haveComment) {
            outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT);
            boolean added = false;
            if (!propertyString.isBlank()) {
                outputBuilder.add(new Text(propertyString));
                added = true;
            }
            if (linkedVariables != null) {
                if (added) {
                    outputBuilder.add(Space.ONE);
                }
                added = true;
                String prefix = linkedVariables.isDelayed() ? "{DL " : "{L ";
                String main = linkedVariables.variables().entrySet().stream()
                        .sorted(Comparator.comparing(e -> e.getKey().simpleName()))
                        .map(e -> e.getKey().simpleName() + ":" + e.getValue().toString())
                        .collect(Collectors.joining(",", prefix, "}"));
                outputBuilder.add(new Text(main));
            }
            if (castType != null) {
                if (added) {
                    outputBuilder.add(Space.ONE);
                }
                added = true;
                outputBuilder.add(Symbol.LEFT_PARENTHESIS)
                        .add(castType.output(qualification))
                        .add(Symbol.RIGHT_PARENTHESIS);
            }
            if (state != null) {
                if (added) {
                    outputBuilder.add(Space.ONE);
                }
                outputBuilder.add(state.output(qualification));
            }
            outputBuilder.add(Symbol.RIGHT_BLOCK_COMMENT);
        }
        return outputBuilder;
    }

    private static String stringValue(Map.Entry<Property, DV> e) {
        if (e.getKey() == Property.INDEPENDENT && e.getValue().equals(MultiLevel.INDEPENDENT_1_DV))
            return "@Dependent1";
        return e.getKey().toString();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        if (castType != null && (
                property == Property.IMMUTABLE || property == Property.CONTAINER ||
                        property == Property.INDEPENDENT)) {
            return evaluationContext.getAnalyserContext().getProperty(castType, property);
        }
        DV inMap = properties.getOrDefault(property, null);
        if (inMap != null) return inMap;
        return evaluationContext.getProperty(expression, property, duringEvaluation, false);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        if (linkedVariables != null) return linkedVariables;
        return expression.linkedVariables(evaluationContext);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public <T extends Expression> T asInstanceOf(Class<T> clazz) {
        return expression.asInstanceOf(clazz);
    }

    @Override
    public boolean isInstanceOf(Class<? extends Expression> clazz) {
        return expression.isInstanceOf(clazz);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Expression oUnboxed)) return false;
        Expression unboxed = this;
        while (unboxed instanceof PropertyWrapper propertyWrapper) {
            unboxed = propertyWrapper.expression;
        }
        while (oUnboxed instanceof PropertyWrapper propertyWrapper) {
            oUnboxed = propertyWrapper.expression;
        }
        return unboxed.equals(oUnboxed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }

    @Override
    public boolean hasState() {
        return state != null && !(state instanceof BooleanConstant bc && bc.constant());
    }

    public Expression state() {
        return state;
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay()
                .merge(state == null ? CausesOfDelay.EMPTY: state.causesOfDelay())
                .merge(linkedVariables == null ? CausesOfDelay.EMPTY: linkedVariables.causesOfDelay());
    }

    public Expression expression() {
        return expression;
    }

    public Map<Property, DV> properties() {
        return properties;
    }

    public LinkedVariables linkedVariables() {
        return linkedVariables;
    }

    public ParameterizedType castType() {
        return castType;
    }

}
