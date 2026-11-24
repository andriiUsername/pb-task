package privatbank.task.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

public final class Concurrently {

    private Concurrently() {
    }

    public static <T> List<Outcome<T>> run(int times, Callable<T> task) throws InterruptedException {
        return run(Collections.nCopies(times, task));
    }

    public static <T> List<Outcome<T>> run(List<Callable<T>> tasks) throws InterruptedException {
        ExecutorService executor = new DelegatingSecurityContextExecutorService(
                Executors.newFixedThreadPool(tasks.size()));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<T> future : futures) {
                outcomes.add(Outcome.of(future));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    public record Outcome<T>(T value, Throwable error) {

        static <T> Outcome<T> of(Future<T> future) throws InterruptedException {
            try {
                return new Outcome<>(future.get(60, TimeUnit.SECONDS), null);
            } catch (ExecutionException e) {
                return new Outcome<>(null, e.getCause());
            } catch (TimeoutException e) {
                throw new AssertionError("A task did not finish in time", e);
            }
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
