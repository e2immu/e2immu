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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.BeforeMark;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Finalizer;
import org.e2immu.support.EventuallyFinal;

@Container
public class Finalizer_2 {

    /*
    a "processor" takes an eventually final object in @BeforeMark state, does things to it,
    and returns it in the @BeforeMark state in the finalizer. This guarantees that all temporary
    data is lost. To ensure that the object stays in @BeforeMark state, the processor cannot
    expose it except for through the finalizer.
     */


    private int count;
    @BeforeMark
    private final EventuallyFinal<String> eventuallyFinal;

    public Finalizer_2(@BeforeMark EventuallyFinal<String> eventuallyFinal) {
        this.eventuallyFinal = eventuallyFinal;
    }

    public void set(String s) {
        eventuallyFinal.setVariable(s);
        count++;
    }

    @Finalizer
    @BeforeMark
    public EventuallyFinal<String> done(String last) {
        eventuallyFinal.setVariable(last + "; tried " + count);
        return eventuallyFinal;
    }
}
