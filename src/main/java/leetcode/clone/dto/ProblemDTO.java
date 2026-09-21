package leetcode.clone.dto;

import leetcode.clone.entity.Problem;
import leetcode.clone.entity.ProblemDetail;
import leetcode.clone.entity.helper.TestCase;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProblemDTO {
    private long id;
    private String title;
    private String description;
    private List<TestCase> testCases;
    private List<ProblemDetail> problemDetails;

    public ProblemDTO(Problem problem) {
        this.id = problem.getId();
        this.title = problem.getTitle();
        this.description = problem.getDescription();
        this.testCases = problem.getTestCases();
        this.problemDetails = problem.getDetails();
    }
}
