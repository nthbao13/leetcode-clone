package leetcode.clone.service;

import tools.jackson.databind.ObjectMapper;
import leetcode.clone.constant.BuildStatus;
import leetcode.clone.constant.SubmitStatus;
import leetcode.clone.entity.Problem;
import leetcode.clone.entity.Submission;
import leetcode.clone.entity.helper.TestCase;
import leetcode.clone.repository.SubmissionRepository;
import leetcode.clone.service.execution.ExecutionOutcome;
import leetcode.clone.service.execution.TestCaseOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SubmissionExecutor {

    private final SubmissionRepository submissionRepository;
    private final DockerExecutionService dockerExecutionService;
    private final ObjectMapper objectMapper;

    @Async
    public void execute(Long submissionId, String code, Problem problem) {
        Submission submission = submissionRepository.findById(submissionId).orElseThrow();
        submission.setSubmitStatus(SubmitStatus.IN_PROGRESS);
        submissionRepository.save(submission);

        ExecutionOutcome outcome = dockerExecutionService.executeAll(
                code, submission.getLanguage(), problem.getTestCases()
        );

        applyOutcome(submission, outcome, problem.getTestCases());
        submission.setSubmitStatus(SubmitStatus.DONE);
        submissionRepository.save(submission);
    }

    private void applyOutcome(Submission submission, ExecutionOutcome outcome, List<TestCase> testCases) {
        if (outcome.compileFailed()) {
            submission.setBuildStatus(BuildStatus.FAILURE);
            submission.setErrorOutput(outcome.compileError());
            return;
        }

        long totalRuntime = 0;
        for (int i = 0; i < outcome.testResults().size(); i++) {
            TestCaseOutcome result = outcome.testResults().get(i);
            totalRuntime += result.runtimeMs();

            if (result.timedOut()) {
                submission.setBuildStatus(BuildStatus.TIME_LIMIT_EXCEED);
                submission.setRuntimeMs(totalRuntime);
                return;
            }
            if (result.oomKilled()) {
                submission.setBuildStatus(BuildStatus.MEMORY_LIMIT_EXCEED);
                submission.setRuntimeMs(totalRuntime);
                return;
            }
            if (result.isRuntimeError()) {
                submission.setBuildStatus(BuildStatus.RUNTIME_ERROR);
                submission.setErrorOutput(result.errorOutput());
                submission.setRuntimeMs(totalRuntime);
                return;
            }

            String expected = serializeExpected(testCases.get(i).getExpectedOutput());
            if (!result.actualOutput().equals(expected)) {
                submission.setBuildStatus(BuildStatus.WRONG_ANSWER);
                submission.setOutput(result.actualOutput());
                submission.setRuntimeMs(totalRuntime);
                return;
            }
        }

        submission.setBuildStatus(BuildStatus.SUCCESS);
        submission.setRuntimeMs(totalRuntime);
    }

    private String serializeExpected(Object expectedOutput) {
        if (expectedOutput instanceof String s) return s;
        return objectMapper.writeValueAsString(expectedOutput);
    }
}
