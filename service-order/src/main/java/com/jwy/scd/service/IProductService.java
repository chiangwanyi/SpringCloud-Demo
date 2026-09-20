package com.jwy.scd.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jwy.scd.api.order.dto.ProductDTO;
import com.jwy.scd.api.order.dto.ProductSaveDTO;
import com.jwy.scd.entity.BizProduct;

import java.util.List;

/**
 * 商品服务接口。
 */
public interface IProductService extends IService<BizProduct> {

    ProductDTO getProductDto(Long id);

    List<ProductDTO> listProductDtos();

    ProductDTO createProduct(ProductSaveDTO dto);

    ProductDTO updateProduct(Long id, ProductSaveDTO dto);

    boolean deleteProduct(Long id);
}
