package tw.linedraw.quick;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Offline timing check; does not open LINE or submit a draw. */
public final class WaitPolicySelfTest {
    public static void main(String[] args) {
        Random random = new Random(23);
        if (WaitPolicy.sampleMillis(.1f, random) != 100) throw new AssertionError("fixed lower bound");
        Set<Long> samples = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            long wait = WaitPolicy.sampleMillis(.8f, random);
            if (wait < 100 || wait > 800) throw new AssertionError("outside 0.1–0.8: " + wait);
            samples.add(wait);
        }
        if (samples.size() < 100) throw new AssertionError("waits did not vary");
        try {
            WaitPolicy.sampleMillis(.05f, random);
            throw new AssertionError("invalid wait accepted");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
        System.out.println("WaitPolicySelfTest passed");
    }
}
