package tw.linedraw.quick;

public final class HandoffPolicySelfTest {
    public static void main(String[] args) {
        if (!HandoffPolicy.shouldDirect(true, false, 0, 30))
            throw new AssertionError("normal next item should use fast handoff");
        if (HandoffPolicy.shouldDirect(true, true, 0, 30))
            throw new AssertionError("manual check must not hand off");
        if (HandoffPolicy.shouldDirect(true, false, 29, 30))
            throw new AssertionError("last item must close once");
        if (HandoffPolicy.shouldDirect(false, false, 0, 30))
            throw new AssertionError("compatibility mode must close each item");
        System.out.println("HandoffPolicySelfTest passed");
    }
}
