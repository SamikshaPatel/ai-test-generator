package com.qa.ai.retry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Wires FlakeRetryAnalyzer to every @Test method globally without touching
 * individual @Test annotations.
 *
 * Register in testng.xml:
 *   <listener class-name="com.qa.ai.retry.RetryAnnotationTransformer"/>
 *
 * Only TimeoutErrors are retried — see FlakeRetryAnalyzer for the policy.
 */
public class RetryAnnotationTransformer implements IAnnotationTransformer {

    private static final Logger log = LogManager.getLogger(RetryAnnotationTransformer.class);

    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        if (annotation.getRetryAnalyzerClass() == null) {
            annotation.setRetryAnalyzer(FlakeRetryAnalyzer.class);
            log.debug("FlakeRetryAnalyzer wired to: {}",
                    testMethod != null ? testMethod.getName() : "unknown");
        }
    }
}
