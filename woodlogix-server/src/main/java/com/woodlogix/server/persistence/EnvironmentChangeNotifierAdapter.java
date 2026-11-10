package com.woodlogix.server.persistence;

import com.woodlogix.application.environment.EnvironmentChangeNotifier;
import com.woodlogix.server.event.EnvironmentChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class EnvironmentChangeNotifierAdapter implements EnvironmentChangeNotifier {
    private final ApplicationEventPublisher eventPublisher;

    public EnvironmentChangeNotifierAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void environmentChanged() {
        eventPublisher.publishEvent(new EnvironmentChangedEvent());
    }
}
