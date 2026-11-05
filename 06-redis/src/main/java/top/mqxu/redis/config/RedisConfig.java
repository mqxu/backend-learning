package top.mqxu.redis.config;

import lombok.RequiredArgsConstructor;
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

/**
 * Redis 相关的两个 bean：RedisTemplate 负责手动读写，CacheManager 负责注解缓存。
 *
 * @author mqxu
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    /** 缓存 key 的统一前缀，与 application.yml 里的 spring.cache.redis.key-prefix 保持一致 */
    private static final String CACHE_PREFIX = "cache:";

    private final DemoProperties properties;

    /**
     * 缓存值统一用 JSON 序列化，并写入类型信息，读回来能直接还原成原来的对象。
     */
    private GenericJacksonJsonRedisSerializer jsonSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("top.mqxu.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.math.")
                        .build())
                .enableSpringCacheNullValueSupport()
                .customize(builder -> builder.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS))
                .build();
    }

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
     * 注解缓存用的 CacheManager。
     *
     * <p>不定义这个 bean 时，Boot 会自己建一个，值用 JDK 序列化：实体类没实现 Serializable 直接抛错，
     * 就算实现了，存进去也是二进制，redis-cli 里读不出来。</p>
     *
     * <p>另外 Spring Data Redis 4 把缓存写操作默认改成延迟执行，写完立刻读可能读不到，
     * 演示场景下这种"看不见"的状态很难排查，所以这里显式要求立即写入。</p>
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // 过期时间，方法与区域上没单独指定时用它
                .entryTtl(properties.getCache().getTtl())
                // 得到 cache:goods::4 这样的 key，冒号分级便于按前缀统计与清理
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
