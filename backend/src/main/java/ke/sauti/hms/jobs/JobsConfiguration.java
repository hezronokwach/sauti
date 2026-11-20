package ke.sauti.hms.jobs;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on the scheduler that drives {@link JobWorker}. Tests switch the worker off
 * ({@code sauti.jobs.enabled=false}) and run it step by step instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class JobsConfiguration {}
