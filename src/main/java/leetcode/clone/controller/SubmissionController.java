package leetcode.clone.controller;

import leetcode.clone.dto.SubmissionDTO;
import leetcode.clone.dto.SubmissionResponseDTO;
import leetcode.clone.service.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/submission")
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    public SubmissionResponseDTO sendSubmission(@RequestBody SubmissionDTO dto) {
        return SubmissionResponseDTO.from(submissionService.sendSubmission(dto));
    }

    @GetMapping("/{id}")
    public SubmissionResponseDTO getStatus(@PathVariable Long id) {
        return SubmissionResponseDTO.from(submissionService.findById(id));
    }
}
