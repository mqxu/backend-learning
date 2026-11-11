package com.campus.activity.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

/**
 * Redis 配置：手动读写用 RedisTemplate，注解缓存用 RedisCacheManager。
 *
 * <p>项目使用 Spring Boot 4 / Jackson 3（{@code tools.jackson}），
 * 序列化器统一用 {@link GenericJacksonJsonRedisSerializer}：值以 JSON 存入 Redis，
 * 并带上类型信息，读回来能直接还原成原来的 POJO，redis-cli 里也能直接查看。</p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /** 注解缓存的统一前缀，与 application.yml 中 spring.cache.redis.key-prefix 保持一致 */
    private static final String CACHE_PREFIX = "campus:cache:";

    /** 注解缓存默认存活时间，分类、热门榜这类读多写少的数据 10 分钟足够 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * 缓存值统一走 JSON 序列化。
     *
     * <p>开启默认类型信息时不能放开所有类型（有反序列化漏洞风险），
     * 这里只允许本项目实体/VO 与 JDK 常用集合、包装类型。</p>
     */
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.campus.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build())
                // 允许缓存 null（application.yml 中 cache-null-values: true 的语义）
                .enableSpringCacheNullValueSupport()
                // 时间按可读格式写入，而非时间戳
                .customize(builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .build();
    }

    /**
     * 通用 RedisTemplate：String key + JSON value。
     *
     * <p>短信验证码等场景直接用 StringRedisTemplate 即可，
     * 这个 Bean 留给需要存 POJO 的地方使用。</p>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = jsonSerializer();

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        return template;
    }

    /**
     * 注解缓存（@Cacheable / @CacheEvict）使用的 CacheManager。
     *
     * <p>不定义这个 Bean 时 Boot 会自动建一个，值走 JDK 序列化，
     * 实体类没有实现 Serializable 会直接报错，存进去的二进制也无法排查。
     * 这里显式指定 key 前缀与 TTL，并开启立即写入：
     * Spring Data Redis 4 默认延迟写缓存，写完立刻读可能读不到，演示时很难排查。</p>
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                // 得到 campus:cache:categories::all 这样的 key，冒号分级便于按前缀统计与清理
                .computePrefixWith(name -> CACHE_PREFIX + name + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));

        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory,
                configurer -> configurer.immediateWrites().collectStatistics());

        return RedisCacheManager.builder(writer)
                .cacheDefaults(config)
                .build();
    }
}
