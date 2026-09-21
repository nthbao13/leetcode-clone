package leetcode.clone.controller;

import leetcode.clone.dto.ProblemDTO;
import leetcode.clone.entity.Problem;
import leetcode.clone.service.ProblemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/problems")
public class ProblemController {
    private final ProblemService problemService;

    @GetMapping
    public List<ProblemDTO> findAll() {
        List<Problem> problems = problemService.findAll();
        return problems.stream().map(ProblemDTO::new).toList();
    }

    @GetMapping("/{id}")
    public ProblemDTO findOne(@PathVariable Long id) {
        return new ProblemDTO(problemService.findById(id).orElseThrow(() -> new RuntimeException("problem not found")));
    }
}
