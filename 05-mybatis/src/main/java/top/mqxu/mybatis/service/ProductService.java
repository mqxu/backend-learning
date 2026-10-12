package top.mqxu.mybatis.service;

import com.baomidou.mybatisplus.spring.service.IService;
import top.mqxu.mybatis.entity.Product;

import java.util.List;

/**
 * 商品服务，只提供查询，商品的增删在挑战练习里不涉及
 *
 * @author mqxu
 */
public interface ProductService extends IService<Product> {

    /** 查询全部商品，按 id 升序 */
    List<Product> listProducts();
}
