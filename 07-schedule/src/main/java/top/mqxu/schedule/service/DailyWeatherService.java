package top.mqxu.schedule.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 每日天气早报：定时取天气、拼邮件、发出去。
 * 三个外部依赖（天气接口、SMTP、收件人）都做了可关闭处理，本地不配也能完整跑通流程。
 *
 * @author mqxu
 * @date 2026/10/21
 * @description DailyWeatherService
 **/
@Slf4j
@Service
public class DailyWeatherService {

    private final OkHttpClient okHttpClient = new OkHttpClient();

    @Resource
    private JavaMailSender mailSender;

    @Value("${weather.api.key:}")
    private String weatherApiKey;

    @Value("${weather.api.city}")
    private String cityId;

    @Value("${weather.api.url}")
    private String weatherApiUrl;

    @Value("${weather.mock:true}")
    private boolean weatherMock;

    @Value("${email.mock:true}")
    private boolean emailMock;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${email.recipients}")
    private String[] toEmails;

    /**
     * 每天 9:22 执行。cron 写在配置里，改时间不用改代码。
     */
    @Scheduled(cron = "${weather.cron:0 22 9 * * ?}")
    public void sendDailyWeather() {
        boolean ok = sendWeatherEmail();
        log.info("【天气早报】定时任务执行完成，结果 {}", ok ? "成功" : "失败");
    }

    /**
     * 发送天气邮件（定时任务与手动触发共用）
     *
     * @return 是否发送成功
     */
    public boolean sendWeatherEmail() {
        try {
            JSONObject todayWeather = getTodayWeather();
            if (todayWeather == null) {
                log.error("【天气早报】获取天气数据失败");
                return false;
            }
            String date = todayWeather.getString("fxDate");
            String tempMax = todayWeather.getString("tempMax");
            String tempMin = todayWeather.getString("tempMin");
            String textDay = todayWeather.getString("textDay");
            String windDirDay = todayWeather.getString("windDirDay");
            String windScaleDay = todayWeather.getString("windScaleDay");

            String subject = String.format("【每日天气早报】%s 南京天气",
                    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            String content = String.format("""
                    日期：%s
                    天气：%s
                    温度：%s℃ ~ %s℃
                    风向：%s
                    风力：%s 级
                    提示：出门请根据天气增减衣物，注意交通安全。""",
                    date, textDay, tempMin, tempMax, windDirDay, windScaleDay);

            sendEmail(subject, content);
            log.info("【天气早报】邮件处理完成，收件人：{}", String.join(",", toEmails));
            return true;
        } catch (Exception e) {
            log.error("【天气早报】执行失败", e);
            return false;
        }
    }

    /**
     * 获取当天天气数据（daily 数组第 0 个元素 = 今天）
     */
    public JSONObject getTodayWeather() throws IOException {
        String weatherResponse = getWeatherData();
        if (weatherResponse == null) {
            return null;
        }
        JSONObject weatherJson = JSONObject.parseObject(weatherResponse);
        JSONArray daily = weatherJson.getJSONArray("daily");
        return daily == null || daily.isEmpty() ? null : daily.getJSONObject(0);
    }

    /**
     * 调用和风天气 API 获取 3 天预报。mock 模式下返回内置数据。
     */
    private String getWeatherData() throws IOException {
        if (weatherMock) {
            log.info("天气接口处于模拟模式，返回内置数据");
            return """
                    {"code":"200","daily":[
                      {"fxDate":"%s","tempMax":"21","tempMin":"13","textDay":"多云",
                       "windDirDay":"东北风","windScaleDay":"1-3"}
                    ]}""".formatted(LocalDate.now());
        }

        String requestUrl = String.format("%s?location=%s&key=%s", weatherApiUrl, cityId, weatherApiKey);
        Request request = new Request.Builder().url(requestUrl).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            log.warn("天气接口返回异常状态码：{}", response.code());
            return null;
        }
    }

    /**
     * 发送邮件。mock 模式下只打日志，不连 SMTP。
     */
    private void sendEmail(String subject, String content) {
        if (emailMock) {
            log.info("邮件通道处于模拟模式\n主题：{}\n{}", subject, content);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmails);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
}
