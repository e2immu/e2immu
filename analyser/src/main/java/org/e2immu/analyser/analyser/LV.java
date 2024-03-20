package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterizedType;

import java.util.Objects;
import java.util.Set;

public class LV implements Comparable<LV> {
    private static final int HC = 4;

    public static final LV LINK_STATICALLY_ASSIGNED = new LV(0, null, null, "statically_assigned", CausesOfDelay.EMPTY);
    public static final LV LINK_ASSIGNED = new LV(1, null, null, "assigned", CausesOfDelay.EMPTY);
    public static final LV LINK_DEPENDENT = new LV(2, null, null, "dependent", CausesOfDelay.EMPTY);
    public static final LV LINK_COMMON_HC = new LV(HC, null, null, "common_hc", CausesOfDelay.EMPTY);
    public static final LV LINK_INDEPENDENT = new LV(5, null, null, "independent", CausesOfDelay.EMPTY);

    public interface HiddenContent extends Comparable<HiddenContent> {
        Set<ParameterizedType> obtain(ParameterizedType in);

        String label();

        @Override
        default int compareTo(HiddenContent o) {
            return label().compareTo(o.label());
        }
    }

    public static final HiddenContent WHOLE_TYPE = new HiddenContent() {
        @Override
        public Set<ParameterizedType> obtain(ParameterizedType in) {
            return Set.of(in);
        }

        @Override
        public String label() {
            return "-";
        }
    };
    public static final HiddenContent TYPE_PARAM_0 = new HiddenContent() {
        @Override
        public Set<ParameterizedType> obtain(ParameterizedType in) {
            return Set.of(in.parameters.get(0));
        }

        @Override
        public String label() {
            return "0";
        }
    };


    private final int value;
    private final HiddenContent mine;
    private final HiddenContent theirs;
    private final String label;
    private final CausesOfDelay causesOfDelay;

    private LV(int value, HiddenContent mine, HiddenContent theirs, String label, CausesOfDelay causesOfDelay) {
        this.value = value;
        this.mine = mine;
        this.theirs = theirs;
        this.label = Objects.requireNonNull(label);
        this.causesOfDelay = Objects.requireNonNull(causesOfDelay);
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
        return new LV(0, null, null, causes.label(), causes);
    }

    public static LV createHC(HiddenContent mine, HiddenContent theirs) {
        return new LV(HC, mine, theirs, "common_hc", CausesOfDelay.EMPTY);
    }

    public boolean isDelayed() {
        return causesOfDelay.isDelayed();
    }

    public boolean le(LV other) {
        return value <= other.value;
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
        int c = value - o.value;
        if (c != 0 || value != HC) return c;
        return mine.compareTo(theirs);
    }
}
