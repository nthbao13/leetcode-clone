package leetcode.clone.service.execution;

import java.util.List;

public record ExecutionOutcome(
        boolean compileFailed,
        String compileError,
        List<TestCaseOutcome> testResults
) {
    public static ExecutionOutcome compilationFailure(String error) {
        return new ExecutionOutcome(true, error, List.of());
    }

    public static ExecutionOutcome success(List<TestCaseOutcome> results) {
        return new ExecutionOutcome(false, null, results);
    }
}
