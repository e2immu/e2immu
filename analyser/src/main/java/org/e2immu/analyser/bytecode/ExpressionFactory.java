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

import org.e2immu.analyser.bytecode.asm.LocalTypeMap;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.Primitives;
import org.objectweb.asm.Type;

public class ExpressionFactory {

    public static Expression from(LocalTypeMap localTypeMap, Identifier identifier, Object value) {
        if (value == null) return new NullConstant(identifier);
        Primitives primitives = localTypeMap.getPrimitives();
        if (value instanceof String s) return new StringConstant(primitives, identifier, s);
        if (value instanceof Integer i) return new IntConstant(primitives, identifier, i);
        if (value instanceof Short s) return new ShortConstant(primitives, identifier, s);
        if (value instanceof Long l) return new LongConstant(primitives, identifier, l);
        if (value instanceof Byte b) return new ByteConstant(primitives, identifier, b);
        if (value instanceof Double d) return new DoubleConstant(primitives, identifier, d);
        if (value instanceof Float f) return new FloatConstant(primitives, identifier, f);
        if (value instanceof Character c) return new CharConstant(primitives, identifier, c);
        if (value instanceof Boolean b) return new BooleanConstant(primitives, identifier, b);
        if (value instanceof Type t) {
            TypeInspection ti = localTypeMap.getOrCreate(t.getClassName(), LocalTypeMap.LoadMode.TRIGGER);
            ParameterizedType parameterizedType = ti.typeInfo().asParameterizedType(localTypeMap);
            return new TypeExpression(identifier, parameterizedType, Diamond.SHOW_ALL);
        }
        throw new UnsupportedOperationException("Value " + value + " is of " + value.getClass());
    }
}
