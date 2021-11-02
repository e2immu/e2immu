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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.util.StringUtil;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Stream;

public class AssignmentIds implements Comparable<AssignmentIds> {
    public static AssignmentIds NOT_YET_ASSIGNED = new AssignmentIds();

    private final TreeSet<String> ids;

    private AssignmentIds() {
        this.ids = new TreeSet<>();
    }

    public AssignmentIds(String id) {
        this.ids = new TreeSet<>();
        ids.add(id);
    }

    public AssignmentIds(String assignmentId, Stream<AssignmentIds> stream) {
        this.ids = new TreeSet<>();
        this.ids.add(assignmentId);
        stream.forEach(a -> ids.addAll(a.ids));
    }

    public boolean hasNotYetBeenAssigned() {
        return ids.isEmpty();
    }

    public String getLatestAssignment() {
        return ids.isEmpty() ? "-" : ids.floor("~");
    }

    @Override
    public int compareTo(AssignmentIds o) {
        return getLatestAssignment().compareTo(o.getLatestAssignment());
    }

    public String getLatestAssignmentIndex() {
        return ids.isEmpty() ? "-" : StringUtil.stripLevel(getLatestAssignment());
    }

    @Override
    public String toString() {
        return String.join(",", ids);
    }

    public String getEarliestAssignmentIndex() {
        return ids.isEmpty() ? "-" : ids.ceiling("-");
    }

    public Iterator<String> idStream() {
        return ids.descendingIterator();
    }
}
