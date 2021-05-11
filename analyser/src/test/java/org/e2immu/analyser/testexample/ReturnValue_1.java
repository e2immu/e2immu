package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Container;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.Variable;
import org.junit.jupiter.api.Test;

/*
more complex; no value expected for nextInt.
 */
public class ReturnValue_1 {

    @Container
    static class Random {
        @Variable
        private int seed;

        public Random(int seed) {
            this.seed = seed;
        }

        @Modified
        public int next() {
            seed = (23 * seed + 41) % 149;
            return seed;
        }
    }

    private final Random random = new Random(432);

    @Modified
    public int nextInt(int max) {
        return random.next() % max;
    }

    @Test
    public void test() {
        for (int i = 0; i < 20; i++) {
            System.out.println(nextInt(50));
        }
    }
}
