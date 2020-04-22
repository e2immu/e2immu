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

package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.NotModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import static org.e2immu.annotation.AnnotationType.VERIFY_ABSENT;

public class NotModifiedChecks2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotModifiedChecks2.class);

    @NotModified(type = VERIFY_ABSENT) // modified by add method
    final Set<String> s2 = new HashSet<>();

    @NotModified(type = VERIFY_ABSENT) // modifies s2
    public int add(String s) {
        Set<String> theSet = s2; // linked to s2, which is linked to set2
        LOGGER.debug("The set has {} elements before adding {}", theSet.size(), s);
        theSet.add(s); // this one modifies set2!
        return 1;
    }

}
