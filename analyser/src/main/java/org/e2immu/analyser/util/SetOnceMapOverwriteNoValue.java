/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.annotation.E2Container;

/**
 * @param <K>
 */
@E2Container(after = "frozen,map")
public class SetOnceMapOverwriteNoValue<K> extends SetOnceMap<K, Expression> {

    @Override
    public boolean isSet(K k) {
        Expression e = getOtherwiseNull(k);
        return e != null && e != EmptyExpression.NO_VALUE;
    }
}
