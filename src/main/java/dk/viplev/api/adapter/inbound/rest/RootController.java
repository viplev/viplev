package dk.viplev.api.adapter.inbound.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequestMapping("/")
public class RootController {

    @Hidden
    @GetMapping
    public RedirectView redirectToSwagger() {
        return new RedirectView("/swagger-ui/index.html");
    }

}
