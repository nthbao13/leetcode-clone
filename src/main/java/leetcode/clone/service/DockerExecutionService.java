package leetcode.clone.service;

import tools.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import leetcode.clone.constant.Language;
import leetcode.clone.entity.helper.TestCase;
import leetcode.clone.service.execution.ExecutionOutcome;
import leetcode.clone.service.execution.TestCaseOutcome;
import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DockerExecutionService {

    private final DockerClient dockerClient;
    private final ObjectMapper objectMapper;
    private final ContainerPool containerPool;

    @Value("${docker.timeout.compile.seconds}")
    private long compileTimeoutSeconds;

    @Value("${docker.timeout.run.seconds}")
    private long runTimeoutSeconds;

    public ExecutionOutcome executeAll(String code, Language language, List<TestCase> testCases) {
        String containerId;
        try {
            containerId = containerPool.borrow(language);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExecutionOutcome.compilationFailure("Container pool interrupted");
        }
        if (containerId == null) {
            return ExecutionOutcome.compilationFailure("Container pool exhausted");
        }

        try {
            copyToContainer(containerId, code, getFileName(language));

            if (language == Language.JAVA || language == Language.CPP) {
                TestCaseOutcome compile = compile(containerId, language);
                if (compile.timedOut()) {
                    return ExecutionOutcome.compilationFailure("Compilation timed out");
                }
                if (compile.exitCode() != 0) {
                    return ExecutionOutcome.compilationFailure(compile.errorOutput());
                }
            }

            List<TestCaseOutcome> results = new ArrayList<>();
            for (TestCase testCase : testCases) {
                String inputJson = objectMapper.writeValueAsString(testCase.getInput());
                copyToContainer(containerId, inputJson, "input.json");
                TestCaseOutcome result = run(containerId, language);
                results.add(result);
                if (result.timedOut() || result.oomKilled()) break;
            }
            return ExecutionOutcome.success(results);

        } catch (Exception e) {
            return ExecutionOutcome.compilationFailure(e.getMessage());
        } finally {
            containerPool.release(language, containerId);
        }
    }

    private void copyToContainer(String containerId, String content, String fileName) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            TarArchiveEntry entry = new TarArchiveEntry(fileName);
            entry.setSize(bytes.length);
            tar.putArchiveEntry(entry);
            tar.write(bytes);
            tar.closeArchiveEntry();
        }

        dockerClient.copyArchiveToContainerCmd(containerId)
                .withTarInputStream(new ByteArrayInputStream(baos.toByteArray()))
                .withRemotePath("/sandbox")
                .exec();
    }

    private TestCaseOutcome compile(String containerId, Language language) throws InterruptedException {
        String[] cmd = switch (language) {
            case JAVA -> new String[]{"javac", "-cp", "/opt/gson.jar", "-d", "/sandbox", "/sandbox/Solution.java"};
            case CPP -> new String[]{"g++", "/sandbox/solution.cpp", "-o", "/sandbox/solution"};
            default -> throw new IllegalArgumentException("Language does not need compilation: " + language);
        };
        return exec(containerId, cmd, compileTimeoutSeconds, false);
    }

    private TestCaseOutcome run(String containerId, Language language) throws InterruptedException {
        String[] cmd = switch (language) {
            case JAVA -> new String[]{"sh", "-c", "java -cp /sandbox:/opt/gson.jar __Runner < /sandbox/input.json"};
            case PYTHON -> new String[]{"sh", "-c", "python3 /sandbox/solution.py < /sandbox/input.json"};
            case CPP -> new String[]{"sh", "-c", "/sandbox/solution < /sandbox/input.json"};
        };
        return exec(containerId, cmd, runTimeoutSeconds, true);
    }

    private TestCaseOutcome exec(String containerId, String[] cmd, long timeoutSeconds, boolean checkOom)
            throws InterruptedException {
        String execId = dockerClient.execCreateCmd(containerId)
                .withCmd(cmd)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec()
                .getId();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        long start = System.currentTimeMillis();

        boolean completed = dockerClient.execStartCmd(execId)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (frame.getStreamType() == StreamType.STDOUT) {
                                stdout.write(frame.getPayload());
                            } else if (frame.getStreamType() == StreamType.STDERR) {
                                stderr.write(frame.getPayload());
                            }
                        } catch (IOException ignored) {}
                    }
                })
                .awaitCompletion(timeoutSeconds, TimeUnit.SECONDS);

        long runtimeMs = System.currentTimeMillis() - start;
        int exitCode = completed
                ? dockerClient.inspectExecCmd(execId).exec().getExitCodeLong().intValue()
                : -1;

        // exit code 137 = SIGKILL (128+9), sent by Docker OOM killer when memory limit exceeded
        boolean oomKilled = checkOom && exitCode == 137;

        return new TestCaseOutcome(
                stdout.toString(StandardCharsets.UTF_8).trim(),
                stderr.toString(StandardCharsets.UTF_8).trim(),
                !completed,
                oomKilled,
                exitCode,
                runtimeMs
        );
    }

    private String getFileName(Language language) {
        return switch (language) {
            case JAVA -> "Solution.java";
            case PYTHON -> "solution.py";
            case CPP -> "solution.cpp";
        };
    }
}
