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

package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.*;
import org.e2immu.support.EventuallyFinal;

@E2Immutable(recursive = true, after = "eventuallyFinal")
public class Finalizer_1 {

    /*
    the goal is to have a class that "finishes" a certain eventually immutable object.
    It receives it in the @BeforeMark state, and returns it finally in the final state.
    In the meantime, it gets modified (finished), while there is other temporary data around.
    Once the final state is reached, we try to guarantee that the temporary data is destroyed
    by severely limiting the scope of the finisher object.

    The @BeforeMark indicates that the field will be in the "before" state until the very last
    moment, i.e., until a call to "done", which finalizes the object.
     */

    private int count;
    @BeforeMark
    private final EventuallyFinal<String> eventuallyFinal;

    public Finalizer_1(@BeforeMark(contract = true) EventuallyFinal<String> eventuallyFinal) {
        this.eventuallyFinal = eventuallyFinal;
    }

    @Modified
    @Only(before = "eventuallyFinal")
    public void set(String s) {
        eventuallyFinal.setVariable(s);
        count++;
    }

    @Finalizer
    @ERContainer
    @Mark("eventuallyFinal")
    public EventuallyFinal<String> done(String last) {
        eventuallyFinal.setFinal(last + "; tried " + count);
        return eventuallyFinal;
    }
}

