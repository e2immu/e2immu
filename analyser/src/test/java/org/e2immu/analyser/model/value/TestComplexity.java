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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestComplexity extends CommonAbstractValue {

    @Test
    public void testSimple1() {
        // a
        assertEquals(2, a.getComplexity());

        // !a
        assertEquals(3, negate(a).getComplexity());

        // !a && b
        assertEquals(6, newAndAppend(b, negate(a)).getComplexity()); // &&=1, a=2, b=2, !=1

        // i >= 0
        assertEquals(2, GreaterThanZero.greater(context, i, newInt(0), true).getComplexity());

        // i > 0  === i + -1 >= 0 (2 for 1, 1 for +, 1 for -1)
        assertEquals(4, GreaterThanZero.greater(context, i, newInt(0), false).getComplexity());

        // i <= 0
        assertEquals(5, GreaterThanZero.less(context, i, newInt(0), true).getComplexity());

        // i < 0  === -1 - i >= 0 (1 for -1, 1 for +, 1 for unary -, 2 for i)
        Expression iLt0 = GreaterThanZero.less(context, i, newInt(0), false);
        assertEquals(3, iLt0.getComplexity());

        // s == null
        assertEquals(3, equals(s, NullConstant.NULL_CONSTANT).getComplexity());

        // s != null
        assertEquals(4, negate(equals(s, NullConstant.NULL_CONSTANT)).getComplexity());

        // i + j
        assertEquals(5, sum(i, j).getComplexity());

        // i + 1
        assertEquals(4, sum(i, newInt(1)).getComplexity());

        // i - 1
        assertEquals(4, sum(i, newInt(-1)).getComplexity());

        // i + 2
        assertEquals(5, sum(i, newInt(2)).getComplexity());
    }

    /*
    (<m:interfacesImplemented>.isEmpty()||null==<m:mapInTermsOfParametersOfSubType>)&&(<m:interfacesImplemented>.isEmpty()
    ||<f:typeInfo>!=<f:typeInfo>)?null:<m:interfacesImplemented>.isEmpty()?
    null==<m:parentClass>?<return value>:null==<m:mapInTermsOfParametersOfSubType>?<f:typeInfo>==<f:typeInfo>?
    <m:forwardTypeParameterMap>:<return value>:<m:combineMaps>:null==<m:mapInTermsOfParametersOfSubType>
    ?<f:typeInfo>==<f:typeInfo>?<m:forwardTypeParameterMap>:<return value>:<m:combineMaps>

    a = <m:interfacesImplemented>.isEmpty()   BOOLEAN    a
    b = <m:mapInTermsOfParametersOfSubType>   obj        s
    c = <f:typeInfo>_1                        var        s1
    d = <f:typeInfo>_2                        var        s2
    e = <m:parentClass>                       obj        s3
    f = <return value>                                   s4
    g = <m:forwardTypeParameterMap>                      s5
    h = <m:combineMaps>                                  s6

    (a||null==b)&&(a||c!=d) ? null :
           a ?
              null==e ? f :
                  null==b ?
                      c == d ? g : f
                  : h
             :
             (null==b ? (c == d ? g : f) : h);
     */
    @Test
    public void test() {
        Expression condition1 = newAnd(newOr(a, newEquals(NullConstant.NULL_CONSTANT, s)),
                newOr(a, negate(newEquals(s1, s2))));
        assertEquals("(a||null==s)&&(a||s1!=s2)", condition1.toString());
        // 21 = (1+4+1+2+1)+3+(9)
        assertEquals(17, condition1.getComplexity());

        Expression cEqdGThenF = newInline(newEquals(s1, s2), s5, s4);
        assertEquals("s1==s2?s5:s4", cEqdGThenF.toString());

        Expression expression4 = newInline(newEquals(NullConstant.NULL_CONSTANT, s), cEqdGThenF, s6);
        assertEquals("null==s?s1==s2?s5:s4:s6", expression4.toString());

        Expression expression2 = newInline(newEquals(NullConstant.NULL_CONSTANT, s3), s4, expression4);
        assertEquals("null==s3?s4:null==s?s1==s2?s5:s4:s6", expression2.toString());

        Expression expression3 = newInline(newEquals(NullConstant.NULL_CONSTANT, s), cEqdGThenF, s6);
        assertEquals("null==s?s1==s2?s5:s4:s6", expression3.toString());

        Expression expression1 = newInline(a, expression2, expression3);
        InlineConditional overall = newInline(condition1, NullConstant.NULL_CONSTANT, expression1);
        assertEquals("(a||null==s)&&(a||s1!=s2)?null:a?null==s3?s4:null==s?s1==s2?s5:s4:s6:null==s?s1==s2?s5:s4:s6",
                overall.toString());
        assertEquals(121, overall.getComplexity());

        Expression better = overall.optimise(context, null);
        assertEquals("(a||null==s)&&(a||s1!=s2)?null:null==s?s5:s6", better.toString());

        Assignment assignment = new Assignment(PRIMITIVES, s1, overall);
        EvaluationResult res = assignment.evaluate(context, ForwardEvaluationInfo.DEFAULT);
        assertEquals("(a||null==s)&&(a||s1!=s2)?null:null==s?s5:s6", res.value().toString());
    }
}
