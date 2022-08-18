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

package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.support.SetOnce;

import java.util.Map;
/*
mimics code in MethodAnalyserVisitor, try to catch a bug brought by TestAnalyseTest
 */

public interface Independent1_7 {

    @ImmutableContainer(after = "data,name", hc = true)
    class MethodInfo<X> {
        public final SetOnce<String> name = new SetOnce<>();
        public final SetOnce<X> data = new SetOnce<>();

        public String name() {
            return name.getOrDefault("not yet set");
        }

        public X getX() {
            return data.getOrDefaultNull();
        }
    }

    void visit(Data<?> data);

    record Data<X>(int iteration,
                MethodInfo<X> methodInfo,
                Map<String, Integer> statuses) {

        public Data {
            assert methodInfo != null;
        }

        public String label() {
            return methodInfo.name();
        }

        public Integer getStatus(String key) {
            if (statuses == null) return null;
            return statuses.get(key);
        }
    }

}
