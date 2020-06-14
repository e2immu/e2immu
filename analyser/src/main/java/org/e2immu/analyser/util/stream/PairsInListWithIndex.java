package org.e2immu.analyser.util.stream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PairsInListWithIndex {

    static class PairInList<T> {
        public final int i;
        public final T ti;
        public final int j;
        public final T tj;

        public PairInList(int i, T ti, int j, T tj) {
            this.i = i;
            this.ti = ti;
            this.j = j;
            this.tj = tj;
        }

        @Override
        public String toString() {
            return "(" + i + "," + j + ")=(" + ti + "," + tj + ")";
        }
    }

    /*
    for(int i=0; i<n-1; i++) {
      for(int j=i+1; j<n; j++) {
         ...
      }
    }

    or with iterators, with loops, ...

     */

    static <T> Stream<PairInList<T>> stream(Iterable<T> input) {
        Iterable<PairInList<T>> iterable = () -> new PairsInListWithIndexIterator<>(input);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    static <T> Stream<PairInList<T>> stream(ArrayList<T> input) {
        Iterable<PairInList<T>> iterable = () -> new PairsInListWithIndexIterator<>(input);
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    private static class PairsInListWithIndexIterator<T> implements Iterator<PairInList<T>> {
        private final ArrayList<T> ts;
        private int i;
        private int j;

        PairsInListWithIndexIterator(Iterable<T> iterable) {
            ts = new ArrayList<>();
            for (T t : iterable) ts.add(t);
        }

        PairsInListWithIndexIterator(ArrayList<T> arrayList) {
            this.ts = arrayList;
        }

        @Override
        public boolean hasNext() {
            j++;
            if (j < ts.size()) return true;
            i++;
            j = i + 1;
            return j < ts.size();
        }

        @Override
        public PairInList<T> next() {
            return new PairInList<>(i, ts.get(i), j, ts.get(j));
        }
    }
}
