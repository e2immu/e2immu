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
import org.e2immu.annotation.Immutable;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.rare.IgnoreModifications;

import java.util.function.Consumer;

// example of _0, but without the contracted @Container

public class ExternalContainer_2 {

    @Container
    static class I {
        private int i;

        @Modified
        public void setI(int i) {
            this.i = i;
        }

        @NotModified
        public int getI() {
            return i;
        }
    }

    @Immutable
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
        @Modified
        public void accept(I i) {
            value = i.getI();
        }

        public int getValue() {
            return value;
        }
    }

    @Container(absent = true)
    private final Consumer<I> myNonContainer = new MyNonContainer(3);
    @Container
    private final Consumer<I> myContainer = new MyContainer();
    @Container(absent = true)
    private final Consumer<I> myContainerLinkedToParameter;

    public ExternalContainer_2(@Container(absent = true) Consumer<I> consumer) {
        this.myContainerLinkedToParameter = consumer;
    }

    @Modified
    public void go() {
        if (myContainerLinkedToParameter != null) {
            print(myContainerLinkedToParameter);
        }
        print(myContainer);
        print(myNonContainer);
    }

    // it is not because modifications on "in" are ignored, that the actual modification on iField is ignored!
    @Modified
    private void print(@IgnoreModifications Consumer<I> in) {
        in.accept(iField);
        System.out.println(iField.getI());
    }

    @Container(implied = true)
    @Modified
    private final I iField = new I();

}
