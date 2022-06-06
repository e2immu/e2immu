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
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.Equals;
import org.e2immu.analyser.model.expression.MethodCall;

import java.util.List;

public record LhsRhs(Expression lhs, Expression rhs) {

    public static List<LhsRhs> extractEqualities(Expression e) {
        if (e instanceof Equals equals) {
            return List.of(new LhsRhs(equals.lhs, equals.rhs));
        }
        LhsRhs equalsMethod = equalsMethodCall(e);
        if (equalsMethod != null) return List.of(equalsMethod);
        if (e instanceof And and) {
            return and.getExpressions().stream().flatMap(clause -> extractEqualities(clause).stream()).toList();
        }
        return List.of();
    }

    public static LhsRhs equalsMethodCall(Expression e) {
        if (e instanceof MethodCall mc && mc.methodInfo.name.equals("equals") && mc.parameterExpressions.size() == 1) {
            Expression rhs = mc.parameterExpressions.get(0);
            if (rhs.isConstant()) return new LhsRhs(rhs, mc.object);
            return new LhsRhs(mc.object, rhs);
        }
        return null;
    }
}
