package pl.michalbzowski.windband.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/band")
public class BandPageController {

    @GetMapping("/attributes")
    public String attributeDefsPage() {
        return "band/attribute-defs";
    }
}
