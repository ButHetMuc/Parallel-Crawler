package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object delegate;
  private final ProfilingState profilingState;


  // TODO: You will need to add more instance fields and constructor arguments to this class.
  public <T> ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.delegate = Objects.requireNonNull(delegate);
    this.profilingState = Objects.requireNonNull(state);
  }


  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.
    // Check if the method is annotated with @Profiled
    boolean isProfiled = method.isAnnotationPresent(Profiled.class);
    Instant start = null;

    if (isProfiled) {
      start = clock.instant();
    }

    try {
      // Invoke the actual method on the delegate
      return method.invoke(delegate, args);
    } catch (InvocationTargetException e) {
      // If the method throws an exception, rethrow the original cause
      throw e.getCause();
    } finally {
      if (isProfiled && start != null) {
        // Measure the time taken and record it
        Instant end = clock.instant();
        Duration elapsed = Duration.between(start, end);
        profilingState.record(delegate.getClass(), method, elapsed);
      }
    }
  }
}
