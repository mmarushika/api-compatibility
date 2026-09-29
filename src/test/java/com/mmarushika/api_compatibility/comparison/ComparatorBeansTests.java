package com.mmarushika.api_compatibility.comparison;

import com.mmarushika.api_compatibility.model.ChangeType;
import com.mmarushika.api_compatibility.model.DetectedChange;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Spring wiring of the comparator graph. Every comparator is stateless, so Spring can
 * expose it as a singleton scoped bean and hand the same instance to every collaborator and to
 * concurrent callers.
 */
@SpringBootTest
class ComparatorBeansTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ComparisonEngine engine;

    @Test
    void registersEveryComparatorAsASingletonScopedBean() {
        assertThat(engine).isSameAs(context.getBean(ComparisonEngine.class));
        assertThat(context.getBean(PathComparator.class))
                .isSameAs(context.getBean(PathComparator.class));
        assertThat(context.getBean(OperationComparator.class))
                .isSameAs(context.getBean(OperationComparator.class));
        assertThat(context.getBean(ContentComparator.class))
                .isSameAs(context.getBean(ContentComparator.class));
    }

    @Test
    void comparesSafelyWhenTheSharedEngineIsUsedConcurrently() throws Exception {
        int threads = 16;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<DetectedChange>>> tasks = IntStream.range(0, threads)
                    .mapToObj(index -> (Callable<List<DetectedChange>>) () -> engine.compare(
                            spec(new PathItem().get(new Operation()).post(new Operation())),
                            spec(new PathItem().get(new Operation()))))
                    .toList();

            List<Future<List<DetectedChange>>> futures = executor.invokeAll(tasks);

            for (Future<List<DetectedChange>> future : futures) {
                assertThat(future.get())
                        .extracting(DetectedChange::type)
                        .containsExactly(ChangeType.OPERATION_REMOVED);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static OpenAPI spec(PathItem pathItem) {
        return new OpenAPI().paths(new Paths().addPathItem("/users", pathItem));
    }
}
