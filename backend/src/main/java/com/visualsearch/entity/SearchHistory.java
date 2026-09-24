package com.visualsearch.entity;

import com.visualsearch.enums.SearchType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "search_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "search_type", length = 30, nullable = false)
    private SearchType searchType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "query_image_id")
    private Image queryImage;

    @Column(name = "query_text", columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "execution_duration_ms")
    private Integer executionDurationMs;
}
