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

package org.e2immu.analyser.parser.eventual.testexample;

import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.Mark;
import org.e2immu.annotation.Only;
import org.e2immu.annotation.TestMark;

import java.util.Objects;

/*
Variant on EventuallyFinal in the support classes
 */
@E2Container(after = "isFinal,value")
public class EventuallyE2Immutable_9<T> {

    private T value;
    private boolean isFinal;

    public T get() {
        return value;
    }

    @Mark("isFinal")
    public void setFinal(T value) {
        if (this.isFinal) {
            throw new IllegalStateException("Trying to overwrite different final value");
        }
        this.isFinal = true;
        this.value = value;
    }

    // IMPORTANT: this method does not have the correct semantics!!
    @Mark("isFinal")
    public void setFinalAllowEquals(T value) {
        if (!Objects.equals(value, this.value)) setFinal(value);
    }

    @Only(before = "isFinal")
    public void setVariable(T value) {
        if (this.isFinal) throw new IllegalStateException("Value is already final");
        this.value = value;
    }

    @TestMark("isFinal")
    public boolean isFinal() {
        return isFinal;
    }
}

