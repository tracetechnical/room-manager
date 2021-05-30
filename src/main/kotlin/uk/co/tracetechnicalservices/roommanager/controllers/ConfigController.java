package uk.co.tracetechnicalservices.roommanager.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.co.tracetechnicalservices.roommanager.services.SetupService;

@RestController
public class ConfigController {
    SetupService setupService;

    @Autowired
    public ConfigController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping
    public String de() {
        return "aa";
    }
}
