package br.edu.usjt.dinga.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Redireciona os atalhos {@code /slides} e {@code /slides/} para {@code /slides/index.html},
 * garantindo carregamento imediato da apresentação comercial e técnica.
 */
@Controller
public class SlidesRedirectController {

    @GetMapping({"/slides", "/slides/"})
    public String redirectToSlidesIndex() {
        return "redirect:/slides/index.html";
    }
}
