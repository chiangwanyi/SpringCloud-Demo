package com.jwy.scd.controller;

import com.jwy.scd.api.order.ProductApi;
import com.jwy.scd.api.order.dto.ProductDTO;
import com.jwy.scd.api.order.dto.ProductSaveDTO;
import com.jwy.scd.service.IProductService;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品接口实现：实现 service-order-api 中定义的 {@link ProductApi} 契约。
 * <p>HTTP 映射（/api/product/*）继承自接口上的 {@code @RequestMapping}。
 */
@RestController
public class ProductController implements ProductApi {

    private final IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
    }

    @Override
    public ProductDTO getProduct(Long id) {
        return productService.getProductDto(id);
    }

    @Override
    public List<ProductDTO> listProducts() {
        return productService.listProductDtos();
    }

    @Override
    public ProductDTO createProduct(ProductSaveDTO dto) {
        return productService.createProduct(dto);
    }

    @Override
    public ProductDTO updateProduct(Long id, ProductSaveDTO dto) {
        return productService.updateProduct(id, dto);
    }

    @Override
    public void deleteProduct(Long id) {
        productService.deleteProduct(id);
    }
}
