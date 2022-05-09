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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.E2Container;

import java.util.Objects;
import java.util.function.Predicate;

@E2Container
public final class EmptyExpression extends BaseExpression implements Expression {
    public static final EmptyExpression EMPTY_EXPRESSION = new EmptyExpression("<empty>");
    public static final EmptyExpression DEFAULT_EXPRESSION = new EmptyExpression("<default>"); // negation of the disjunction of all earlier conditions
    public static final EmptyExpression FINALLY_EXPRESSION = new EmptyExpression("<finally>"); // always true condition
    public static final EmptyExpression NO_RETURN_VALUE = new EmptyExpression("<no return value>"); // assigned to void methodsprivate final String msg;

    private final String msg;

    public EmptyExpression(String msg) {
        super(Identifier.constant("__unknown_" + msg + "__"));
        this.msg = msg;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text(msg));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return new EvaluationResult.Builder(context).build();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return property.falseDv;
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return this;
    }

    public String msg() {
        return msg;
    }

    @Override
    public int hashCode() {
        return Objects.hash(msg);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }
}
