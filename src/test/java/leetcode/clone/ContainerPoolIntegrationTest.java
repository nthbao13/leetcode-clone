package leetcode.clone;

import leetcode.clone.constant.Language;
import leetcode.clone.service.ContainerPool;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ContainerPoolIntegrationTest {

    @Autowired
    ContainerPool containerPool;

    @Test
    void borrow_returnsRunningContainer() throws InterruptedException {
        String id = containerPool.borrow(Language.PYTHON);

        assertThat(id).isNotNull();

        containerPool.release(Language.PYTHON, id);
    }

    @Test
    void release_makesContainerAvailableAgain() throws InterruptedException {
        int size = containerPool.poolSize(Language.PYTHON);
        String[] drained = new String[size];

        // Drain the entire pool
        for (int i = 0; i < size; i++) {
            drained[i] = containerPool.borrow(Language.PYTHON);
        }

        // Release one back
        containerPool.release(Language.PYTHON, drained[0]);

        // Now we should be able to borrow again
        String reused = containerPool.tryBorrow(Language.PYTHON, 500, TimeUnit.MILLISECONDS);
        assertThat(reused).isEqualTo(drained[0]);

        // Cleanup
        containerPool.release(Language.PYTHON, reused);
        for (int i = 1; i < size; i++) {
            containerPool.release(Language.PYTHON, drained[i]);
        }
    }

    @Test
    void borrow_returnsNullWhenPoolExhausted() throws InterruptedException {
        int poolSize = containerPool.poolSize(Language.PYTHON);
        String[] borrowed = new String[poolSize];

        for (int i = 0; i < poolSize; i++) {
            borrowed[i] = containerPool.borrow(Language.PYTHON);
        }

        String extra = containerPool.tryBorrow(Language.PYTHON, 200, TimeUnit.MILLISECONDS);
        assertThat(extra).isNull();

        for (String id : borrowed) {
            containerPool.release(Language.PYTHON, id);
        }
    }
}
