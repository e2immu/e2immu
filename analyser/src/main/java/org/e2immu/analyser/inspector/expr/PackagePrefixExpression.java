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

package org.e2immu.analyser.inspector.expr;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;

import java.util.Objects;
import java.util.function.Predicate;

final class PackagePrefixExpression extends BaseExpression implements Expression {
    private final PackagePrefix packagePrefix;

    PackagePrefixExpression(PackagePrefix packagePrefix) {
        super(Identifier.constant(packagePrefix));
        this.packagePrefix = packagePrefix;
    }

    @Override
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Precedence precedence() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return 0;
    }

    public PackagePrefix packagePrefix() {
        return packagePrefix;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PackagePrefixExpression) obj;
        return Objects.equals(this.packagePrefix, that.packagePrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packagePrefix);
    }

    @Override
    public String toString() {
        return "PackagePrefixExpression[" +
                "packagePrefix=" + packagePrefix + ']';
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        throw new UnsupportedOperationException();
    }
}
