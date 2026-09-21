package leetcode.clone.controller;

import leetcode.clone.dto.SubmissionDTO;
import leetcode.clone.entity.Submission;
import leetcode.clone.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/submission")
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public Submission sendSubmission(@RequestBody SubmissionDTO dto) {
        return submissionService.sendSubmission(dto);
    }

    @GetMapping("/{id}")
    public Submission getStatus(@PathVariable Long id) {
        return submissionService.findById(id);
    }
}
