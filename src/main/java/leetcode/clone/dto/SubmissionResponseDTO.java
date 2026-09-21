package leetcode.clone.dto;

import leetcode.clone.constant.BuildStatus;
import leetcode.clone.constant.Language;
import leetcode.clone.constant.SubmitStatus;
import leetcode.clone.entity.Submission;

public record SubmissionResponseDTO(
        Long id,
        SubmitStatus submitStatus,
        BuildStatus buildStatus,
        Language language,
        String output,
        String errorOutput,
        Long runtimeMs
) {
    public static SubmissionResponseDTO from(Submission s) {
        return new SubmissionResponseDTO(
                s.getId(),
                s.getSubmitStatus(),
                s.getBuildStatus(),
                s.getLanguage(),
                s.getOutput(),
                s.getErrorOutput(),
                s.getRuntimeMs()
        );
    }
}
