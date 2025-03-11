package com.grocerymanager.api.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(public * com.grocerymanager.api.controller..*.*(..))")
    public void controllerMethods() {}

    @Pointcut("execution(public * com.grocerymanager.api.service..*.*(..))")
    public void serviceMethods() {}

    @Around("controllerMethods() || serviceMethods()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();

        String params = Arrays.toString(joinPoint.getArgs());
        // Hide passwords in logs
        if (params.contains("password")) {
            params = params.replaceAll("password=[^,\\]]*", "password=[REDACTED]");
        }

        logger.info("→ Method called: {}.{}() with params: {}", className, methodName, params);

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - start;

        if (logger.isDebugEnabled()) {
            String resultStr;
            if (result instanceof Optional<?>) {
                resultStr = ((Optional<?>) result).isPresent() ? "Optional[present]" : "Optional.empty";
            } else if (result instanceof Collection<?>) {
                resultStr = "Collection of size " + ((Collection<?>) result).size();
            } else if (result != null && result.getClass().getName().startsWith("com.grocerymanager.api.model")) {
                resultStr = result.getClass().getSimpleName() + "[...]";
            } else {
                resultStr = result != null ? result.toString() : "null";
            }

            // Truncate results that are too long
            if (resultStr.length() > 200) {
                resultStr = resultStr.substring(0, 200) + "... (tronqué)";
            }
            logger.debug("← Method completed: {}.{}() in {}ms, result: {}",
                    className, methodName, duration, resultStr);
        } else {
            logger.info("← Method completed: {}.{}() in {}ms", className, methodName, duration);
        }

        return result;
    }
}