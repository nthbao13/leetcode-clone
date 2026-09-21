package leetcode.clone.entity;

import jakarta.persistence.*;
import leetcode.clone.constant.BuildStatus;
import leetcode.clone.constant.Language;
import leetcode.clone.constant.SubmitStatus;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "submissions")
@Getter
@Setter
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Enumerated(EnumType.STRING)
    private Language language;

    @Column(columnDefinition = "TEXT")
    private String codeSubmit;

    @Enumerated(EnumType.STRING)
    private BuildStatus buildStatus;

    @Enumerated(EnumType.STRING)
    private SubmitStatus submitStatus;

    @Column(columnDefinition = "TEXT")
    private String output;

    @Column(columnDefinition = "TEXT")
    private String errorOutput;

    private Long runtimeMs;
}
