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
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;

import java.util.Objects;
import java.util.Set;

/**
 * Specifically used to transfer @Mark(" ...") at CONTRACT level.
 */
public final class ContractMark extends BaseExpression implements Expression {
    private final Set<FieldInfo> fields;

    /**
     */
    public ContractMark(Set<FieldInfo> fields) {
        super(Identifier.constant(ContractMark.class));
        this.fields = fields;
    }

    @Override
    public ParameterizedType returnType() {
        return fields.stream().findFirst().orElseThrow().type;
    }

    @Override
    public Precedence precedence() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        return null;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_NO_VALUE;
    }

    @Override
    public String toString() {
        return fields().toString();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return DV.FALSE_DV;
    }

    public Set<FieldInfo> fields() {
        return fields;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ContractMark) obj;
        return Objects.equals(this.fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }

}
