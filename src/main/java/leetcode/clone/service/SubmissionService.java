package leetcode.clone.service;

import jakarta.persistence.EntityNotFoundException;
import leetcode.clone.constant.SubmitStatus;
import leetcode.clone.dto.SubmissionDTO;
import leetcode.clone.entity.Problem;
import leetcode.clone.entity.Submission;
import leetcode.clone.repository.ProblemRepository;
import leetcode.clone.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionRepository submissionRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionExecutor submissionExecutor;

    public Submission sendSubmission(SubmissionDTO dto) {
        Problem problem = problemRepository.findById(dto.getProblemId())
                .orElseThrow(() -> new EntityNotFoundException("Problem not found: " + dto.getProblemId()));

        Submission submission = new Submission();
        submission.setProblem(problem);
        submission.setLanguage(dto.getLanguage());
        submission.setCodeSubmit(dto.getCodeSubmit());
        submission.setSubmitStatus(SubmitStatus.PENDING);
        submission = submissionRepository.save(submission);

        submissionExecutor.execute(submission.getId(), dto.getCodeSubmit(), problem);
        return submission;
    }

    public Submission findById(Long id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Submission not found: " + id));
    }
}
