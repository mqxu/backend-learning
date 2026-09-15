package top.mqxu.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * @author mqxu
 * @date 2026/9/16
 * @description 案例：@ConfigurationProperties 类型安全的配置绑定
 * <p>
 * 相比 @Value，它可以一次性把一组同前缀的配置绑定到一个对象上，
 * 支持嵌套对象、List、Map 等复杂结构。
 **/
@Data
@Component
@ConfigurationProperties(prefix = "student")
public class StudentProperties {

    private String name;

    private Integer age;

    /** 简单 List：绑定 student.hobbies */
    private List<String> hobbies;

    /** Map：绑定 student.scores */
    private Map<String, Integer> scores;

    /** 嵌套对象：绑定 student.address */
    private Address address;

    /** List 嵌套对象：绑定 student.courses */
    private List<Course> courses;

    @Data
    public static class Address {
        private String province;
        private String city;
    }

    @Data
    public static class Course {
        private String name;
        private Integer credit;
    }
}