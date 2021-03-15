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

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

/*
Situation: consumer applied to the parameter itself: we assume the worst: the abstract method is modifying.
In this way, we indicate that the abstract method is applied to the parameter rather than a field or a variable
derived from it.

In applyToParameterItself, y is a parameter of abstract type Y.
In applyToParameterMY and applyToParameterNMY, y is not of abstract type, and normal rules apply.
 */
public class AbstractTypeAsParameter_2 {

    @Container
    abstract static class Y {
        private int i;

        public abstract void increment();

        public int getI() {
            return i;
        }

        public void set(int i) {
            this.i = i;
        }
    }

    @NotModified
    public static int applyToParameterItself(int i, @Modified Y y) {
        y.set(i);
        return y.getI();
    }

    static class MY extends Y {

        @Modified
        @Override
        public void increment() {
            set(getI()+1);
        }
    }

    @NotModified
    public static int applyToParameterMY(int i, @Modified MY y) {
        y.increment();
        return y.getI();
    }

    static class NMY extends Y {

        @NotModified
        @Override
        public void increment() {
            System.out.println("i is "+getI());
        }
    }

    @NotModified
    public static int applyToParameterNMY(int i, @NotModified NMY y) {
        y.increment();
        return y.getI();
    }
}
