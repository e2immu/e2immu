package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    private final HiddenContent mine;
    private final HiddenContent theirs;
    private final String label;
    private final CausesOfDelay causesOfDelay;
    private final DV correspondingIndependent;

    public boolean isCommonHC() {
        return HC == value;
    }

    public interface HiddenContent {
        /*
        return null when the link cannot be found
         */
        HiddenContent intersect(HiddenContent theirs);

        boolean isHiddenContentOf(HiddenContent theirs); // level 3 link
    }

    private LV(int value, HiddenContent mine, HiddenContent theirs, String label, CausesOfDelay causesOfDelay,
               DV correspondingIndependent) {
        this.value = value;
        this.mine = mine;
        this.theirs = theirs;
        this.label = Objects.requireNonNull(label);
        this.causesOfDelay = Objects.requireNonNull(causesOfDelay);
        this.correspondingIndependent = correspondingIndependent;
    }

    public static LV initialDelay() {
        return delay(DelayFactory.initialDelay());
    }

    public int value() {
        return value;
    }

    public HiddenContent mine() {
        return mine;
    }

    public HiddenContent theirs() {
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
        return new LV(0, null, null, causes.label(), causes, causes);
    }

    public static LV createHC(HiddenContent mine, HiddenContent theirs) {
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
        @Override
        public String toString() {
            return index.stream().map(Object::toString).collect(Collectors.joining("-"));
        }
    }

    public static HiddenContent wholeType(ParameterizedType parameterizedType) {
        return new HiddenContentImpl(List.of(new IndexedType(parameterizedType, List.of())));
    }

    public static HiddenContent typeParameter(ParameterizedType parameterizedType, int index) {
        return new HiddenContentImpl(List.of(new IndexedType(parameterizedType, List.of(index))));
    }

    public static HiddenContent typeParameters(ParameterizedType pt1, List<Integer> indices1) {
        return new HiddenContentImpl(List.of(new IndexedType(pt1, List.copyOf(indices1))));
    }

    public static HiddenContent typeParameters(ParameterizedType pt1, List<Integer> indices1,
                                               ParameterizedType pt2, List<Integer> indices2) {
        return new HiddenContentImpl(List.of(new IndexedType(pt1, List.copyOf(indices1)),
                new IndexedType(pt2, indices2)));
    }

    public static class HiddenContentImpl implements HiddenContent {
        private final List<IndexedType> sequence;

        private HiddenContentImpl(List<IndexedType> sequence) {
            this.sequence = sequence;
        }

        @Override
        public HiddenContent intersect(HiddenContent theirs) {
            return null;
        }

        @Override
        public boolean isHiddenContentOf(HiddenContent theirs) {
            return false;
        }

        @Override
        public String toString() {
            return sequence.stream().map(IndexedType::toString).collect(Collectors.joining(",",
                    "<", ">"));
        }
    }
}
