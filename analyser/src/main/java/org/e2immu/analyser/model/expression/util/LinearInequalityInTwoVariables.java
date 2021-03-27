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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.variable.Variable;

import java.util.List;

import static org.e2immu.analyser.model.expression.util.InequalityHelper.extractEquals;
import static org.e2immu.analyser.model.expression.util.InequalityHelper.onlyNotEquals;

public record LinearInequalityInTwoVariables(EvaluationContext evaluationContext,
                                             double a, Variable x,
                                             double b, Variable y,
                                             double c, boolean allowEquals) implements Inequality {
    public LinearInequalityInTwoVariables {
        assert evaluationContext != null;
        assert a != 0.0;
        assert b != 0.0;
        assert x != null;
        assert y != null;
        assert !x.equals(y);
    }

    public boolean accept(double px, double py) {
        double sum = a * px + b * py + c;
        return allowEquals ? sum >= 0 : sum > 0;
    }

    public boolean isOpenLeftX() {
        return a < 0;
    }

    public boolean isOpenRightX() {
        return a > 0;
    }

    public boolean isOpenLeftY() {
        return b < 0;
    }

    public boolean isOpenRightY() {
        return b > 0;
    }

    public Boolean accept(List<Expression> expressionsInX, List<Expression> expressionsInY) {
        if (onlyNotEquals(expressionsInX) || onlyNotEquals(expressionsInY)) return true;
        Double xEquals = extractEquals(expressionsInX);
        Double yEquals = extractEquals(expressionsInY);
        if (xEquals != null && yEquals != null) {
            return accept(xEquals, yEquals);
        }
        if (xEquals != null) {
            // we have x to a constant, and inequalities for y => linear inequality in one variable
            LinearInequalityInOneVariable inequality = new LinearInequalityInOneVariable(evaluationContext,
                    b, y, a * xEquals + c, allowEquals);
            return inequality.accept(expressionsInY);
        }
        if (yEquals != null) {
            LinearInequalityInOneVariable inequality = new LinearInequalityInOneVariable(evaluationContext,
                    a, x, b * yEquals + c, allowEquals);
            return inequality.accept(expressionsInX);
        }
        // at least one inequality on x, at least one on y; they can be expressed as intervals
        Interval intervalX = Interval.extractInterval(evaluationContext, expressionsInX);
        Interval intervalY = Interval.extractInterval(evaluationContext, expressionsInY);
        if (intervalX == null || intervalY == null) return null;

        if (intervalX.isClosed() && intervalY.isClosed()) {
            // is a box
            return accept(intervalX.left(), intervalY.left()) || accept(intervalX.right(), intervalY.left()) ||
                    accept(intervalX.left(), intervalY.right()) || accept(intervalX.right(), intervalY.right());
        }
        if (intervalX.isClosed()) {
            if (intervalY.isOpenLeft()) { // looks like the capital letter PI
                return accept(intervalX.left(), intervalY.right()) || accept(intervalX.right(), intervalY.right()) ||
                        isOpenLeftY();
            }
            // looks like the letter U
            return accept(intervalX.left(), intervalY.left()) || accept(intervalX.right(), intervalY.left()) ||
                    isOpenRightY();
        }
        if (intervalY.isClosed()) {
            if (intervalX.isOpenLeft()) { // looks like ]
                return accept(intervalX.right(), intervalY.left()) || accept(intervalX.right(), intervalY.right()) ||
                        isOpenLeftX();
            }
            // looks like [
            return accept(intervalX.left(), intervalY.left()) || accept(intervalX.right(), intervalY.left()) ||
                    isOpenRightX();
        }
        // neither are closed; look like 90 degrees rotations of L; first, try the corner point
        if (accept(intervalX.isOpenLeft() ? intervalX.right() : intervalX.left(), intervalY.isOpenLeft() ? intervalY.right() : intervalY.left()))
            return true;

        return intervalX.isOpenRight() && isOpenRightX() || intervalX.isOpenLeft() && isOpenLeftX()
                || intervalY.isOpenRight() && isOpenRightY() || intervalY.isOpenLeft() && isOpenLeftY();
    }

}
