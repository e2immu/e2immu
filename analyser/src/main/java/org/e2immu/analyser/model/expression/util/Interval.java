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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.GreaterThanZero;

import java.util.List;

public record Interval(double left, boolean leftIncluded, double right, boolean rightIncluded) {

    public Interval {
        assert Double.isFinite(left) || Double.isFinite(right);
        assert left < right || left == right && (leftIncluded || rightIncluded);
        assert Double.isFinite(left) || left == Double.NEGATIVE_INFINITY;
        assert Double.isFinite(right) || right == Double.POSITIVE_INFINITY;
    }

    public boolean isPoint() {
        return left == right;
    }

    public boolean isOpenLeft() {
        return left == Double.NEGATIVE_INFINITY && Double.isFinite(right);
    }

    public boolean isOpenRight() {
        return right == Double.POSITIVE_INFINITY && Double.isFinite(left);
    }

    public boolean isClosed() {
        return Double.isFinite(left) && Double.isFinite(right);
    }

    public Interval combine(Interval other) {
        if (isOpenLeft()) {
            assert other.isOpenRight();
            return new Interval(other.left, other.leftIncluded, right, other.rightIncluded);
        }
        if (isOpenRight()) {
            assert other.isOpenLeft();
            return new Interval(left, leftIncluded, other.right, other.rightIncluded);
        }
        throw new IllegalStateException();
    }

    public static Interval extractInterval(List<Expression> expressions) {
        if (expressions.size() == 1) {
            return extractInterval(expressions.get(0));
        }
        if (expressions.size() == 2) {
            Interval i1 = extractInterval(expressions.get(0));
            Interval i2 = extractInterval(expressions.get(1));
            return i1 == null || i2 == null ? null : i1.combine(i2);
        }
        return null;
    }

    public static Interval extractInterval(Expression expression) {
        if (expression instanceof GreaterThanZero ge) {
            Inequality inequality = InequalityHelper.extract(ge);
            if (inequality instanceof LinearInequalityInOneVariable one) return one.interval();
        }
        return null;
    }
}
