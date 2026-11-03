package tw.linedraw.quick;

/** Pure decision rules for the LINE-to-LINE fast handoff. */
final class HandoffPolicy {
    static final long TRANSITION_TIMEOUT_MS = 3500L;

    private HandoffPolicy() {}

    static boolean shouldDirect(boolean enabled, boolean manualCheck, int index, int total) {
        return enabled && !manualCheck && index >= 0 && index + 1 < total;
    }
}
