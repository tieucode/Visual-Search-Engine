package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.messaging.ImageIndexingMessage.ImageItem;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Holds at most one batch of preparation futures ahead of the consumer. */
public final class PreparationPipeline implements AutoCloseable {
    public record Outcome(ImageItem item, PreparedImage image, RuntimeException failure) {}
    private final List<ImageItem> items;
    private final int batchSize;
    private final ThreadPoolTaskExecutor executor;
    private final ImagePreparation preparation;
    private final List<Future<Outcome>> futures = new ArrayList<>();
    private int consumed;
    private int submitted;

    public PreparationPipeline(List<ImageItem> items, int batchSize,
            ThreadPoolTaskExecutor executor, ImagePreparation preparation) {
        this.items = items;
        this.batchSize = batchSize;
        this.executor = executor;
        this.preparation = preparation;
        submitNext();
    }
    public boolean hasNext() { return consumed < items.size(); }
    public List<ImageItem> remaining() { return items.subList(consumed, items.size()); }
    private void submitNext() {
        int end = Math.min(submitted + batchSize, items.size());
        while (submitted < end) {
            ImageItem item = items.get(submitted++);
            futures.add(executor.submit(() -> {
                try { return new Outcome(item, preparation.prepare(item), null); }
                catch (RuntimeException failure) { return new Outcome(item, null, failure); }
            }));
        }
    }
    public List<Outcome> next() {
        List<Outcome> outcomes = new ArrayList<>();
        try {
            for (Future<Outcome> future : futures) outcomes.add(future.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Image preparation interrupted", exception);
        } catch (ExecutionException | CancellationException exception) {
            throw new IllegalStateException("Image preparation did not complete", exception);
        }
        consumed += outcomes.size();
        futures.clear();
        submitNext();
        return outcomes;
    }
    @Override public void close() {
        futures.forEach(future -> future.cancel(true));
        executor.getThreadPoolExecutor().purge();
        futures.clear();
    }
}
