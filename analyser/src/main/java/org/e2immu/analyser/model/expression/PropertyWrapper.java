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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class PropertyWrapper extends BaseExpression implements Expression, ExpressionWrapper {
    private final Expression expression;
    private final Expression state;
    private final Map<Property, DV> properties;
    private final LinkedVariables linkedVariables;
    private final ParameterizedType castType;


    public PropertyWrapper(Expression expression, Expression state, Map<Property, DV> properties, LinkedVariables linkedVariables, ParameterizedType castType) {
        super(expression.getIdentifier(), expression.getComplexity());
        assert !(expression instanceof Negation) : "we always want the negation to be on the outside";
        this.expression = expression;
        this.state = state != null && state.isBoolValueTrue() ? null : state;
        this.properties = properties != null && properties.isEmpty() ? null : properties;
        this.linkedVariables = linkedVariables != null && linkedVariables.isEmpty() ? null : linkedVariables;
        this.castType = castType;
        assert state != null && !state.isBoolValueTrue() ||
                properties != null && !properties.isEmpty() ||
                linkedVariables != null && !linkedVariables.isEmpty() ||
                castType != null;
    }

    public static Expression wrapPreventIncrementalEvaluation(Expression expression) {
        return new PropertyWrapper(expression, null, Map.of(Property.MARK_CLEAR_INCREMENTAL, DV.TRUE_DV), null, null);
    }

    public Expression copy(Expression other) {
        return new PropertyWrapper(other, state, properties, linkedVariables, castType);
    }

    public boolean hasProperty(Property property) {
        return properties != null && properties.containsKey(property);
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression transState = state == null ? null : state.translate(inspectionProvider, translationMap);
        Expression tex = expression.translate(inspectionProvider, translationMap);
        LinkedVariables transLv = linkedVariables == null ? null : linkedVariables.translate(translationMap);
        ParameterizedType transType = castType == null ? null : translationMap.translateType(castType);
        if (transState == state && tex == expression && transLv == linkedVariables && transType == castType) {
            return this;
        }
        return new PropertyWrapper(tex, transState, properties, transLv, transType);
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
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult reValue = expression.evaluate(context, forwardEvaluationInfo);
        return reEvaluated(context, reValue);
    }

    private EvaluationResult reEvaluated(EvaluationResult evaluationContext, EvaluationResult reValue) {
        Expression newValue = reValue.value();
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reValue);

        // most importantly: we don't want any double wrappers with exactly the same info!
        if (newValue instanceof PropertyWrapper pw && Objects.equals(pw.castType, castType) &&
                Objects.equals(pw.state, state) && Objects.equals(pw.properties, properties) &&
                Objects.equals(pw.linkedVariables, linkedVariables)) {
            return builder.setExpression(pw).build();
        }
        // IMPROVE it would really be good if we never had two PropertyWrappers in a row

        Map<Property, DV> reduced = properties == null ? null : reduce(evaluationContext, newValue, properties);
        boolean dropWrapper = (reduced == null || reduced.isEmpty()) && state == null && linkedVariables == null && castType == null;
        Expression result = dropWrapper ? newValue : new PropertyWrapper(newValue, state, reduced, linkedVariables, castType);

        return builder.setExpression(result).build();
    }

    private static Map<Property, DV> reduce(EvaluationResult context,
                                            Expression expression,
                                            Map<Property, DV> map) {
        return map.entrySet().stream()
                .filter(e -> {
                    DV v = context.evaluationContext().getProperty(expression, e.getKey(), true, false);
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

    public static Expression propertyWrapper(Expression value, LinkedVariables linkedVariables, PropertyWrapper other) {
        return new PropertyWrapper(value, other.state, other.properties, linkedVariables, other.castType);
    }

    public static Expression propertyWrapper(Expression value, LinkedVariables linkedVariables, Map<Property, DV> altProps) {
        return new PropertyWrapper(value, null, altProps, linkedVariables, null);
    }

    public static Expression propertyWrapper(Expression value, LinkedVariables linkedVariables) {
        if (value instanceof PropertyWrapper pw) {
            return new PropertyWrapper(pw.expression, pw.state, pw.properties,
                    pw.linkedVariables == null ? linkedVariables : pw.linkedVariables.merge(linkedVariables), pw.castType);
        }
        return new PropertyWrapper(value, null, null, linkedVariables, null);
    }

    public static Expression addState(Expression expression, Expression state) {
        assert state != null;
        return new PropertyWrapper(expression, state, null, null, null);
    }

    public static Expression addState(Expression expression, Expression state, Map<Property, DV> properties) {
        assert state != null;
        return new PropertyWrapper(expression, state, properties, null, null);
    }


    @Override
    public Expression generify(EvaluationContext evaluationContext) {
        if (hasState()) {
            Expression generified = expression.generify(evaluationContext);
            if ((properties == null || properties.isEmpty()) && linkedVariables == null && castType == null) {
                return generified;
            }
            return new PropertyWrapper(generified, null, properties, linkedVariables, castType);
        }
        return this;
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
        if (v instanceof PropertyWrapper pw) {
            int c = nullLast(state, pw.state, Comparator.naturalOrder());
            if (c != 0) return c;
            int d = nullLast(castType, pw.castType, Comparator.comparing(ParameterizedType::toString));
            if (d != 0) return d;
            int e = nullLast(properties, pw.properties, Properties::compareMaps);
            if (e != 0) return e;
            return nullLast(linkedVariables, pw.linkedVariables, Comparator.naturalOrder());
        }
        throw new UnsupportedOperationException();
    }

    private static <T> int nullLast(T t1, T t2, Comparator<T> comparator) {
        if (t1 != null && t2 == null) return -1;
        if (t2 != null && t1 == null) return 1;
        if (t1 == null) return 0;
        return comparator.compare(t1, t2);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String propertyString = properties == null ? "" : properties.entrySet().stream()
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
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (castType != null && (
                property == Property.IMMUTABLE || property == Property.CONTAINER ||
                        property == Property.INDEPENDENT)) {
            return context.getAnalyserContext().getProperty(castType, property, false);
        }
        DV inMap = properties == null ? null : properties.getOrDefault(property, null);
        if (inMap != null) return inMap;
        return context.evaluationContext().getProperty(expression, property, duringEvaluation, false);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        if (linkedVariables != null) return linkedVariables;
        return expression.linkedVariables(context);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
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

    /*
    Which equality to choose? purely one based on the expression being wrapped (which would be compatible with the
    comparator), or a literal one, looking at all the fields?

    We must choose the literal one, or implement unwrapping in EVERY equality in Expression subclasses, otherwise
    equals() is not symmetrical.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropertyWrapper pw)) return false;
        return expression.equals(pw.expression)
                && Objects.equals(state, pw.state)
                && Objects.equals(castType, pw.castType)
                && Objects.equals(properties, pw.properties)
                && Objects.equals(linkedVariables, pw.linkedVariables);
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
        return expression.causesOfDelay().merge(state == null ? CausesOfDelay.EMPTY : state.causesOfDelay());
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        Expression e = expression.causesOfDelay().isDelayed() ? expression.mergeDelays(causesOfDelay) : expression;
        Expression s = state == null ? null : state.causesOfDelay().isDelayed() ? state.mergeDelays(causesOfDelay) : state;
        return new PropertyWrapper(e, s, properties, linkedVariables, castType);
    }

    @Override
    public boolean isConstant() {
        return expression.isConstant();
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

    @Override
    public DV hardCodedPropertyOrNull(Property property) {
        DV inMap = properties == null ? null : properties.getOrDefault(property, null);
        if (inMap != null) return inMap;
        return expression.hardCodedPropertyOrNull(property);
    }

    @Override
    public Set<Variable> directAssignmentVariables() {
        if (linkedVariables != null) {
            return linkedVariables.directAssignmentVariables();
        }
        return Set.of();
    }

    @Override
    public Expression unwrapIfConstant() {
        if (expression.isConstant()) {
            return expression;
        }
        return this;
    }

    public Expression unwrapState() {
        if (linkedVariables == null && properties == null && castType == null) return expression;
        return new PropertyWrapper(expression, null, properties, linkedVariables, castType);
    }
}
