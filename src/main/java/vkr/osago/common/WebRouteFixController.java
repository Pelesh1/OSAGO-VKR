package vkr.osago.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebRouteFixController {

    @GetMapping({"/insurance/osago", "/insurance/osago/", "/insurance/osago/."})
    public String osagoIndex() {
        return "forward:/insurance/osago/index.html";
    }
}
