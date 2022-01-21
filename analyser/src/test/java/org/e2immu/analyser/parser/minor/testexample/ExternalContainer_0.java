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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

// example that shows the basics of what a @Container contracted restriction can do
// readable in a sort of sequential way.

public class ExternalContainer_0 {

    interface Consumer<T> {
        // allow for modifying accepts
        @Modified
        void accept(T t); // allow for modification of t
    }

    @Container // no methods that modify their parameters
    static class I {
        int i;

        public void setI(int i) {
            this.i = i;
        }

        public int getI() {
            return i;
        }
    }

    @Container(absent = true) // one method that modifies its parameter!
    record MyNonContainer(int value) implements Consumer<I> {

        @Override
        public void accept(@Modified I i) {
            i.setI(value);
        }
    }

    @Container
    static class MyContainer implements Consumer<I> {
        private int value;

        @Override
        public void accept(I i) {
            value = i.getI();
        }

        public int getValue() {
            return value;
        }
    }

    @Container(absent = true)
    private final Consumer<I> myNonContainer = new MyNonContainer(3);
    @Container // computed from the assignment
    private final Consumer<I> myContainer = new MyContainer();
    @Container // computed
    private final Consumer<I> myContainerLinkedToParameter;

    // not contracted but computed: the @Container property, travels from @Container on the field
    public ExternalContainer_0(@Container Consumer<I> consumer) {
        this.myContainerLinkedToParameter = consumer;
    }

    public void go() {
        print(myContainerLinkedToParameter); // causes CONTEXT_CONTAINER to go up, which travels to the field, to the param
        print(myContainer); // does not raise an error
        print(myNonContainer); // raises an error
    }

    // the cause of all complexity: we demand that all implementations be @Container
    private void print(@Container(contract = true) Consumer<I> in) {
        in.accept(i);
        System.out.println(i.getI());
    }

    // we can be guaranteed that the accept method in "print" does not modify i!
    @NotModified
    private final I i = new I();

}
