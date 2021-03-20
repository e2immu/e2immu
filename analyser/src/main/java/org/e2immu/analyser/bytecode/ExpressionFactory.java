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

package org.e2immu.analyser.bytecode;

import org.e2immu.analyser.model.Diamond;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.inspector.TypeContext;
import org.objectweb.asm.Type;

public class ExpressionFactory {

    public static Expression from(TypeContext typeContext, Object value) {
        Primitives primitives = typeContext.getPrimitives();
        if (value == null) return NullConstant.NULL_CONSTANT;
        if (value instanceof String s) return new StringConstant(primitives, s);
        if (value instanceof Integer i) return new IntConstant(primitives, i);
        if (value instanceof Short s) return new ShortConstant(primitives, s);
        if (value instanceof Long l) return new LongConstant(primitives, l);
        if (value instanceof Byte b) return new ByteConstant(primitives, b);
        if (value instanceof Double d) return new DoubleConstant(primitives, d);
        if (value instanceof Float f) return new FloatConstant(primitives, f);
        if (value instanceof Character c) return new CharConstant(primitives, c);
        if (value instanceof Boolean b) return new BooleanConstant(primitives, b);
        if (value instanceof Type t)
            return new TypeExpression(typeContext.getFullyQualified(t.getClassName(), true)
                    .asParameterizedType(typeContext), Diamond.SHOW_ALL);
        throw new UnsupportedOperationException("Value " + value + " is of " + value.getClass());
    }
}
