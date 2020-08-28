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

package org.e2immu.analyser.pattern;

import java.util.function.Predicate;

/*
 Example:
   String s1 = a1;
   if (s1 == null) {
       s1 = "Was null...";
   }
   //here s1 != null


 General structure:
   LV lv = someExpression;
   if(someCondition(lv)) {
     lv = someOtherExpression;
   }

 Replace with:
   LV tmp = someExpression;
   LV lv;
   if(someCondition(tmp)) {
     lv = someOtherExpression;
   } else {
     lv = tmp;
   }
   // it is now easier to compute the value of lv


 Note that the method `conditionalValue` does the same; it should somehow be analysed in a similar way.
 That is possible if we inline, and substitute the predicate in a specific condition (which happens anyway).
 */
public class ConditionalAssignment {


    public static <T> T conditionalValue(T initial, Predicate<T> condition, T alternative) {
        return condition.test(initial) ? alternative : initial;
    }
}
