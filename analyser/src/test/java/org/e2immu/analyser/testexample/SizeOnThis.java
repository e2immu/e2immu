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

import org.e2immu.annotation.NotModified;

import java.util.HashSet;
import java.util.Set;

/**
 * the goal of this example is to show that the @Size system can work on `this`,
 * and that is has no knowledge at all on the details of how size is computed.
 *
 */
public class SizeOnThis {

    private final Set<String> strings = new HashSet<>();

    void size$Aspect$Size() {}
    @NotModified
    public int size() {
        return strings.size();
    }

    boolean isEmpty$Value$Size(int size) { return size == 0; }
    @NotModified
    public boolean isEmpty() {
        return strings.isEmpty();
    }

    // annotation is about the object
    boolean clear$Modification$Size(int post, int pre) { return post == 0; }
    @NotModified(absent = true)
    private void clear() {
        strings.clear();
    }

    // this annotation is about the object, not the return value
    boolean add$Modification$Size(int post, int pre) { return pre == 0 ? post == 1: post >= pre && post <= pre+1; }
    @NotModified(absent = true)
    public boolean add(String a) {
        return strings.add(a);
    }

    // this annotation is about the object, not the return value (even though in this particular case,
    // they could be the same.)
    boolean method1$Modification$Size(int post, int pre) { return post >= 1; }
    @NotModified(absent = true)
    public int method1(String s) {
        clear();
        if (isEmpty()) { // ERROR, constant evaluation
            System.out.println("Should always be printed");
        }
        if (strings.isEmpty()) { // not an error, what do we know about strings? we know about `this`
            System.out.println("Should always be printed");
        }
        this.add(s);
        if(isEmpty()) { // ERROR, constant evaluation
            System.out.println("Will never be printed");
        }
        return size();
    }

    boolean method2$Modification$Size(int post, int pre) { return post >= 1; }
    @NotModified(absent = true)
    public void method2() {
        int n = method1("a");
        if(n >= 1) { // ERROR constant evaluation
            System.out.println("Should always be printed");
        }
    }
}
