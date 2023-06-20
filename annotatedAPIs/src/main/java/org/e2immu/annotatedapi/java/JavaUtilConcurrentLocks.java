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

package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;

public class JavaUtilConcurrentLocks {
    final static String PACKAGE_NAME = "java.util.concurrent.locks";

    // none of the method are modified, see initial assumptions: synchronisation is outside the scope
    @ImmutableContainer
    @Independent
    interface Condition$ {

    }

    @ImmutableContainer
    @Independent
    interface Lock$ {

    }

    @ImmutableContainer
    @Independent
    interface ReentrantLock$ {

    }

    @ImmutableContainer
    @Independent
    interface ReadWriteLock$ {

    }

    @ImmutableContainer
    @Independent
    interface ReentrantReadWriteLock$ {

        @ImmutableContainer
        @Independent
        interface ReadLock {

        }

        @ImmutableContainer
        @Independent
        interface WriteLock {

        }
    }
}
