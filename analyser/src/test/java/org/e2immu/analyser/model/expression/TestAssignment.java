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
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestAssignment {

    @BeforeAll
    public static void beforeClass() {
        Logger.activate();
    }

    private final Primitives primitives = new Primitives();
    private final InspectionProvider inspectionProvider = InspectionProvider.defaultFrom(primitives);

    private LocalVariable makeLocalVariableInt() {
        return new LocalVariable.Builder()
                .setName("i").setSimpleName("i")
                .setParameterizedType(primitives.intParameterizedType)
                .setOwningType(primitives.stringTypeInfo)
                .build();
    }

    @Test
    public void testNormal() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(Identifier.generate(),
                inspectionProvider, lvi, new IntConstant(primitives, 0), false);
        Expression iPlusEquals1 = new Assignment(primitives,
                new VariableExpression(i.localVariableReference), new IntConstant(primitives, 1));
        assertEquals("i=1", iPlusEquals1.minimalOutput());
    }

    @Test
    public void testPlusEqualsOne() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(Identifier.generate(),
                inspectionProvider, lvi, new IntConstant(primitives, 0), false);
        Expression iPlusEquals1 = new Assignment(Identifier.generate(), primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, null, true);
        assertEquals("i+=1", iPlusEquals1.minimalOutput());

        Expression iPlusEquals1AsPlusPlusI = new Assignment(Identifier.generate(), primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, true, true);
        assertEquals("++i", iPlusEquals1AsPlusPlusI.minimalOutput());

        Expression iPlusEquals1AsIPlusPlus = new Assignment(Identifier.generate(), primitives, new VariableExpression(i.localVariableReference),
                new IntConstant(primitives, 1), primitives.assignPlusOperatorInt, false, true);
        assertEquals("i++", iPlusEquals1AsIPlusPlus.minimalOutput());
    }

    @Test
    public void testPlusPlus() {
        LocalVariable lvi = makeLocalVariableInt();
        LocalVariableCreation i = new LocalVariableCreation(Identifier.generate(),
                inspectionProvider, lvi, new IntConstant(primitives, 0), false);

        Expression iPlusPlus = new UnaryOperator(Identifier.generate(), primitives.postfixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.PLUSPLUS);
        assertEquals("i++", iPlusPlus.minimalOutput());

        Expression plusPlusI = new UnaryOperator(Identifier.generate(), primitives.prefixIncrementOperatorInt,
                new VariableExpression(i.localVariableReference), Precedence.UNARY);
        assertEquals("++i", plusPlusI.minimalOutput());
    }
}
