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

package org.e2immu.analyser.parser.minor.testexample;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Nullable;

import java.util.Objects;

public class ExternalNotNull_0 {

    @Nullable // upperCaseO has no effect because of the active 'null'
    private final String o;

    @NotNull // upperCaseP has an effect
    private final String p;

    // produces warning: initialization conflict: @NotNull on q2, and null in the first constructor
    @Final(absent = true)
    @Nullable
    private String q;

    @Final(absent = true)
    @NotNull
    private String r;

    @Nullable
    private final String s;

    ExternalNotNull_0(@NotNull String p1,
                      @NotNull String r1) {
        o = null;
        p = p1;
        q = null;
        r = Objects.requireNonNull(r1);
        s = null;
    }

    ExternalNotNull_0(@NotNull String p2,
                      @NotNull String q2,
                      @NotNull String r2,
                      @Nullable String s2) {
        o = "hello";
        p = p2;
        q = q2;
        r = Objects.requireNonNull(r2);
        s = s2;
        System.out.println(q2.toLowerCase());
        if (p == null) {
            System.out.println("I believe p is null"); // produce a warning!
        }
        if (p2 == null) {
            System.out.println("I believe p2 is null"); // produce a warning!
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
