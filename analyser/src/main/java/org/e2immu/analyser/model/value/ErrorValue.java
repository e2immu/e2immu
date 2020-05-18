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

import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Value;

public class ErrorValue implements Value {
    public static ErrorValue divisionByZero(Value alternative) {
        return new ErrorValue("division by zero", alternative);
    }

    public static ErrorValue nullPointerException(Value alternative) {
        return new ErrorValue("null pointer exception", alternative);
    }

    public static ErrorValue potentialNullPointerException(Value alternative) {
        return new ErrorValue("potential null pointer exception", alternative);
    }

    public static ErrorValue unnecessaryMethodCall(Value alternative) {
        return new ErrorValue("Unnecessary method call", alternative);
    }

    public static ErrorValue inlineConditionalEvaluatesToConstant(Value alternative) {
        return new ErrorValue("Inline conditional evaluates to constant", alternative);
    }

    public final String msg;
    public final Value alternative;

    private ErrorValue(String msg, Value alternative) {
        this.msg = msg;
        this.alternative = alternative;
    }

    @Override
    public int compareTo(Value o) {
        if (o == this) return 0;
        return 1; // I'm always at the end
    }

    @Override
    public String toString() {
        return msg;
    }

}
