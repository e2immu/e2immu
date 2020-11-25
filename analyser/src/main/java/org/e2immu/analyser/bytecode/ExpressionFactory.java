/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.bytecode;

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
                    .asParameterizedType(typeContext));
        throw new UnsupportedOperationException("Value " + value + " is of " + value.getClass());
    }
}
