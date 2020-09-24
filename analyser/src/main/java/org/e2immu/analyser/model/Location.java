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

package org.e2immu.analyser.model;

import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.Objects;

public class Location {
    public static final Location NO_LOCATION = new Location((WithInspectionAndAnalysis) null, null, 0);

    public final WithInspectionAndAnalysis info;
    public final String statementWithinMethod;
    public final int counter; // in the same statement, there can be multiple identical flows starting...

    public Location(WithInspectionAndAnalysis info) {
        this(Objects.requireNonNull(info), null, 0);
    }

    public Location(WithInspectionAndAnalysis info, int counter) {
        this(Objects.requireNonNull(info), null, counter);
    }

    public Location(MethodInfo methodInfo, String statementIndex) {
        this(Objects.requireNonNull(methodInfo), statementIndex, 0);
    }

    private Location(WithInspectionAndAnalysis info, String statementWithinMethod, int counter) {
        this.info = info;
        this.statementWithinMethod = statementWithinMethod;
        this.counter = counter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(info, location.info) &&
                Objects.equals(statementWithinMethod, location.statementWithinMethod) &&
                counter == location.counter;
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, statementWithinMethod, counter);
    }

    @Override
    public String toString() {
        return info == null ? "<no location>" : ObjectFlow.typeLetter(info) + ":" + info.name()
                + (statementWithinMethod == null ? "" : ":" + statementWithinMethod)
                + (counter == 0 ? "" : "#" + counter);
    }
}
