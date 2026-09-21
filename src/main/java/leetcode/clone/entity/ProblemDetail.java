package leetcode.clone.entity;

import jakarta.persistence.*;
import leetcode.clone.constant.Language;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "problem_detail")
@Getter
@Setter
public class ProblemDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Enumerated(EnumType.STRING)
    private Language language;

    private String codeTemplate;
}
