package com.visualsearch.repository;

import com.visualsearch.entity.Image;
import com.visualsearch.enums.ImageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, UUID> {

    Page<Image> findByUploadedByIdOrderByCreatedAtDesc(
            UUID userId,
            Pageable pageable
    );

    List<Image> findByIdInAndType(List<UUID> ids, ImageType type);

    @Query("""
            select i from Image i
            where i.id in :ids and i.type = com.visualsearch.enums.ImageType.UPLOAD
              and (i.mediaKind = com.visualsearch.enums.MediaKind.IMAGE or exists (
                  select ix.id from ImageIndex ix
                  where ix.image.id = i.id and ix.status = com.visualsearch.enums.IndexStatus.SUCCESS
              ))
            """)
    List<Image> findSearchableMedia(List<UUID> ids);
}
