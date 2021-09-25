/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.annotatedapi;

import org.e2immu.annotation.*;

public class JavaIo {
    final static String PACKAGE_NAME = "java.io";


    @E2Container
    @Independent
    interface Serializable$ {

    }

    @Container
    interface PrintStream$ {
        @Modified
        @AllowsInterrupt
        void print(char c);

        @Modified
        @AllowsInterrupt
        void print(boolean b);

        @Modified
        @AllowsInterrupt
        void print(int i);

        @Modified
        @AllowsInterrupt
        void print(float f);

        @Modified
        @AllowsInterrupt
        void print(long l);

        @Modified
        @AllowsInterrupt
        void print(String s);

        @Modified
        @AllowsInterrupt
        void print(@NotModified Object obj);

        @Modified
        @AllowsInterrupt
        void println();

        @Modified
        @AllowsInterrupt
        void println(char c);

        @Modified
        @AllowsInterrupt
        void println(boolean b);

        @Modified
        @AllowsInterrupt
        void println(int i);

        @Modified
        @AllowsInterrupt
        void println(float f);

        @Modified
        @AllowsInterrupt
        void println(long l);

        @Modified
        @AllowsInterrupt
        void println(String s);

        @Modified
        @AllowsInterrupt
        void println(@NotModified Object obj);
    }

}
