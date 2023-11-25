package org.e2immu.analyser.resolver.testexample;

import java.util.Set;

// should catch an error, but does not...?
public class MethodCall_51 {

    static class ResolverPath implements java.io.Serializable {

        interface PathProcessor {
            void processPath(ResolverPath path);
        }

        public void get(Set<ResolverPath> paths, PathProcessor processor) {
        }
    }


    interface PathProcessor extends ResolverPath.PathProcessor {
        void setOuterJoin(boolean outerJoin);
    }

    private PathProcessor processor;

    public void method() {
        new ResolverPath().get(null, processor);
    }

}
