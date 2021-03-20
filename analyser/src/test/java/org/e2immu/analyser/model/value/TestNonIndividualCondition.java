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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNonIndividualCondition extends CommonAbstractValue {

    private Expression rest(Expression value, Filter.FilterMode filterMode) {
        Filter filter = new Filter(minimalEvaluationContext, filterMode);
        return filter.filter(value, filter.individualNullOrNotNullClauseOnParameter()).rest();
    }

    @Test
    public void test1() {
        Expression sEqualsNull = equals(NullConstant.NULL_CONSTANT, s);
        assertEquals("null==s", sEqualsNull.toString());
        assertEquals("null==s", rest(sEqualsNull, Filter.FilterMode.ACCEPT).toString());

        Expression pEqualsNull = equals(NullConstant.NULL_CONSTANT, p);
        assertEquals("null==p", pEqualsNull.minimalOutput());
        assertTrue(rest(pEqualsNull, Filter.FilterMode.ACCEPT).isBoolValueTrue());
        assertTrue(rest(negate(pEqualsNull), Filter.FilterMode.ACCEPT).isBoolValueTrue());

        Expression orValue = newOrAppend(sEqualsNull, pEqualsNull);
        assertEquals("null==s", rest(orValue, Filter.FilterMode.REJECT).toString());

        Expression orValue2 = newOrAppend(sEqualsNull, pEqualsNull);
        assertEquals("null==s||null==p", rest(orValue2, Filter.FilterMode.ACCEPT).minimalOutput());

        Expression andValue = newAndAppend(sEqualsNull, pEqualsNull);
        assertEquals("null==s&&null==p", rest(andValue, Filter.FilterMode.REJECT).toString());

        Expression andValue2 = newAndAppend(sEqualsNull, pEqualsNull);
        assertEquals("null==s", rest(andValue2, Filter.FilterMode.ACCEPT).toString());

        Expression notAndValue = negate(andValue);
        assertEquals("null!=s||null!=p", notAndValue.toString());
        assertEquals("null!=s", rest(notAndValue, Filter.FilterMode.REJECT).toString());
    }
}
