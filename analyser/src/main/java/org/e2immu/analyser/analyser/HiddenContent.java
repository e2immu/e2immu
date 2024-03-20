package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;

public record HiddenContent(List<ParameterizedType> hiddenTypes, CausesOfDelay causesOfDelay) {
    public HiddenContent {
        assert hiddenTypes != null;
    }

    public HiddenContent merge(HiddenContent other) {
        return new HiddenContent(ListUtil.concatImmutable(hiddenTypes, other.hiddenTypes),
                causesOfDelay.merge(other.causesOfDelay));
    }
}
