package com.gt.ssrs.language;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rest/language")
public class LanguageController {

    private static final Logger log = LoggerFactory.getLogger(LanguageController.class);

    @GetMapping(value = "/allElements", produces = "application/json")
    public List<WordElement> getAllLanguageElements() {
        return List.of(WordElement.values());
    }

    @GetMapping(value = "/allLanguages", produces = "application/json")
    public List<Language> getAllLanguages() {
        return List.of(Language.values());
    }


}
