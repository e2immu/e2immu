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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.Property;
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

/*
A final field can have been initialised with multiple different values; in some situations
it pays to keep track of all of them.
 */
public class MultiValue extends BaseExpression implements Expression {

    public final MultiExpression multiExpression;
    private final ParameterizedType commonType;
    private final InspectionProvider inspectionProvider;

    public MultiValue(Identifier identifier,
                      InspectionProvider inspectionProvider,
                      MultiExpression multiExpression,
                      ParameterizedType formalCommonType) {
        super(identifier, multiExpression.getComplexity());
        this.commonType = formalCommonType.commonType(inspectionProvider, multiExpression.commonType(inspectionProvider));
        this.multiExpression = multiExpression;
        this.inspectionProvider = inspectionProvider;
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Expression> multi = multiExpression.stream().toList();
        List<Expression> translated = multi.stream()
                .map(e -> e.translate(inspectionProvider, translationMap)).collect(TranslationCollectors.toList(multi));
        ParameterizedType translatedType = translationMap.translateType(commonType);
        if (multi == translated && translatedType == commonType) return this;
        return new MultiValue(identifier, this.inspectionProvider, MultiExpression.create(translated), translatedType);
    }

    @Override
    public ParameterizedType returnType() {
        return commonType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder()
                .add(Symbol.LEFT_BRACKET)
                .add(multiExpression.stream().map(expression -> expression.output(qualification))
                        .collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_BRACKET);
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
    public EvaluationResult evaluate(EvaluationResult context,
                                     ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        for (Expression expression : multiExpression.expressions()) {
            EvaluationResult result = expression.evaluate(context, forwardEvaluationInfo);
            builder.compose(result);
        }
        return builder.build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_ARRAY;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return Arrays.compare(multiExpression.expressions(), ((MultiValue) v).multiExpression.expressions());
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (Property.NOT_NULL_EXPRESSION == property) {
            DV notNull = multiExpression.getProperty(context, property, duringEvaluation);
            if (notNull.isDelayed()) return notNull;
            return MultiLevel.composeOneLevelLessNotNull(notNull); // default = @NotNull level 0
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
        MultiValue that = (MultiValue) o;
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
            multiExpression.stream().forEach(e -> e.visit(visitor));
        }
        visitor.afterExpression(this);
    }
}
