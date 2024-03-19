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
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.MultiExpression;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ArrayInitializer extends BaseExpression implements Expression {

    public final MultiExpression multiExpression;
    private final ParameterizedType commonType;

    public ArrayInitializer(Identifier identifier, InspectionProvider inspectionProvider, List<Expression> values) {
        super(identifier, values.stream().mapToInt(Expression::getComplexity).sum() + 1);
        this.multiExpression = MultiExpression.create(values);
        this.commonType = multiExpression.commonType(inspectionProvider);
    }

    public ArrayInitializer(InspectionProvider inspectionProvider,
                            List<Expression> values,
                            ParameterizedType formalCommonType) {
        this(Identifier.joined("array init", values.stream().map(Expression::getIdentifier).toList()),
                inspectionProvider, values, formalCommonType);
    }

    public ArrayInitializer(Identifier identifier,
                            InspectionProvider inspectionProvider,
                            List<Expression> values,
                            ParameterizedType formalCommonType) {
        super(identifier, values.stream().mapToInt(Expression::getComplexity).sum() + 1);
        this.multiExpression = MultiExpression.create(values);
        ParameterizedType multiCommon = multiExpression.commonType(inspectionProvider);
        this.commonType = formalCommonType == null ? multiCommon
                : formalCommonType.commonType(inspectionProvider, multiCommon);
    }

    private ArrayInitializer(Identifier identifier, MultiExpression multiExpression, ParameterizedType commonType) {
        super(identifier, Arrays.stream(multiExpression.expressions())
                .mapToInt(Expression::getComplexity).sum() + 1);
        this.multiExpression = multiExpression;
        this.commonType = commonType;
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        // IMPROVE can be made more efficient, make a TranslationCollector on arrays
        List<Expression> exs = multiExpression.stream().toList();
        List<Expression> translatedExpressions = exs.stream().map(e -> e.translate(inspectionProvider, translationMap))
                .collect(TranslationCollectors.toList(exs));
        ParameterizedType translatedType = translationMap.translateType(commonType);
        if (translatedType == commonType && translatedExpressions == exs) return this;
        return new ArrayInitializer(identifier, inspectionProvider, translatedExpressions, translatedType);
    }

    @Override
    public ParameterizedType returnType() {
        return new ParameterizedType(commonType.typeInfo, 1);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACE)
                .add(multiExpression.stream().map(expression -> expression.output(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACE);
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }


    @Override
    public List<? extends Element> subElements() {
        return List.of(multiExpression.expressions());
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        List<EvaluationResult> results = multiExpression.stream()
                .map(e -> e.evaluate(context, forwardEvaluationInfo))
                .collect(Collectors.toList());
        List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());

        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context).compose(results);
        builder.setExpression(new ArrayInitializer(identifier, context.getAnalyserContext(), values, commonType));

        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((ArrayInitializer) v).multiExpression.expressions());
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return Arrays.stream(multiExpression.expressions()).map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        MultiExpression multi = new MultiExpression(Arrays.stream(multiExpression.expressions())
                .map(e -> e.isDelayed() ? e.mergeDelays(causesOfDelay) : e)
                .toArray(Expression[]::new));
        return new ArrayInitializer(identifier, multi, commonType);
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (multiExpression.isEmpty()) {
            return switch (property) {
                case EXTERNAL_IMMUTABLE, IMMUTABLE -> MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
                case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
                case CONSTANT -> DV.TRUE_DV;
                case CONTAINER_RESTRICTION, CONTAINER -> MultiLevel.CONTAINER_DV;
                case EXTERNAL_IGNORE_MODIFICATIONS, IGNORE_MODIFICATIONS -> MultiLevel.NOT_IGNORE_MODS_DV;
                case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV;
                case IDENTITY -> DV.FALSE_DV;
                default -> throw new UnsupportedOperationException("Property " + property);
            };
        }
        if (Property.NOT_NULL_EXPRESSION == property) {
            DV notNull = multiExpression.getProperty(context, property, duringEvaluation);
            if (notNull.isDelayed()) return notNull;
            return MultiLevel.composeOneLevelMoreNotNull(notNull);
        }
        if (Property.EXTERNAL_IMMUTABLE == property || Property.IMMUTABLE == property) {
            // it is an array
            return MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV;
        }
        if (Property.CONTAINER_RESTRICTION == property || Property.CONTAINER == property) {
            return MultiLevel.CONTAINER_DV;
        }
        if (Property.EXTERNAL_IGNORE_MODIFICATIONS == property || Property.IGNORE_MODIFICATIONS == property) {
            return MultiLevel.NOT_IGNORE_MODS_DV;
        }
        // default is to refer to each of the components
        return multiExpression.getProperty(context, property, duringEvaluation);
    }

    @Override
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        return multiExpression.variables(descendIntoFieldReferences);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayInitializer that = (ArrayInitializer) o;
        return Arrays.equals(multiExpression.expressions(), that.multiExpression.expressions());
    }

    @Override
    public int hashCode() {
        return Objects.hash((Object[]) multiExpression.expressions());
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            multiExpression.stream().forEach(v -> v.visit(predicate));
        }
    }

    @Override
    public void visit(Visitor visitor) {
        if (visitor.beforeExpression(this)) {
           multiExpression.stream().forEach(v -> v.visit(visitor));
        }
        visitor.afterExpression(this);
    }

    @Override
    public boolean isConstant() {
        return multiExpression.stream().allMatch(Expression::isConstant);
    }
}
