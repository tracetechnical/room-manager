package uk.co.tracetechnicalservices.roommanager.controllers

import org.springframework.web.bind.annotation.RestController
import org.springframework.beans.factory.annotation.Autowired
import uk.co.tracetechnicalservices.roommanager.services.SetupService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.system.exitProcess

@RestController
@RequestMapping("config")
class ConfigController(var setupService: SetupService) {
    @GetMapping("reload")
    fun reloadConfig(): String {
        setupService.loadConfig()
        return "OK"
    }

    @GetMapping("restart")
    fun restart(): String {
        exitProcess(0)
    }
}