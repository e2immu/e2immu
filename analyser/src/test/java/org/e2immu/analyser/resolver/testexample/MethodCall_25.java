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

package org.e2immu.analyser.resolver.testexample;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class MethodCall_25 {

    interface Element {

        default void visit(Consumer<Element> consumer) {
            subElements().forEach(element -> element.visit(consumer));
            consumer.accept(this);
        }

        default void visit(Predicate<Element> predicate) {
            if (predicate.test(this)) {
                subElements().forEach(element -> element.visit(predicate));
            }
        }

        List<Element> subElements();
    }

    public void method(Element e) {
        e.visit(element -> {
            System.out.println("?");
            return true;
        });
    }

    /* Compilation error:
    public void method2(Element e) {
        e.visit(element -> System.out.println("?"));
    }
    */

    public void method2(Element e) {
        e.visit(element -> {
            System.out.println("?");
        });
    }

    public void method3(Element e) {
        e.visit(element -> {
            System.out.println("?");
            if (element == null) {
                return false;
            } else {
                return true;
            }
        });
    }

    public void method4(Element e) {
        e.visit(element -> {
            try {
                System.out.println("Hello");
                return true;
            } finally {
                System.out.println("?");
            }
        });
    }

    /*
    Compilation error:
    public void method5(Element e, List<String> list) {
        e.visit(element -> {
            for (String s : list) {
               return true;
            }
        });
    }
     */

    /*
    Compilation error: (cannot distinguish!)
    public void method5(Element e) {
        e.visit(element -> {
            throw new UnsupportedOperationException("?" + element);
        });
    }*/
}
