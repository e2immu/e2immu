/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.util;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

import java.util.Objects;

@E2Container(after = "isFinal")
public class EventuallyFinal<T> {
    private T value;
    private boolean isFinal;

    public T get() {
        return value;
    }

    @Mark("isFinal")
    public void setFinal(T value) {
        if (this.isFinal && !Objects.equals(value, this.value)) {
            throw new UnsupportedOperationException("Trying to overwrite different final value");
        }
        this.isFinal = true;
        this.value = value;
    }

    @Only(before = "isFinal")
    public void setVariable(T value) {
        if (this.isFinal) throw new UnsupportedOperationException("Value is already final");
        this.value = value;
    }

    @TestMark("isFinal")
    public boolean isFinal() {
        return isFinal;
    }

    @TestMark(value = "isFinal", before = true)
    public boolean isVariable() {
        return !isFinal;
    }
}
