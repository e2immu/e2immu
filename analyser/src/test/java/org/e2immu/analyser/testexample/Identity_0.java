/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Identity;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Identity_0 {
    /*
    The @NotNull on the idemX methods relies on LOGGER.debug(@NotNull String s) { .. }
     */
    @NotNull
    static final Logger LOGGER = LoggerFactory.getLogger(Identity_0.class);

    @Identity
    @NotModified
    @NotNull
    public static String idem(@NotNull String s) {
        LOGGER.debug(s);
        return s;
    }

}
