package top.mqxu.mybatis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 连接池演示接口，只在 pool1 profile 下注册
 *
 * <p>{@code SELECT SLEEP(n)} 会让这条查询在数据库端一直执行 n 秒，
 * 期间连接一直被它占着。配合 {@code maximum-pool-size=1}，
 * 就能稳定复现“池里唯一的连接被占住，后来的请求取不到连接”。</p>
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/diagnostics")
@Profile("pool1")
@RequiredArgsConstructor
public class DiagnosticsController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 占住一个连接若干秒
     *
     * @param seconds 睡眠秒数，默认 3
     */
    @GetMapping("/slow-query")
    public Map<String, Object> slowQuery(@RequestParam(defaultValue = "3") int seconds) {
        return jdbcTemplate.queryForMap("SELECT SLEEP(?) AS slept", seconds);
    }
}
