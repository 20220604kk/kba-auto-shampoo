package tw.linedraw.quick;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Samples one independent wait for each phase, inclusive of 0.3 seconds and its configured upper bound. */
final class WaitPolicy {
    static final int MIN_MILLIS = 300;

    private WaitPolicy() {}

    static long sampleMillis(float upperSeconds) {
        return sampleMillis(upperSeconds, ThreadLocalRandom.current());
    }

    static long sampleMillis(float upperSeconds, Random random) {
        if (!Float.isFinite(upperSeconds) || upperSeconds < .3f || upperSeconds > 30f)
            throw new IllegalArgumentException("Wait upper bound must be 0.3–30 seconds");
        int upperMillis = Math.max(MIN_MILLIS, Math.round(upperSeconds * 1000));
        return MIN_MILLIS + random.nextInt(upperMillis - MIN_MILLIS + 1);
    }
}
