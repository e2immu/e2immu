package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class Container_10A {

    public Container_10A get(String key) {
        if (key.indexOf('.') != -1) {
            T tok = new T(key, '.');
            Container_10A current = this;
            while (tok.hasMoreElements()) { // 0.0.2
                String subKey = tok.nextElement();
                if (tok.hasMoreElements()) { // 0.0.2.0.1
                    current = current.get(subKey); // 0.0.2.0.1.0.0
                    if (current == null) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    @Container
    @Independent
    private static class T {
        private final char s;
        @NotModified // but assigned!
        private boolean hasMoreElements;
        @NotModified // but assigned
        private String current;

        public T(String value, char s) {
            this.current = value;
            this.s = s;
            this.hasMoreElements = (current != null && !current.isEmpty());
        }

        public boolean hasMoreElements() {
            return this.hasMoreElements;
        }

        @Modified
        public String nextElement() {
            if (!this.hasMoreElements) {
                return null;
            }
            int idx = this.current.indexOf(this.s);
            if (idx == -1) {
                this.hasMoreElements = false;
                return this.current;
            }
            String res = this.current.substring(0, idx);
            this.current = current.substring(idx + 1);
            this.hasMoreElements = !current.isEmpty();
            return res;
        }
    }
}
