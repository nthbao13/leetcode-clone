package leetcode.clone.service;

import leetcode.clone.entity.Problem;
import leetcode.clone.repository.ProblemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProblemService {
    private final ProblemRepository problemRepository;

    public List<Problem> findAll() {
        return problemRepository.findAll();
    }

    public Optional<Problem> findById(long id) {
        return problemRepository.findById(id);
    }
}
