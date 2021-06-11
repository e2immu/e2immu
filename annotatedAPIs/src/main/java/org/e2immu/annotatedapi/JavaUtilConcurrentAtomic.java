package org.e2immu.annotatedapi;

import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotModified;

public class JavaUtilConcurrentAtomic {
    final static String PACKAGE_NAME = "java.util.concurrent.atomic";

    class AtomicInteger$ {

        @NotModified
        int get() {
            return 0;
        }

        @Modified
        int getAndIncrement() {
            return 0;
        }

        @Modified
        int incrementAndGet() { return 0; }
    }
}
