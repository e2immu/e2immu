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

package org.e2immu.analyser.parser.basics.testexample;

import java.util.LinkedList;
import java.util.List;

public class Basics_15 {

    private StringBuilder builder;
    private List<String> list;

    public void createStringBuilder(String in) {
        StringBuilder sb = new StringBuilder(in);
        sb.append(" = ");
        sb.append(34);
        builder = sb;
    }

    public void createSet(String s1, String s2) {
        List<String> l1 = new LinkedList<>();
        l1.add(s1);
        l1.add(s2);
        List<String> l2 = l1.subList(0, 1); // explicitly have a link to a local variable
        this.list = l2;
    }

    public StringBuilder getBuilder() {
        return builder;
    }

    public List<String> getList() {
        return list;
    }
}
