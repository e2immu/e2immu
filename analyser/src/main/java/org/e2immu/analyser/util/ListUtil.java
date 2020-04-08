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

import com.google.common.collect.ImmutableList;
import org.e2immu.annotation.ExtensionClass;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;

@ExtensionClass
public class ListUtil {

    private ListUtil() {
    }

    @NotNull
    public static <T> List<T> immutableConcat(@NotModified @NullNotAllowed Iterable<? extends T> list1,
                                              @NotModified @NullNotAllowed Iterable<? extends T> list2) {
        return new ImmutableList.Builder<T>().addAll(list1).addAll(list2).build();
    }
}
