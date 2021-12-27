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

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@E2Container
public class EnclosedExpression extends ElementImpl implements Expression {

    private final Expression inner;

    public EnclosedExpression(Identifier identifier, Expression inner) {
        super(identifier);
        this.inner = inner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnclosedExpression that = (EnclosedExpression) o;
        return inner.equals(that.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inner);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new EnclosedExpression(identifier, translationMap.translateExpression(inner));
    }

    @Override
    public int order() {
        return inner.order();
    }

    @Override
    public ParameterizedType returnType() {
        return inner.returnType();
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(Symbol.LEFT_PARENTHESIS).add(inner.output(qualification)).add(Symbol.RIGHT_PARENTHESIS);
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(inner);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        return inner.evaluate(evaluationContext, forwardEvaluationInfo);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return inner.causesOfDelay();
    }

    public Expression inner() {
        return inner;
    }

    @Override
    public boolean isInstanceOf(Class<? extends Expression> clazz) {
        return inner.isInstanceOf(clazz);
    }

    @Override
    public <T extends Expression> T asInstanceOf(Class<T> clazz) {
        return inner.asInstanceOf(clazz);
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        return inner.erasureTypes(typeContext);
    }
}
