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

package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.InfoObject;
import org.e2immu.analyser.model.Location;

import java.util.HashMap;
import java.util.Set;

public class DelayFactory {

    public static CausesOfDelay createDelay(CauseOfDelay cause) {
        return new SingleDelay(cause);
    }

    public static CausesOfDelay createDelay(Set<CauseOfDelay> causes) {
        if (causes.isEmpty()) return CausesOfDelay.EMPTY;
        if (causes.size() == 1) return new SingleDelay(causes.stream().findFirst().orElseThrow());
        int maxPriority = causes.stream().mapToInt(c -> c.cause().priority).max().getAsInt();
        if (maxPriority == CauseOfDelay.LOW) return new SingleDelay(causes.stream().findFirst().orElseThrow());
        return SimpleSet.mergeIntoMapAndReturn(causes.stream().filter(c -> c.cause().priority == maxPriority),
                new HashMap<>());
    }

    public static CausesOfDelay createDelay(Location location, CauseOfDelay.Cause cause) {
        return new SingleDelay(new SimpleCause(location, cause));
    }

    public static CausesOfDelay createDelay(InfoObject infoObject, CauseOfDelay.Cause cause) {
        return new SingleDelay(new SimpleCause(infoObject.newLocation(), cause));
    }
}
