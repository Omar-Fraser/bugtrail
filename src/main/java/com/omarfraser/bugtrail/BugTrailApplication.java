package com.omarfraser.bugtrail;

import com.omarfraser.bugtrail.triage.TriageService;
import com.omarfraser.bugtrail.triage.TriageWeights;
import com.omarfraser.bugtrail.workflow.TransitionValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BugTrailApplication {

    public static void main(String[] args) {
        SpringApplication.run(BugTrailApplication.class, args);
    }

    /**
     * The triage engine and transition validator are plain Java with no Spring
     * annotations of their own, so they are registered here instead.
     *
     * <p>That is deliberate: keeping the framework out of the domain logic is what
     * lets their tests run without a Spring context, which is why the unit layer
     * finishes in under a second.
     */
    @Bean
    public TriageService triageService() {
        return new TriageService(TriageWeights.DEFAULTS);
    }

    @Bean
    public TransitionValidator transitionValidator() {
        return new TransitionValidator();
    }
}
