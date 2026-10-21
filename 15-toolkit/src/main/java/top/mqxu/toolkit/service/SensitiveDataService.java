package top.mqxu.toolkit.service;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.IdcardUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.symmetric.AES;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.CreateCustomerRequest;
import top.mqxu.toolkit.dto.CustomerView;
import top.mqxu.toolkit.support.Customer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感数据处理
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 手机号、身份证 AES 加密存储，密码 BCrypt 哈希，查询时按角色脱敏
 */
@Service
@RequiredArgsConstructor
public class SensitiveDataService {

    private final ToolkitProperties properties;

    private final Map<String, Customer> store = new ConcurrentHashMap<>();

    private AES aes;

    @PostConstruct
    void init() {
        String key = properties.getCrypto().getAesKey();
        if (StrUtil.isBlank(key) || key.getBytes(StandardCharsets.UTF_8).length != 16) {
            throw new IllegalStateException("toolkit.crypto.aes-key 必须是 16 位字符串");
        }
        aes = SecureUtil.aes(key.getBytes(StandardCharsets.UTF_8));
    }

    public CustomerView create(CreateCustomerRequest request) {
        if (StrUtil.isBlank(request.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "姓名不能为空");
        }
        if (!Validator.isMobile(request.phone())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式不正确");
        }
        if (!Validator.isCitizenId(request.idCard())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "身份证号不正确");
        }
        if (StrUtil.length(request.password()) < 6) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码至少 6 位");
        }

        Customer customer = new Customer();
        customer.setId(IdUtil.fastSimpleUUID());
        customer.setName(request.name());
        customer.setPhoneEncrypted(aes.encryptBase64(request.phone()));
        customer.setIdCardEncrypted(aes.encryptBase64(request.idCard()));
        customer.setPasswordHash(BCrypt.hashpw(request.password()));
        customer.setLevel(StrUtil.blankToDefault(request.level(), "normal"));
        customer.setCreatedAt(System.currentTimeMillis());

        store.put(customer.getId(), customer);
        return toView(customer, "admin");
    }

    public CustomerView get(String id, String role) {
        return toView(find(id), role);
    }

    /**
     * 直接看存储层的内容，教学演示用
     */
    public Map<String, Object> raw(String id) {
        Customer customer = find(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("phoneEncrypted", customer.getPhoneEncrypted());
        data.put("idCardEncrypted", customer.getIdCardEncrypted());
        data.put("passwordHash", customer.getPasswordHash());
        return data;
    }

    public boolean verifyPassword(String id, String rawPassword) {
        Customer customer = find(id);
        return BCrypt.checkpw(StrUtil.nullToEmpty(rawPassword), customer.getPasswordHash());
    }

    private Customer find(String id) {
        Customer customer = store.get(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customer;
    }

    private CustomerView toView(Customer customer, String role) {
        String phone = aes.decryptStr(customer.getPhoneEncrypted());
        String idCard = aes.decryptStr(customer.getIdCardEncrypted());

        int age = IdcardUtil.getAgeByIdCard(idCard);
        String gender = IdcardUtil.getGenderByIdCard(idCard) == 1 ? "男" : "女";

        boolean full = "admin".equalsIgnoreCase(role);
        return new CustomerView(
                customer.getId(),
                customer.getName(),
                full ? phone : DesensitizedUtil.mobilePhone(phone),
                full ? idCard : DesensitizedUtil.idCardNum(idCard, 6, 4),
                age,
                gender,
                customer.getLevel());
    }
}
