package com.gt.ssrs.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableCaching
@EnableScheduling
public class CachingConfig {

    private static final Logger log = LoggerFactory.getLogger(CachingConfig.class);

    public static final String LANGUAGES = "languages";
    public static final String LANGUAGE_SEQUENCE = "language_sequence";
    private static final long CACHE_EVICT_SCHEDULE_MS = 15 * 60 * 1000;

    @Bean
    public CacheManager getLanguageCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(LANGUAGES, LANGUAGE_SEQUENCE);
        return cacheManager;
    }

    @CacheEvict(allEntries = true, value = {LANGUAGES, LANGUAGE_SEQUENCE})
    @Scheduled(fixedDelay = CACHE_EVICT_SCHEDULE_MS,  initialDelay = CACHE_EVICT_SCHEDULE_MS)
    public void ReportLanguageCacheEvict() {
        log.info("Flushing " + LANGUAGES + " cache.");
    }

}
