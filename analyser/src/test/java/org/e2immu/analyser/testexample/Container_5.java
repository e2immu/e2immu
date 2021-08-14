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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@E1Container
public class Container_5 {

    @NotModified(absent = true)
    @Modified
    @Linked(absent = true)
    @NotNull
    private final List<String> list;

    @Independent
    public Container_5() {
        list = new ArrayList<>();
    }

    @Independent
    public Container_5(@NotNull1 Collection<String> coll5) {
        this();
        addAll5(coll5);
    }

    @Modified
    public void add(String string) {
        this.list.add(string);
    }

    @Modified
    public void addAll5(@NotNull1 Collection<String> collection) {
        list.addAll(collection);
    }

    @NotModified
    public void visit(@NotNull1 Consumer<String> consumer) {
        list.forEach(consumer);
    }
}


