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

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;
import org.e2immu.annotation.Variable;

import java.util.Objects;

/*
tiny variant, which causes additional delays
See last line of first constructor.
 */
public class ExternalNotNull_1 {

    @Nullable // upperCaseO has no effect because of the active 'null'
    private final String o;

    @NotNull // upperCaseP has an effect
    private final String p;

    @Variable
    @Nullable
    private String q;

    @Variable
    @NotNull
    private String r;

    @Nullable
    private final String s;

    ExternalNotNull_1(@NotNull String p1,  // local CNN
                      @NotNull String r1) { // local CNN
        o = null;
        p = p1;
        q = null;
        r = Objects.requireNonNull(r1);
        s = null;
        System.out.println(p.toUpperCase()); // THIS LINE CAUSE(S/D) INFINITE DELAYS
    }

    ExternalNotNull_1(@NotNull String p2, // external NN
                      @NotNull String q2, // local CNN
                      @NotNull String r2,
                      @Nullable String s2) {
        o = "hello";
        p = p2;
        q = q2;
        r = Objects.requireNonNull(r2);
        s = s2;
        System.out.println(q2.toLowerCase());
        if (p == null) {
            System.out.println("I believe p is null"); // produce a warning?
        }
    }

    public void setQ(String qs) {
        this.q = qs;
    }

    public void setR(String rs) {
        this.r = Objects.requireNonNull(rs);
    }

    public String upperCaseO() {
        return o.toUpperCase(); // must raise a warning, ENN=1
    }

    public String upperCaseP() {
        return p.toUpperCase(); // p's CNN travels to the 2nd constructor; no warning once ENN=5
    }

    public String upperCaseQ() {
        return q.toUpperCase(); // this must raise a warning, we cannot guarantee NN, ENN=1
    }

    public String upperCaseR() {
        return r.toUpperCase(); // no problems here, r is @NotNull assignment-wise (NNE=5)
    }

    public String getS() {
        return s;
    }
}
