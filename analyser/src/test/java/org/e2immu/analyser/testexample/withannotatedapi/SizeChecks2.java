package org.e2immu.analyser.testexample.withannotatedapi;

import org.e2immu.annotation.*;

import java.util.Set;

public class SizeChecks2 {

    // this test tries to ensure that the effects of the "add1" method are not shown on "set1"
    // the method may not be called

    static class Test1 {
        @Size(type = AnnotationType.VERIFY_ABSENT)
        @Variable
        @Nullable
        private Set<String> set1;

        @Modified
        @Size(type = AnnotationType.VERIFY_ABSENT)
        public void add1(String s) {
            set1.add(s); // ERROR causes a potential null pointer exception
        }

        public void setSet1(Set<String> set1) {
            this.set1 = set1;
        }

        public Set<String> getSet1() {
            return set1;
        }
    }

    // variant an test1: even if add2 is called, there's a condition on the modification!
    // no guarantee that it has an effect

    static class Test2 {

        @Size(type = AnnotationType.VERIFY_ABSENT)
        @Variable
        @Nullable
        private Set<String> set2;

        @Modified
        @Size(type = AnnotationType.VERIFY_ABSENT)
        public Set<String> add2(String s) {
            Set<String> set2 = this.set2;
            if (set2 != null) set2.add(s);
            return set2;
        }

        public void setSet2(Set<String> set2) {
            this.set2 = set2;
        }

        public Set<String> getSet2() {
            return set2;
        }
    }

    static class Test3 {

        @Size(min = 1)
        @Final
        @NotNull
        private final Set<String> set3;

        public Test3(Set<String> set3) {
            if(set3 == null || set3.isEmpty()) {
                throw new UnsupportedOperationException();
            }
            this.set3 = set3;
        }

        public Set<String> getSet3() {
            return set3;
        }

    }
}

