package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.stream.Collectors;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;

  private final PageParserFactory parserFactory;

  // Concurrent collections for thread-safe operations
  private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
  private final Map<String, Integer> wordCounts = new ConcurrentHashMap<>();

  // Maximum depth for crawling
  private final int maxDepth;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      @MaxDepth int maxDepth,
      PageParserFactory parserFactory
      ) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.parserFactory = parserFactory;
    this.maxDepth = maxDepth;
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    pool.invoke(new CrawlTask(startingUrls, 0));

    // Build and return the crawl result
    return new CrawlResult.Builder()
            .setWordCounts(wordCounts)
            .setUrlsVisited(visitedUrls.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
  private class CrawlTask extends RecursiveTask<Void> {
    private final List<String> urls;
    private final int depth;

    public CrawlTask(List<String> urls, int depth) {
      this.urls = urls;
      this.depth = depth;
    }

    @Override
    protected Void compute() {
      // Check if we've reached the maximum depth or if we've exceeded the timeout
      if (depth >= maxDepth || clock.instant().isAfter(clock.instant().plus(timeout))) {
        return null; // Stop crawling
      }

      List<CrawlTask> subtasks = new ArrayList<>();

      for (String url : urls) {
        if (visitedUrls.add(url)) { // Only visit if not already visited
          try {
            PageParser.Result result = parserFactory.get(url).parse();

            // Update word counts in a thread-safe manner
            result.getWordCounts().forEach((word, count) ->
                    wordCounts.merge(word, count, Integer::sum)
            );

            // Create subtasks for the links found on this page
            if (depth < maxDepth) {
              subtasks.add(new CrawlTask(result.getLinks(), depth + 1));
            }
          } catch (Exception e) {
            // Log the exception (could be an HTTP error, etc.)
            System.err.println("Error crawling URL " + url + ": " + e.getMessage());
          }
        }
      }

      // Execute all subtasks in parallel
      invokeAll(subtasks);
      return null;
    }
  }
}
