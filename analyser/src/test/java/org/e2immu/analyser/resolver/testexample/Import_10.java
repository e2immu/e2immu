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

package org.e2immu.analyser.resolver.testexample;

// IMPORTANT: keep this import static...* statement!
import org.e2immu.analyser.resolver.testexample.importhelper.MultiLevel;

import java.util.Set;

import static org.e2immu.analyser.resolver.testexample.importhelper.MultiLevel.Effective.*;

public class Import_10 {

    record ChangeData(Set<Integer> statementTimes) {

    }

    public void method1(int statementTime) {
        ChangeData changeData = new ChangeData(Set.of(statementTime));
    }

    // completely irrelevant but here we use the enum constants
    public Boolean method2(MultiLevel.Effective effective) {
        if(effective == E1) {
            return true;
        }
        if(effective == E2) {
            return false;
        }
        return null;
    }
}
