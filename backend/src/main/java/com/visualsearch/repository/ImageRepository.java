package com.visualsearch.repository;

import com.visualsearch.entity.Image;
import com.visualsearch.enums.ImageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findByIdInAndType(List<UUID> ids, ImageType type);
}
