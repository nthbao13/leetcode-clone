package leetcode.clone.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import leetcode.clone.constant.Language;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ContainerPool {

    private final DockerClient dockerClient;

    @Value("${docker.image.java}")
    private String javaImage;

    @Value("${docker.image.python}")
    private String pythonImage;

    @Value("${docker.image.cpp}")
    private String cppImage;

    @Value("${docker.memory.bytes}")
    private long memoryBytes;

    @Value("${docker.cpu.quota}")
    private long cpuQuota;

    @Value("${docker.cpu.period}")
    private long cpuPeriod;

    @Value("${docker.pool.size:5}")
    private int size;

    private final Map<Language, BlockingQueue<String>> pools = new EnumMap<>(Language.class);

    @PostConstruct
    public void init() {
        for (Language lang : Language.values()) {
            BlockingQueue<String> queue = new ArrayBlockingQueue<>(size);
            for (int i = 0; i < size; i++) {
                queue.offer(createAndStart(lang));
            }
            pools.put(lang, queue);
        }
    }

    public String borrow(Language lang) throws InterruptedException {
        return pools.get(lang).poll(30, TimeUnit.SECONDS);
    }

    public String tryBorrow(Language lang, long timeout, TimeUnit unit) throws InterruptedException {
        return pools.get(lang).poll(timeout, unit);
    }

    public void release(Language lang, String containerId) {
        cleanSandbox(containerId);
        pools.get(lang).offer(containerId);
    }

    public int poolSize(Language lang) {
        return size;
    }

    @PreDestroy
    public void destroy() {
        pools.values().forEach(queue -> {
            String id;
            while ((id = queue.poll()) != null) {
                dockerClient.removeContainerCmd(id).withForce(true).exec();
            }
        });
    }

    private String createAndStart(Language lang) {
        String image = switch (lang) {
            case JAVA -> javaImage;
            case PYTHON -> pythonImage;
            case CPP -> cppImage;
        };

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(memoryBytes)
                .withCpuQuota(cpuQuota)
                .withCpuPeriod(cpuPeriod)
                .withNetworkMode("none");

        String id = dockerClient.createContainerCmd(image)
                .withHostConfig(hostConfig)
                .withCmd("sleep", "infinity")
                .withTty(false)
                .exec()
                .getId();

        dockerClient.startContainerCmd(id).exec();
        return id;
    }

    private void cleanSandbox(String containerId) {
        try {
            dockerClient.execCreateCmd(containerId)
                    .withCmd("sh", "-c", "rm -rf /sandbox/* && mkdir -p /sandbox")
                    .withAttachStdout(false)
                    .withAttachStderr(false)
                    .exec();
        } catch (Exception ignored) {}
    }
}
