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

package org.e2immu.analyser.util;

import java.util.Spliterator;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class StreamUtil {

    private StreamUtil(){
        throw new UnsupportedOperationException();
    }

    // https://stackoverflow.com/questions/32495069/how-to-short-circuit-a-reduce-operation-on-a-stream

    public static <T> T reduceWithCancel(Stream<T> s, T acc, BinaryOperator<T> op, Predicate<? super T> cancelPredicate) {
        BoxConsumer<T> box = new BoxConsumer<>();
        Spliterator<T> spliterator = s.spliterator();

        while (!cancelPredicate.test(acc) && spliterator.tryAdvance(box)) {
            acc = op.apply(acc, box.value);
        }

        return acc;
    }

    private static class BoxConsumer<T> implements Consumer<T> {
        T value;
        public void accept(T t) {
            value = t;
        }
    }
}
