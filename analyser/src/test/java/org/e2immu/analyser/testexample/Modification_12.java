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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Dependent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Variable;

import java.util.List;

public class Modification_12 {

    static class MyList {

        @Variable // implying @Modified
        private List<String> list;

        @Modified
        @Dependent
        public void setList(@Modified List<String> list) {
            this.list = list;
        }

        public List<String> getList() {
            return list;
        }

        @Modified
        public void add(String s) {
            list.add(s); // causes potential null pointer exception
        }
    }

    /*
     in the general scheme of a = b.method(c, d), the following two methods show that the link between
     b and c is bi-directional.
     */

    public static void modifyingActionBC_1(@Modified List<String> in) {
        MyList myList = new MyList();
        myList.setList(in); // links 'in' to 'myList': changes to 'myList' will cause changes to 'in'

        myList.add("abc"); // causes in to be modified
    }

    public static void modifyingActionBC_2(@Modified List<String> in, @Modified MyList myList) {
        myList.setList(in); // links myList to in

        in.add("abc"); // causes myList to be modified
    }
}
