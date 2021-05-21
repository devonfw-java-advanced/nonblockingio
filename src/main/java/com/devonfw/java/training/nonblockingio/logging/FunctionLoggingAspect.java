package com.devonfw.java.training.nonblockingio.logging;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Aspect
@Configuration
@EnableAspectJAutoProxy
public class FunctionLoggingAspect {

    Logger logger = LoggerFactory.getLogger(FunctionLoggingAspect.class);

    @Pointcut("execution(public * *(..))")
    private void anyPublicFunction() {
    }

    @Pointcut("within(com.devonfw.java.training.nonblockingio.mvc..*)")
    private void inMvcLayer() {
    }

    @Around("anyPublicFunction() && inMvcLayer()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String parameters = Stream.of(joinPoint.getArgs()).map(Object::toString)
                .collect(Collectors.joining(", ", "(", ")"));

        logger.info("Start {}.{}{}", className, methodName, parameters);
        Object obj = joinPoint.proceed();

        long timeTaken = System.currentTimeMillis() - startTime;
        logger.info("End   {}.{}{}: {} ms", className, methodName, parameters, timeTaken);

        return obj;
    }
}
