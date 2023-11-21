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
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util.PackedIntMap;

import java.util.Objects;
import java.util.function.Predicate;

public class TypeExpression extends BaseExpression implements Expression {
    public final ParameterizedType parameterizedType;
    public final Diamond diamond;

    public TypeExpression(Identifier identifier, ParameterizedType parameterizedType, Diamond diamond) {
        super(identifier, 1);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.diamond = diamond;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeExpression that = (TypeExpression) o;
        return parameterizedType.equals(that.parameterizedType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType);
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(parameterizedType.output(qualification, false, diamond));
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return parameterizedType.typesReferenced(true);
    }

    @Override
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return parameterizedType.typesReferenced2(weight);
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        return builder.setExpression(new TypeExpression(identifier, parameterizedType, diamond)).build();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if(translated != this) return translated;

        ParameterizedType translatedType = translationMap.translateType(parameterizedType);
        if (translatedType == parameterizedType) return this;
        return new TypeExpression(identifier, translatedType, diamond);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_TYPE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString().compareTo(((TypeExpression) v).parameterizedType.detailedString());
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION) return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        if (property == Property.IMMUTABLE) {
            // used by EvaluationContext.extractHiddenContent
            return context.getAnalyserContext().typeImmutable(parameterizedType);
        }
        return property.falseDv;
    }

    @Override
    public boolean cannotHaveState() {
        return true;
    }
}
