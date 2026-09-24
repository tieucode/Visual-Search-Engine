package com.visualsearch.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "search_clicks")
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
    @JoinColumn(name = "search_history_id", nullable = false)
    private SearchHistory searchHistory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clicked_image_id", nullable = false)
    private Image clickedImage;
}
