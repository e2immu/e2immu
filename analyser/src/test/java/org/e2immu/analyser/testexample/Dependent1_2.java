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

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Independent1;
import org.e2immu.annotation.Linked1;
import org.e2immu.annotation.Modified;

/*
Third test, from Road to immutability
 */
public class Dependent1_2<T> {

    @Linked1(to = {"Dependent1_2.x", "add:t"})
    private T x;
    @Linked1(to = {"Dependent1_2.y", "add:t"})
    private T y;
    private boolean next;

    public Dependent1_2() {
    }

    @Independent
    public Dependent1_2(@Independent1 Dependent1_2<T> c) {
        x = c.x;
        y = c.y;
        next = c.next;
    }

    /*
    TODO extend fieldAnalysis.linked1Variables from Variable to Expression
    @Independent
    public Dependent1_2(@Dependent2 Dependent1_2<T> c, String msg) {
        x = c.getX();
        y = c.getY();
        next = c.next;
        System.out.println(msg);
    }

     */

    @Modified
    public void add(T t) {
        if (next) {
            this.y = t;
        } else {
            this.x = t;
        }
        next = !next;
    }

    public T getX() {
        return x;
    }

    public T getY() {
        return y;
    }

    public void addWithMessage(T t, String msg) {
        System.out.println(msg);
        add(t);
    }
}
