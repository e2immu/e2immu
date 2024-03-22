package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class LV implements Comparable<LV> {
    private static final int HC = 4;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, null, null,
            "statically_assigned", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_ASSIGNED = new LV(1, null, null,
            "assigned", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);
    public static final LV LINK_DEPENDENT = new LV(2, null, null,
            "dependent", CausesOfDelay.EMPTY, MultiLevel.DEPENDENT_DV);

    // do not use for equality!
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

    public interface HiddenContentSelector extends DijkstraShortestPath.Connection {
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
        assert value == HC;
        return createHC(theirs, mine);
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
        if (value > other.value) return other;
        assert value != HC || other.value != HC || mineEqualsTheirs(other);
        return this;
    }

    private boolean mineEqualsTheirs(LV other) {
        return Objects.equals(mine, other.mine) && Objects.equals(theirs, other.theirs);
    }

    public LV max(LV other) {
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
    }

    // integers represent type parameters, as result of HC.typeParameters()
    public static class HiddenContentSelectorImpl implements HiddenContentSelector {
        private final Set<Integer> set;

        private HiddenContentSelectorImpl() {
            set = Set.of(-1);
        }

        public HiddenContentSelectorImpl(Set<Integer> set) {
            assert set != null && set.stream().allMatch(i -> i >= 0);
            this.set = Set.copyOf(set);
        }

        @Override
        public boolean doesNotContain(DijkstraShortestPath.Connection required) {
            boolean containsRequired = this == CS_ALL || set.containsAll(((HiddenContentSelectorImpl) required).set);
            return !containsRequired;
        }

        @Override
        public String toString() {
            return this == CS_ALL ? "*" : set.stream().sorted().map(Object::toString)
                    .collect(Collectors.joining(",", "<", ">"));
        }

        public Set<Integer> set() {
            return set;
        }
    }

    public static HiddenContentSelector selectTypeParameter(int i) {
        return new HiddenContentSelectorImpl(Set.of(i));
    }

    public static HiddenContentSelector selectTypeParameters(int... is) {
        return new HiddenContentSelectorImpl(Arrays.stream(is).boxed().collect(Collectors.toUnmodifiableSet()));
    }

    public static final HiddenContentSelector CS_ALL = new HiddenContentSelectorImpl();

    public static class HiddenContentImpl implements HiddenContent {
        private final List<IndexedType> sequence;

        private HiddenContentImpl(List<IndexedType> sequence) {
            this.sequence = sequence;
        }

        @Override
        public HiddenContentSelector all() {
            Set<Integer> set = sequence.stream().flatMap(IndexedType::typeParameterIndexStream)
                    .collect(Collectors.toUnmodifiableSet());
            return new HiddenContentSelectorImpl(set);
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
                for (IndexedType it : recursively.sequence) {
                    List<Integer> indices = Stream.concat(Stream.of(-countParameter),
                            it.index.stream()).toList();
                    sequence.add(new IndexedType(it.parameterizedType, indices));
                }
            } else {
                sequence.add(new IndexedType(tp, List.of(-countParameter)));
            }
            countParameter++;
        }
        return new HiddenContentImpl(List.copyOf(sequence));
    }

}
