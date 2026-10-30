package com.assistant.guest;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GuestInitializer implements ApplicationRunner {

    private final GuestService guests;

    public GuestInitializer(GuestService guests) {
        this.guests = guests;
    }

    @Override
    public void run(ApplicationArguments args) {
        guests.ensureGuest();
    }
}
