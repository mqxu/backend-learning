package top.mqxu.web.entity;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户实体
 *
 * @author mqxu
 */
@Data
public class User {
    private Long id;
    private String username;
    private Integer age;
    private String email;
    private LocalDate birthday;
    private String address;
}
