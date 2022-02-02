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

/*
non-constructor version of Singleton_1

 */
@E2Container(after = "created")
public class EventuallyE2Immutable_10 {
    private final int k;
    private boolean created;

    public EventuallyE2Immutable_10(int k) {
        this.k = k;
    }

    @Mark("created")
    public void start() {
        if (created) throw new UnsupportedOperationException();
        created = true;
    }

    public int multiply(int i) {
        return k * i;
    }
}
