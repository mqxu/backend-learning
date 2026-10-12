package top.mqxu.mybatis.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.entity.Product;
import top.mqxu.mybatis.service.ProductService;

import java.util.List;

/**
 * 商品接口，用来观察下单前后库存的变化
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 全部商品 */
    @GetMapping
    public List<Product> list() {
        return productService.listProducts();
    }

    /** 按主键查询，主要看 stock */
    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return productService.getById(id);
    }
}
