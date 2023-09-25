package org.e2immu.annotatedapi.java;

import org.e2immu.annotation.Independent;

import java.util.random.RandomGenerator;
import java.util.stream.Stream;

public class JavaUtilRandom {
    public static final String PACKAGE_NAME = "java.util.random";

    @Independent
    interface RandomGenerator$ {


        @Independent
        interface ArbitrarilyJumpableGenerator {
            @Independent
            ArbitrarilyJumpableGenerator of(String name);

            @Independent
            ArbitrarilyJumpableGenerator copy();
            @Independent
            void jump();

            @Independent
            void leap();
        }

        @Independent
        interface JumpableGenerator {
            @Independent
            ArbitrarilyJumpableGenerator of(String name);

            @Independent
            ArbitrarilyJumpableGenerator copy();

            @Independent
            void jump();

            @Independent
            Stream<RandomGenerator> rngs();
            @Independent
            Stream<RandomGenerator> rngs(long l);
        }

        @Independent
        interface LeapableGenerator {
            @Independent
            ArbitrarilyJumpableGenerator of(String name);

            @Independent
            ArbitrarilyJumpableGenerator copy();

            @Independent
            void leap();
        }

        @Independent
        interface StreamableGenerator {
            @Independent
            ArbitrarilyJumpableGenerator of(String name);

            @Independent
            Stream<RandomGenerator> rngs();
            @Independent
            Stream<RandomGenerator> rngs(long l);
        }

        @Independent
        interface SplittableGenerator {
            @Independent
            ArbitrarilyJumpableGenerator of(String name);

            @Independent
            Stream<RandomGenerator> rngs();
            @Independent
            Stream<RandomGenerator> rngs(long l);
        }
    }
}
