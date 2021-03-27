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

package org.e2immu.analyser.model.value.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.GreaterThanZero;
import org.e2immu.analyser.model.expression.Product;
import org.e2immu.analyser.model.expression.Sum;
import org.e2immu.analyser.model.expression.util.LinearInequalityInTwoVariables;
import org.e2immu.analyser.model.expression.util.InequalitySolver;
import org.e2immu.analyser.model.value.CommonAbstractValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestInequalitySolver extends CommonAbstractValue {

    @Test
    public void test1() {
        Expression iGt0 = GreaterThanZero.greater(minimalEvaluationContext, i, newInt(0), false);
        InequalitySolver inequalitySolver = new InequalitySolver(minimalEvaluationContext, iGt0);
        assertEquals("{i=[i>=1]}", inequalitySolver.getPerVariable().toString());
    }

}
