package leetcode.clone.entity.helper;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TestCase {
    private List<Object> input;
    private Object expectedOutput;
    private boolean hidden;
}
