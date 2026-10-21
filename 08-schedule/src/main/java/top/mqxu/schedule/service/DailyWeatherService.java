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

    // 从配置文件注入参数
    @Value("${weather.api.key}")
    private String weatherApiKey;

    @Value("${weather.api.city}")
    private String cityId;

    @Value("${weather.api.url}")
    private String weatherApiUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.recipients}")
    private String[] toEmails;


    /**
     * 定时任务，每天9:20执行
     */
    @Scheduled(cron = "0 22 9 * * ?")
    public void sendDailyWeather() {
        sendWeatherEmail();
    }

    /**
     * 发送天气邮件（定时任务与手动触发共用）
     *
     * @return 是否发送成功
     */
    public boolean sendWeatherEmail() {
        try {
            // 1. 获取当天天气
            JSONObject todayWeather = getTodayWeather();
            if (todayWeather == null) {
                log.error("【天气早报】获取天气数据失败");
                return false;
            }
            // 2. 提取天气相关信息
            String date = todayWeather.getString("fxDate");           // 日期
            String tempMax = todayWeather.getString("tempMax");       // 最高温
            String tempMin = todayWeather.getString("tempMin");       // 最低温
            String textDay = todayWeather.getString("textDay");       // 白天天气（如“晴”）
            String windDirDay = todayWeather.getString("windDirDay"); // 白天风向
            String windScaleDay = todayWeather.getString("windScaleDay"); // 白天风力

            // 3. 构造邮件内容
            String subject = String.format("【每日天气早报】%s 南京天气", LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
            String content = String.format("""
                    📅 日期：%s
                    🌤️ 天气：%s
                    🌡️ 温度：%s℃ ~ %s℃
                    💨 风向：%s
                    🌬️ 风力：%s级
                    💡 提示：出门请根据天气增减衣物，注意交通安全！""", date, textDay, tempMin, tempMax, windDirDay, windScaleDay);
            // 4. 发送邮件
            sendEmail(subject, content);
            log.info("【天气早报】邮件发送成功，收件人：{}", String.join(",", toEmails));
            return true;
        } catch (Exception e) {
            log.error("【天气早报】执行失败", e);
            return false;
        }
    }

    /**
     * 获取当天天气数据（daily 数组第 0 个元素 = 今天）
     *
     * @return 当天天气 JSON，获取失败返回 null
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
     * 调用和风天气API,获取3天天气预报
     *
     * @return API响应（JSON格式）
     */
    private String getWeatherData() throws IOException {
        // 构造API请求URL（含城市ID和API密钥）
        String requestUrl = String.format("%s?location=%s&key=%s", weatherApiUrl, cityId, weatherApiKey);
        Request request = new Request.Builder().url(requestUrl).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
            return null;
        }
    }

    /**
     * 发送邮件方法
     *
     * @param subject 邮件主题
     * @param content 邮件内容
     */
    private void sendEmail(String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmails);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }
}