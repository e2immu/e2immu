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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

/*
similar to setOnce, to detect errors
 */
@E2Container(after = "t")
public class EventuallyE2Immutable_0<T> {

    private T t;

    @Mark("t")
    public void setT(T t) {
        if (t == null) throw new NullPointerException();
        if (this.t != null) throw new UnsupportedOperationException();
        this.t = t;
    }

    @Only(after = "t")
    public T getT() {
        if (t == null) throw new UnsupportedOperationException();
        return t;
    }

    @TestMark("t")
    public boolean isSet() {
        return t != null;
    }

    @TestMark(value = "t", isMark = false)
    public boolean isNotYetSet() {
        return t == null;
    }

    public void copy(EventuallyE2Immutable_0<T> other) {
        if (!other.isSet()) {
            setT(other.getT()); // should cause an error!
        }
    }

    public void copy2(EventuallyE2Immutable_0<T> other) {
        if (other.isNotYetSet()) {
            setT(other.getT()); // should cause an error!
        }
    }

    public void copy3() {
        setT(getT()); // should cause an error
    }
}
