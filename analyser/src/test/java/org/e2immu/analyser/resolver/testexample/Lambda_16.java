package org.e2immu.analyser.resolver.testexample;

import java.util.List;
import java.util.function.Supplier;

public class Lambda_16 {

    static class OO {
    }

    static abstract class Bar {
    }

    @FunctionalInterface
    interface Provider<O extends Bar> {
        List<OO> get(O dto);
    }

    static class Util {
        public <O extends Bar> Util(final List<O> list, Provider<O> provider) {
        }
    }

    static class Config extends Bar {
        List<OO> oos;
    }

    Util method1(List<Config> options) {
        return new Util(options, dto -> dto.oos);
    }

    Util method2(Supplier<List<Config>> options) {
        return new Util(options.get(), dto -> dto.oos);
    }
}
