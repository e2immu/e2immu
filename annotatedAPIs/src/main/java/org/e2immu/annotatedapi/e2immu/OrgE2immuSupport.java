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

package org.e2immu.annotatedapi.e2immu;

import org.e2immu.annotation.*;
import org.e2immu.support.SetOnce;

public class OrgE2immuSupport {

    final static String PACKAGE_NAME = "org.e2immu.support";

    @ERContainer(after = "frozen")
    public static class Freezable$ {

        @Mark("frozen")
        public void freeze() {
        }

        @TestMark("frozen")
        public boolean isFrozen() {
            return false;
        }

        private boolean ensureNotFrozen$Precondition() {
            return !isFrozen();
        }

        @NotModified
        public void ensureNotFrozen() {
        }

        private boolean ensureFrozen$Precondition() {
            return isFrozen();
        }

        @NotModified
        public void ensureFrozen() {
        }
    }

    @E2Container(after = "isFinal")
    interface EventuallyFinal$<T> {

        @Mark("isFinal")
        void setFinal(T value);

        @Only(before = "isFinal")
        void setVariable(T value);
    }

    @E2Container(after = "t")
    interface SetOnce$<T> {

        @Mark("t")
        void set(T t);

        @Only(after = "t")
        @NotNull
        T get();

        @Only(after = "t")
        @NotNull
        T get(String message);

        @Mark("t")
        void copy(SetOnce<T> other);
    }
}
