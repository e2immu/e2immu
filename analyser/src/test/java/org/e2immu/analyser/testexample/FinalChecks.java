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

import org.e2immu.annotation.*;

/*
 ERROR in M:FinalChecks:0: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in M:FinalChecks:1: Condition in 'if' or 'switch' statement evaluates to constant
 ERROR in F:s5: Private field not read outside constructors
 */

@E1Immutable(absent = true)
@Container
public class FinalChecks {

    @Final
    private String s3 = "abc";

    private final String s1;

    @Final
    private String s2;

    // because this one is NOT final, the type is not @E1Immutable
    @Final(absent = true)
    private String s4;

    @Final
    private String s5;

    FinalChecks(String s1, String s2) {
        if (s5 == null) {
            s5 = "abc";
        } else {
            // ensure this statement is ignored!!
            s5 = null;
        }
        if (s5 == null) {
            // ensure that this statement is ignored!
            throw new UnsupportedOperationException();
        }
        this.s2 = s2;
        this.s1 = s1 + s3;
    }

    FinalChecks(String s1) {
        this.s1 = s1;
    }

    @Override
    @NotNull
    @NotModified
    public String toString() {
        return s1 + " " + s2 + " " + s3 + " " + s4;
    }

    @Modified
    public void setS4(String s4) {
        this.s4 = s4;
    }
}
