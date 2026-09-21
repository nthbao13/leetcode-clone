package leetcode.clone.dto;

import leetcode.clone.constant.Language;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmissionDTO {
    private Long problemId;
    private String codeSubmit;
    private Language language;
}
