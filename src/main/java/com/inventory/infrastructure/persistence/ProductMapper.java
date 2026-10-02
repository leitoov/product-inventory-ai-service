package com.inventory.infrastructure.persistence;

import com.inventory.domain.model.Product;
import org.mapstruct.*;

/**
 * MapStruct mapper between domain {@link Product} and JPA {@link ProductEntity}.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductMapper {

    @Mapping(target = "id",            source = "id")
    @Mapping(target = "sku",           source = "sku")
    @Mapping(target = "name",          source = "name")
    @Mapping(target = "description",   source = "description")
    @Mapping(target = "imageUrl",      source = "imageUrl")
    @Mapping(target = "price",         source = "price")
    @Mapping(target = "stock",         source = "stock")
    @Mapping(target = "lastUpdatedAt", source = "lastUpdatedAt")
    @Mapping(target = "createdAt",     source = "createdAt")
    Product toDomain(ProductEntity entity);

    @Mapping(target = "lastUpdatedAt", ignore = true)  // managed by @UpdateTimestamp
    @Mapping(target = "createdAt",     ignore = true)  // managed by @CreationTimestamp
    ProductEntity toEntity(Product domain);
}
