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

package org.e2immu.analyser.model.value;

import com.google.common.math.DoubleMath;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

public interface NumericValue extends Value {

    static Value intOrDouble(double b, ObjectFlow objectFlow) {
        if (DoubleMath.isMathematicalInteger(b)) {
            long l = Math.round(b);
            if (l > Integer.MAX_VALUE || l < Integer.MIN_VALUE) return new LongValue(l, objectFlow);
            return new IntValue((int) l, objectFlow);
        }
        return new DoubleValue(b, objectFlow);
    }

    NumericValue negate();

    Number getNumber();

    @Override
    default int encodedSizeRestriction() {
        return Level.encodeSizeEquals(getNumber().intValue());
    }

    @Override
    default boolean isNumeric() {
        return true;
    }
}
