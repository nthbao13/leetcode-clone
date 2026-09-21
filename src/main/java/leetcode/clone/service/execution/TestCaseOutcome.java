package leetcode.clone.service.execution;

public record TestCaseOutcome(
        String actualOutput,
        String errorOutput,
        boolean timedOut,
        boolean oomKilled,
        int exitCode,
        long runtimeMs
) {
    public boolean isRuntimeError() {
        return !timedOut && !oomKilled && exitCode != 0;
    }
}
