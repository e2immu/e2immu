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
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.annotation.AnnotationType;

public class ExpressionFactory {

    public static Expression from(TypeContext typeContext, Object value) {
        if (value == null) return NullConstant.NULL_CONSTANT;
        if (value instanceof String) return new StringConstant((String) value);
        if (value instanceof Integer) return new IntConstant((Integer) value);
        if (value instanceof Short) return new ShortConstant((Short) value);
        if (value instanceof Long) return new LongConstant((Long) value);
        if (value instanceof Byte) return new ByteConstant((Byte) value);
        if (value instanceof Double) return new DoubleConstant((Double) value);
        if (value instanceof Float) return new FloatConstant((Float) value);
        if (value instanceof Character) return new CharConstant((Character) value);
        if (value instanceof Boolean) return new BooleanConstant((Boolean) value);

        if (value instanceof AnnotationType) {
            // TODO
            return NullConstant.NULL_CONSTANT;
        }
        throw new UnsupportedOperationException("Value " + value + " is of class " + value.getClass());
    }
}
