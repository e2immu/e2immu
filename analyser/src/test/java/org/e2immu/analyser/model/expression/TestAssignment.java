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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.LocalVariable;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAssignment {

    private final Primitives primitives = new PrimitivesImpl();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private LocalVariable makeLocalVariableInt() {
        return new LocalVariable.Builder()
                .setName("i")
                .setParameterizedType(primitives.intParameterizedType())
                .setOwningType(primitives.stringTypeInfo())
                .build();
    }

    private static Identifier newId() {
        return Identifier.generate("test");
    }

    @Test
    public void testNormal() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, IntConstant.zero(primitives)));
        Expression iPlusEquals1 = new Assignment(primitives,
                new VariableExpression(newId(), i.localVariableReference), IntConstant.one(primitives));
        assertEquals("i=1", iPlusEquals1.minimalOutput());
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(newId(),
                newId(), new LocalVariableReference(lvi, IntConstant.zero(primitives)));
        VariableExpression ve = new VariableExpression(newId(), i.localVariableReference);
        IntConstant one = IntConstant.one(primitives);
        Expression iPlusEquals1 = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), null,
                true, true, null, null);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());

        Expression iPlusEquals1AsPlusPlusI = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), true,
                true, true, null, null);
        assertEquals("++i", iPlusEquals1AsPlusPlusI.minimalOutput());

        Expression iPlusEquals1AsIPlusPlus = new Assignment(newId(), primitives, ve,
                one, primitives.assignPlusOperatorInt(), false,
                true, true, null, null);
        assertEquals("i++", iPlusEquals1AsIPlusPlus.minimalOutput());
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(newId(), newId(),
                new LocalVariableReference(lvi, IntConstant.zero(primitives)));
        VariableExpression ve = new VariableExpression(newId(), i.localVariableReference);
        Expression iPlusPlus = new UnaryOperator(newId(), primitives.postfixIncrementOperatorInt(),
                ve, Precedence.PLUSPLUS);
        assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(newId(), primitives.prefixIncrementOperatorInt(),
                ve, Precedence.UNARY);
        assertEquals("++i", plusPlusI.minimalOutput());
    }
}
