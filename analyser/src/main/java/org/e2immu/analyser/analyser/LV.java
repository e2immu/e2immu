package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LV implements Comparable<LV> {
    private static final int HC = 4;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, null, null,
            "statically_assigned", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_ASSIGNED = new LV(1, null, null,
            "assigned", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_DEPENDENT = new LV(2, null, null,
            "dependent", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);

    // do not use for equality! Use LV.isCommonHC()
    public static final LV LINK_COMMON_HC = new LV(HC, null, null,
            "common_hc", CausesOfDelay.EMPTY, MultiLevel.INDEPENDENT_HC_DV);
    public static final LV LINK_INDEPENDENT = new LV(5, null, null,
            "independent", CausesOfDelay.EMPTY, MultiLevel.INDEPENDENT_DV);

    private final int value;
    private final HiddenContentSelector mine;
    private final HiddenContentSelector theirs;
    private final String label;
    private final CausesOfDelay causesOfDelay;
    private final DV correspondingIndependent;

    public boolean isCommonHC() {
        return HC == value;
    }

    public interface HiddenContent {
        HiddenContentSelector all();
    }

    private LV(int value, HiddenContentSelector mine, HiddenContentSelector theirs,
               String label, CausesOfDelay causesOfDelay, DV correspondingIndependent) {
        this.value = value;
        this.mine = mine;
        this.theirs = theirs;
        this.label = Objects.requireNonNull(label);
        assert !label.isBlank();
        this.causesOfDelay = Objects.requireNonNull(causesOfDelay);
        this.correspondingIndependent = correspondingIndependent;
    }

    public static LV initialDelay() {
        return delay(DelayFactory.initialDelay());
    }

    public int value() {
        return value;
    }

    public HiddenContentSelector mine() {
        return mine;
    }

    public HiddenContentSelector theirs() {
        return theirs;
    }

    public String label() {
        return label;
    }

    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    public static LV delay(CausesOfDelay causes) {
        assert causes.isDelayed();
        return new LV(-1, null, null, causes.label(), causes, causes);
    }

    public static LV createHC(HiddenContentSelector mine, HiddenContentSelector theirs) {
        return new LV(HC, mine, theirs, mine + "-4-" + theirs, CausesOfDelay.EMPTY, MultiLevel.INDEPENDENT_HC_DV);
    }

    public LV reverse() {
        if (isCommonHC()) {
            return createHC(theirs, mine);
        }
        return this;
    }

    public boolean isDelayed() {
        return causesOfDelay.isDelayed();
    }

    public boolean le(LV other) {
        return value <= other.value;
    }

    public boolean lt(LV other) {
        return value < other.value;
    }

    public boolean ge(LV other) {
        return value >= other.value;
    }

    public LV min(LV other) {
        if (isDelayed()) {
            if (other.isDelayed()) {
                return delay(causesOfDelay.merge(other.causesOfDelay));
            }
            return this;
        }
        if (other.isDelayed()) return other;
        if (value > other.value) return other;
        if (isCommonHC() && other.isCommonHC()) {
            HiddenContentSelector mineUnion = mine.union(other.mine);
            HiddenContentSelector theirsUnion = theirs.union(other.theirs);
            return createHC(mineUnion, theirsUnion);
        }
        return this;
    }

    private boolean mineEqualsTheirs(LV other) {
        return Objects.equals(mine, other.mine) && Objects.equals(theirs, other.theirs);
    }

    public LV max(LV other) {
        if (isDelayed()) {
            if (other.isDelayed()) {
                return delay(causesOfDelay.merge(other.causesOfDelay));
            }
            return this;
        }
        if (other.isDelayed()) return other;
        if (value < other.value) return other;
        assert value != HC || other.value != HC || mineEqualsTheirs(other);
        return this;
    }

    public boolean isDone() {
        return causesOfDelay.isDone();
    }

    @Override
    public int compareTo(LV o) {
        return value - o.value;
    }

    public DV toIndependent() {
        return correspondingIndependent;
    }

    public boolean isInitialDelay() {
        return causesOfDelay().isInitialDelay();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LV lv = (LV) o;
        if (value != lv.value) return false;
        if (value == HC) {
            return Objects.equals(mine, lv.mine) && Objects.equals(theirs, lv.theirs) && Objects.equals(causesOfDelay, lv.causesOfDelay);
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, mine, theirs, causesOfDelay);
    }

    @Override
    public String toString() {
        return label;
    }

    public record IndexedType(ParameterizedType parameterizedType, List<Integer> index) {
        private static int realIndex(int i) {
            return i >= 0 ? i : -i - 1;
        }

        @Override
        public String toString() {
            return index.stream().map(i -> i < 0 ? "*" + realIndex(i) : "" + i)
                    .collect(Collectors.joining("-"));
        }

        public Stream<Integer> typeParameterIndexStream() {
            if (index.isEmpty()) return Stream.of();
            int i = index.get(index.size() - 1);
            return i >= 0 ? Stream.of(i) : Stream.of();
        }

        public ParameterizedType concreteOf(ParameterizedType input) {
            ParameterizedType pt = input;
            for (int i : index) {
                if (i >= 0) return pt.parameters.get(i);
                pt = pt.parameters.get(realIndex(i));
            }
            return pt;
        }
    }

    public static class HiddenContentImpl implements HiddenContent {
        private final List<IndexedType> sequence;
        private final ParameterizedType wholeType;
        private final Integer wholeTypeIndex;

        private HiddenContentImpl(List<IndexedType> sequence) {
            assert !sequence.isEmpty();
            this.sequence = sequence;
            wholeType = null;
            wholeTypeIndex = null;
        }

        private HiddenContentImpl(ParameterizedType wholeType, Integer wholeTypeIndex) {
            this.wholeTypeIndex = wholeTypeIndex;
            this.wholeType = wholeType;
            this.sequence = null;
        }

        @Override
        public HiddenContentSelector all() {
            if (sequence != null) {
                Set<Integer> set = sequence.stream().flatMap(IndexedType::typeParameterIndexStream)
                        .collect(Collectors.toUnmodifiableSet());
                if (set.isEmpty()) {
                    return HiddenContentSelector.None.INSTANCE;
                }
                return new HiddenContentSelector.CsSet(set);
            }
            assert wholeTypeIndex != null;
            return HiddenContentSelector.All.INSTANCE;
        }

        @Override
        public String toString() {
            return sequence.stream().map(IndexedType::toString).collect(Collectors.joining(",",
                    "<", ">"));
        }
    }

    /*
    Create a HiddenContent object for a variable's type.
     */

    public static HiddenContent from(ParameterizedType pt) {
        if (pt.isTypeParameter()) {
            if (pt.arrays > 0) {
                // arrays == 1 ~ Array<T> -> <0>
                // arrays == 2 ~ Array<Array<T>> -> <*0-0>
                List<Integer> list = new ArrayList<>();
                for (int i = 0; i < pt.arrays - 1; i++) list.add(-1);
                list.add(0);
                return new HiddenContentImpl(List.of(new IndexedType(pt.copyWithoutArrays(), list)));
            }
            return new HiddenContentImpl(pt, pt.typeParameter.getIndex());
        }
        AtomicInteger counter = new AtomicInteger();
        Map<ParameterizedType, Integer> typeParameterIndex = new HashMap<>();
        return from(pt, typeParameterIndex, counter);
    }


    private static HiddenContent from(ParameterizedType pt,
                                      Map<ParameterizedType, Integer> typeParameterIndex,
                                      AtomicInteger counter) {
        List<IndexedType> sequence = new ArrayList<>(pt.parameters.size());
        int countParameter = 1;
        for (ParameterizedType tp : pt.parameters) {
            if (tp.isTypeParameter()) {
                Integer index = typeParameterIndex.get(tp);
                if (index == null) {
                    int count = counter.getAndIncrement();
                    typeParameterIndex.put(tp, count);
                    index = count;
                }
                sequence.add(new IndexedType(tp, List.of(index)));
            } else if (!tp.parameters.isEmpty()) {
                HiddenContentImpl recursively = (HiddenContentImpl) from(tp, typeParameterIndex, counter);
                if (recursively != null) {
                    for (IndexedType it : recursively.sequence) {
                        List<Integer> indices = Stream.concat(Stream.of(-countParameter),
                                it.index.stream()).toList();
                        sequence.add(new IndexedType(it.parameterizedType, indices));
                    }
                }
            } else {
                sequence.add(new IndexedType(tp, List.of(-countParameter)));
            }
            countParameter++;
        }
        if (sequence.isEmpty()) {
            return null;
        }
        return new HiddenContentImpl(List.copyOf(sequence));
    }

    public static Map<Integer, ParameterizedType> typesCorrespondingToHC(ParameterizedType pt) {
        if (pt.isUnboundTypeParameter()) return null;
        HiddenContentImpl hiddenContent = (HiddenContentImpl) from(pt);
        if (hiddenContent == null) return null;
        // the selector tells us where to find types
        Map<Integer, ParameterizedType> map = new HashMap<>();
        for (IndexedType it : hiddenContent.sequence) {
            int index = it.index.get(it.index.size() - 1);
            if (index >= 0 && !map.containsKey(index)) {
                ParameterizedType tp = it.concreteOf(pt);
                map.put(index, tp);
            }
        }
        return Map.copyOf(map);
    }

}
