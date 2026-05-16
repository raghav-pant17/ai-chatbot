package com.personal.ai_chatbot.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
public class RepositoryLoggingAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryLoggingAspect.class);
    private static final long SLOW_CALL_THRESHOLD_MS = 500;

    @Around("execution(* com.personal.ai_chatbot.repository..*(..))")
    public Object logRepositoryCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String repository = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String method = joinPoint.getSignature().getName();
        long startedAt = System.nanoTime();

        LOGGER.info("DB call started repository={} method={} args={}", repository, method, joinPoint.getArgs().length);

        try {
            Object result = joinPoint.proceed();
            long elapsedMs = elapsedMillis(startedAt);
            if (elapsedMs >= SLOW_CALL_THRESHOLD_MS) {
                LOGGER.warn("DB call completed slowly repository={} method={} elapsedMs={}", repository, method, elapsedMs);
            } else {
                LOGGER.info("DB call completed repository={} method={} elapsedMs={}", repository, method, elapsedMs);
            }
            return result;
        } catch (Throwable ex) {
            LOGGER.warn(
                    "DB call failed repository={} method={} elapsedMs={} error={}",
                    repository,
                    method,
                    elapsedMillis(startedAt),
                    ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
