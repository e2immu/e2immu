package org.e2immu.analyser.resolver.testexample;

public class TypeParameter_3 {
    enum Visibility {
        NONE;
    }

    interface SerializationConfig {
        VisibilityChecker<?> getDefaultVisibilityChecker();
    }

    // from com.fasterxml.jackson.databind.introspect
    interface VisibilityChecker<T extends VisibilityChecker<T>> {
        T withGetterVisibility(Visibility v);

        T withSetterVisibility(Visibility v);
    }

    static class ObjectMapper {
        public void setVisibilityChecker(VisibilityChecker<?> vc) {

        }

        public SerializationConfig getSerializationConfig() {
            return null;
        }

    }

    private final ObjectMapper mapper = new ObjectMapper();

    // CRASH
    public void method1() {
         mapper.setVisibilityChecker(mapper.getSerializationConfig().getDefaultVisibilityChecker().
                 withGetterVisibility(Visibility.NONE).
                 withSetterVisibility(Visibility.NONE));
     }

    // NO METHOD FOUND
    public void method2() {
        VisibilityChecker<?> o = mapper.getSerializationConfig().getDefaultVisibilityChecker().
                withGetterVisibility(Visibility.NONE);
        mapper.setVisibilityChecker(o.withSetterVisibility(Visibility.NONE));
    }

    public void method3() {
        VisibilityChecker<?> o = mapper.getSerializationConfig().getDefaultVisibilityChecker().
                withGetterVisibility(Visibility.NONE);
        VisibilityChecker<?> vc = o.withSetterVisibility(Visibility.NONE);
        mapper.setVisibilityChecker(vc);
    }
}
