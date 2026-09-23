package com.visualsearch.entity;

import com.visualsearch.enums.ImageFormat;
import com.visualsearch.enums.ImageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "images")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Image extends BaseEntity {

    @Column(name = "path", length = 500, nullable = false)
    private String path;

    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", length = 20, nullable = false)
    private ImageFormat fileFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ImageType type;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
