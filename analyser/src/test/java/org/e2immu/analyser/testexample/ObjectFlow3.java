package org.e2immu.analyser.testexample;

public class ObjectFlow3 {

    static class Config {
        final boolean debug;
        final int complexity;

        public Config(boolean debug, int complexity) {
            this.debug = debug;
            this.complexity = complexity;
        }

        @Override
        public String toString() {
            return "debug: " + debug + ", complexity " + complexity;
        }
    }

    public static void main(String[] args) {
        Config config = new Config(Boolean.parseBoolean(args[0]), Integer.parseInt(args[1]));
        new Main(config).go();
    }

    static class Main {
        public final Config config;

        public Main(Config config) {
            this.config = config;
        }

        public void go() {
           InBetween inBetween = new InBetween(config);
           inBetween.go();
        }
    }

    static class InBetween {
        private final Config config;

        InBetween(Config config) {
            this.config = config;
        }

        void go() {
            DoSomeWork doSomeWork = new DoSomeWork(config);
            doSomeWork.go();
        }
    }

    static class DoSomeWork {
        private final Config config;

        DoSomeWork(Config config) {
            this.config = config;
        }

        void go() {
            System.out.println(config.complexity);
        }
    }
}
