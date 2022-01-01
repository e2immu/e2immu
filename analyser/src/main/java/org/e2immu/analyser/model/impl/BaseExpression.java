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

package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;

import java.util.concurrent.atomic.AtomicReference;

public abstract class BaseExpression extends ElementImpl implements Expression {

    protected BaseExpression(Identifier identifier) {
        super(identifier);
    }

    @Override
    public boolean isReturnValue() {
        UnknownExpression ue = asInstanceOf(UnknownExpression.class);
        return ue != null && UnknownExpression.RETURN_VALUE.equals(ue.msg());
    }

    @Override
    public boolean isNotNull() {
        Negation negatedValue = asInstanceOf(Negation.class);
        return negatedValue != null && negatedValue.expression.isInstanceOf(NullConstant.class);
    }

    @Override
    public boolean isNull() {
        return isInstanceOf(NullConstant.class);
    }

    @Override
    public boolean equalsNull() {
        if (this instanceof Negation) return false;
        Equals equals;
        if ((equals = asInstanceOf(Equals.class)) != null) {
            return equals.lhs.isNull();
        }
        return false;
    }

    @Override
    public boolean equalsNotNull() {
        if (!(this instanceof Negation negation)) return false;
        Equals equals;
        if ((equals = negation.expression.asInstanceOf(Equals.class)) != null) {
            return equals.lhs.isNull();
        }
        return false;
    }

    @Override
    public boolean isBoolValueTrue() {
        BooleanConstant boolValue;
        return ((boolValue = this.asInstanceOf(BooleanConstant.class)) != null) && boolValue.getValue();
    }

    @Override
    public boolean isBoolValueFalse() {
        BooleanConstant boolValue;
        return ((boolValue = this.asInstanceOf(BooleanConstant.class)) != null) && !boolValue.getValue();
    }

    @Override
    public boolean isBooleanConstant() {
        return isInstanceOf(BooleanConstant.class);
    }

    @Override
    public boolean isComputeProperties() {
        return !(this instanceof UnknownExpression);
    }

    @Override
    public boolean isInitialReturnExpression() {
        return this instanceof UnknownExpression unknownExpression && unknownExpression.msg().equals(UnknownExpression.RETURN_VALUE);
    }

    @Override
    public Expression stateTranslateThisTo(FieldReference fieldReference) {
        Expression state = state();
        if (state.isBooleanConstant()) return state;
        // the "this" in the state can belong to the type of the object, or any of its super types
        This thisVar = findThis();
        return state.translate(new TranslationMapImpl.Builder().put(thisVar, fieldReference).build());
    }

    private This findThis() {
        AtomicReference<This> thisVar = new AtomicReference<>();
        state().visit(e -> {
            VariableExpression ve;
            if ((ve = e.asInstanceOf(VariableExpression.class)) != null && ve.variable() instanceof This tv) {
                thisVar.set(tv);
                return false;
            }
            return true;
        });
        return thisVar.get();
    }

    @Override
    public Expression createDelayedValue(EvaluationContext evaluationContext, CausesOfDelay causes) {
        return DelayedExpression.forDelayedValueProperties(returnType(),
                linkedVariables(evaluationContext).changeAllToDelay(causes), causes);
    }

    @Override
    public int compareTo(Expression v) {
        return ExpressionComparator.SINGLETON.compare(this, v);
    }
}
