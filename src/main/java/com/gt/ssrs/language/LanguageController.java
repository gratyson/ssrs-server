package com.gt.ssrs.language;

import com.gt.ssrs.model.Language;
import com.gt.ssrs.model.WordElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest/language")
public class LanguageController {

    private static final Logger log = LoggerFactory.getLogger(LanguageController.class);

    private final LanguageService languageService;

    @Autowired
    public LanguageController(LanguageService languageService) {
        this.languageService = languageService;
    }

    @GetMapping(value = "/allElements", produces = "application/json")
    public List<WordElement> getAllLanguageElements() {
        return languageService.GetAllLanguageElements();
    }

    @GetMapping(value = "/allLanguages", produces = "application/json")
    public List<Language> getAllLanguages() {
        return languageService.GetAllLanguages();
    }


}
