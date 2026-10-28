package top.mqxu.zhihu.service;

import top.mqxu.zhihu.entity.Special;

import java.util.List;

/**
 * @author mqxu
 * @date 2026/10/19
 * @description SpecialService
 **/
public interface SpecialService {
    /**
     * 获取所有数据
     *
     * @return List<Special>
     */
    List<Special> getAll();
}