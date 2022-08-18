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

import org.e2immu.annotation.*;
import org.e2immu.annotation.eventual.Mark;
import org.e2immu.annotation.eventual.Only;
import org.e2immu.support.EventuallyFinal;

// complication of _12, precursor to Finalizer_1
// test whether the guarding of variables (making use of the constraints on eventuallyFinal to restrict the
// use of "count") works

@ImmutableContainer(after = "eventuallyFinal")
public class EventuallyImmutableUtil_13 {

    @Final(after = "eventuallyFinal")
    private int count;
    private final EventuallyFinal<String> eventuallyFinal = new EventuallyFinal<>();

    @Modified
    @Only(before = "eventuallyFinal")
    public void set(String s) {
        eventuallyFinal.setVariable(s);
        count++;
    }

    @ImmutableContainer
    @Mark("eventuallyFinal")
    public EventuallyFinal<String> done(String last) {
        eventuallyFinal.setFinal(last + "; tried " + count);
        return eventuallyFinal;
    }

    public String get() {
        return eventuallyFinal.get();
    }

    public int getCount() {
        return count;
    }
}

