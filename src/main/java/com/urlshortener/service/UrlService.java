package com.urlshortener.service;

import com.urlshortener.model.Url;
import com.urlshortener.repository.UrlRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UrlService {

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String REDIS_PREFIX = "url:";

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${app.instance-id}")
    private int instanceId;

    @Value("${app.total-instances}")
    private int totalInstances;

    private Counter cacheHitCounter;
    private Counter cacheMissCounter;

    public UrlService(UrlRepository urlRepository, StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        cacheHitCounter = Counter.builder("cache.hits").register(meterRegistry);
        cacheMissCounter = Counter.builder("cache.misses").register(meterRegistry);
    }

    public String shorten(String longUrl) {
        Optional<Url> existing = urlRepository.findByLongUrl(longUrl);
        if (existing.isPresent()) {
            return existing.get().getShortUrl();
        }

        Url url = new Url();
        url.setLongUrl(longUrl);
        url.setShortUrl(""); // placeholder
        url = urlRepository.save(url);

        String shortUrl = encode(url.getId());
        url.setShortUrl(shortUrl);
        urlRepository.save(url);

        redisTemplate.opsForValue().set(REDIS_PREFIX + shortUrl, longUrl);

        return shortUrl;
    }

    public String resolve(String shortUrl) {
        String cached = redisTemplate.opsForValue().get(REDIS_PREFIX + shortUrl);
        if (cached != null) {
            cacheHitCounter.increment();
            return cached;
        }

        cacheMissCounter.increment();
        Optional<Url> url = urlRepository.findByShortUrl(shortUrl);
        if (url.isPresent()) {
            String longUrl = url.get().getLongUrl();
            redisTemplate.opsForValue().set(REDIS_PREFIX + shortUrl, longUrl);
            return longUrl;
        }

        return null;
    }

    private String encode(long id) {
        if (id == 0) return String.valueOf(BASE62.charAt(0));
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(BASE62.charAt((int) (id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }
}
