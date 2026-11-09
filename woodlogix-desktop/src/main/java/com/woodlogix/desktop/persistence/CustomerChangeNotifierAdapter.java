package com.woodlogix.desktop.persistence;

import com.woodlogix.application.customer.CustomerChangeNotifier;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CustomerChangeNotifierAdapter implements CustomerChangeNotifier {
    private final ApplicationEventPublisher eventPublisher;

    public CustomerChangeNotifierAdapter(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public void customerChanged() {
        eventPublisher.publishEvent(new CustomerChangedEvent());
    }
}
