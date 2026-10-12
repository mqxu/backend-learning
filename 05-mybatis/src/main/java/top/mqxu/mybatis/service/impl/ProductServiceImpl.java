package top.mqxu.mybatis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import top.mqxu.mybatis.entity.Product;
import top.mqxu.mybatis.mapper.ProductMapper;
import top.mqxu.mybatis.service.ProductService;

import java.util.List;

/**
 * 商品服务实现
 *
 * @author mqxu
 */
@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public List<Product> listProducts() {
        return list(new LambdaQueryWrapper<Product>().orderByAsc(Product::getId));
    }
}
