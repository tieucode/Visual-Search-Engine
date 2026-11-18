package com.visualsearch.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "search_clicks", indexes = {
        @Index(name = "idx_search_click_user_image_time", columnList = "user_id,clicked_image_id,created_at"),
        @Index(name = "idx_search_click_time", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchClick extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_history_id")
    private SearchHistory searchHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_image_id", nullable = false)
    private Image clickedImage;
}
