package com.jwy.scd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jwy.scd.api.order.dto.ProductDTO;
import com.jwy.scd.api.order.dto.ProductSaveDTO;
import com.jwy.scd.entity.BizProduct;
import com.jwy.scd.exception.OrderException;
import com.jwy.scd.mapper.BizProductMapper;
import com.jwy.scd.service.IProductService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl extends ServiceImpl<BizProductMapper, BizProduct> implements IProductService {

    @Override
    public ProductDTO getProductDto(Long id) {
        return toDto(getById(id));
    }

    @Override
    public List<ProductDTO> listProductDtos() {
        return list().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public ProductDTO createProduct(ProductSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw OrderException.badRequest("商品名称不能为空");
        }
        BizProduct product = new BizProduct();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock() == null ? 0 : dto.getStock());
        product.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        save(product);
        return toDto(product);
    }

    @Override
    public ProductDTO updateProduct(Long id, ProductSaveDTO dto) {
        BizProduct product = getById(id);
        if (product == null) {
            throw OrderException.notFound("商品不存在：id=" + id);
        }
        if (StringUtils.hasText(dto.getName())) {
            product.setName(dto.getName());
        }
        if (dto.getPrice() != null) {
            product.setPrice(dto.getPrice());
        }
        if (dto.getStock() != null) {
            product.setStock(dto.getStock());
        }
        if (dto.getStatus() != null) {
            product.setStatus(dto.getStatus());
        }
        updateById(product);
        return toDto(product);
    }

    @Override
    public boolean deleteProduct(Long id) {
        return removeById(id);
    }

    /** 实体 -> 对外 DTO */
    private ProductDTO toDto(BizProduct product) {
        if (product == null) {
            return null;
        }
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setStatus(product.getStatus());
        dto.setCreateTime(product.getCreateTime());
        return dto;
    }
}
