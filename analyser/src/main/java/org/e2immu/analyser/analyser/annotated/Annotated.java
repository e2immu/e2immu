/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser.annotated;

public abstract class Annotated {

   protected static <T> T state(T t) { return t; }

   static boolean addModificationHelper(int i, int j, boolean containsE, boolean notContainsE) {
      return state(containsE) ? i == j : state(notContainsE) || j == 0 ? i == j + 1 : i >= j && i <= j + 1;
   }
   static boolean addValueHelper(int i, int j, boolean containsE, boolean notContainsE, boolean retVal) {
      return !state(containsE) && (state(notContainsE) || j == 0 || retVal);
   }
}
