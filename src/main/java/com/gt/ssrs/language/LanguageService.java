package com.gt.ssrs.language;

import com.gt.ssrs.conf.CachingConfig;
import com.gt.ssrs.language.model.DBLanguage;
import com.gt.ssrs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LanguageService {

    private static final Logger log = LoggerFactory.getLogger(LanguageService.class);

    private final LanguageDao languageDao;

    @Autowired
    public LanguageService(LanguageDao languageDao) {
        this.languageDao = languageDao;
    }

    @Cacheable(CachingConfig.LANGUAGES)
    public List<Language> getAllLanguages() {
        List<DBLanguage> dbLanguages = languageDao.getAllLanguages();
        Map<String, WordElement> languageElements = languageDao.getAllLanguageElements().stream().collect(Collectors.toMap(le -> le.id(), le -> le));
        Map<Long, List<TestRelationship>> reviewRelationships = languageDao.getAllReviewRelationships();

        List<Language> allLanguages = new ArrayList<>();
        for(DBLanguage dbLanguage : dbLanguages) {
            allLanguages.add(new Language(
                    dbLanguage.id(),
                    dbLanguage.displayName(),
                    dbLanguage.fontName(),
                    dbLanguage.audioFileRegex(),
                    dbLanguage.testsToDouble(),
                    dbLanguage.validElementIds().stream().map(id -> languageElements.get(id)).collect(Collectors.toList()),
                    dbLanguage.requiredElementIds().stream().map(id -> languageElements.get(id)).collect(Collectors.toList()),
                    dbLanguage.coreElementIds().stream().map(id -> languageElements.get(id)).collect(Collectors.toList()),
                    dbLanguage.dedupeElementIds().stream().map(id -> languageElements.get(id)).collect(Collectors.toList()),
                    reviewRelationships.getOrDefault(dbLanguage.id(), List.of())));
        }

        return allLanguages;
    }

    public List<WordElement> GetAllLanguageElements() {
        return languageDao.getAllLanguageElements();
    }

    public Language GetLanguageById(long languageId) {
        for(Language language : getAllLanguages()) {
            if (language.id() == languageId) {
                return language;
            }
        }

        return null;
    }

    @Cacheable(CachingConfig.LANGUAGE_SEQUENCE)
    public List<LanguageSequenceValue> getLanguageSequence(long languageId, ReviewType reviewType) {
        return languageDao.getLanguageSequence(languageId, reviewType);
    }
}
