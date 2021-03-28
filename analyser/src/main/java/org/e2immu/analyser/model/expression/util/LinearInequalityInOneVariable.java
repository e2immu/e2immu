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

import java.util.List;

import static org.e2immu.analyser.model.expression.util.InequalityHelper.extractEquals;
import static org.e2immu.analyser.model.expression.util.InequalityHelper.onlyNotEquals;

/*
av + b  >= 0 or av + b > 0
(we're not using x because IntelliJ complains passing y onto x)
 */
public record LinearInequalityInOneVariable(EvaluationContext evaluationContext,
                                            double a,
                                            OneVariable v,
                                            double b,
                                            boolean allowEquals) implements Inequality {

    public LinearInequalityInOneVariable {
        assert a != 0.0;
        assert v != null;
    }

    public boolean accept(double x) {
        double sum = a * x + b;
        return allowEquals ? sum >= 0 : sum > 0;
    }

    /*
    av + b >= 0 <=> v >= -b/a (with a>0.0) or v <= b/a (a<0.0)
     */
    public Interval interval() {
        double bOverA = b / a;
        if (a > 0.0) {
            return new Interval(-bOverA, allowEquals, Double.POSITIVE_INFINITY, true);
        }
        return new Interval(Double.NEGATIVE_INFINITY, true, bOverA, allowEquals);
    }

    /*
    null = not applicable; true = compatible/there are solutions; false = incompatible/no solutions
     */
    public Boolean accept(List<Expression> expressionsInV) {
        if (onlyNotEquals(expressionsInV)) return true; // v != some constant
        Double vEquals = extractEquals(expressionsInV); // v == some constant
        if (vEquals != null) {
            return accept(vEquals);
        }
        Interval intervalX = Interval.extractInterval(evaluationContext, expressionsInV);
        if (intervalX == null) return null;
        return accept(intervalX);
    }

    public Boolean accept(Interval i) {
        if (i.isPoint()) return accept(i.left());
        if (a > 0.0) {
            if (i.isOpenRight()) return true;
            assert i.isOpenLeft() || i.isClosed();
            // v >= -b/a; x<=right (or left <= x <= right); solution when -b/a <= right
            double diff = -b / a - i.right();
            return allowEquals && i.rightIncluded() ? diff <= 0 : diff < 0;
        }
        if (i.isOpenLeft()) return true;
        assert i.isOpenRight() || i.isClosed();
        // v <= -b/a; left <= x (or left <= x <= right); solution when left <= -b/a
        double diff = -b / a - i.left();
        return allowEquals && i.leftIncluded() ? diff >= 0 : diff > 0;
    }
}
