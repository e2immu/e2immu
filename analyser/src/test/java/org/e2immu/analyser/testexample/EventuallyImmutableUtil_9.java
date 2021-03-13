/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.testexample;

import org.e2immu.analyser.util.Freezable;
import org.e2immu.annotation.E2Container;
import org.e2immu.annotation.TestMark;

/*
This one tests inheritance from Freezable
 */
@E2Container(after = "frozen")
public class EventuallyImmutableUtil_9 extends Freezable {

    @TestMark("frozen")
    public boolean isReady() {
        return isFrozen();
    }
}