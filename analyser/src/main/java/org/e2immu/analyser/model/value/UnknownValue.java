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

public class UnknownValue implements Value {
    public static final UnknownValue UNKNOWN_VALUE = new UnknownValue("<unknown value>");
    public static final UnknownValue NO_VALUE = new UnknownValue("<no value>");
    public static final UnknownValue DIVISION_BY_ZERO = new UnknownValue("division by zero");

    private final String msg;

    private UnknownValue(String msg) {
        this.msg = msg;
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

    @Override
    public Boolean isNotNull(EvaluationContext evaluationContext) {
        return null; // no idea!
    }
}
