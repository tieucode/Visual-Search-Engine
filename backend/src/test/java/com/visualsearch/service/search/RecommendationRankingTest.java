package com.visualsearch.service.search;

import com.visualsearch.client.QdrantSearchClient.ScoredImage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRankingTest {
    @Test
    void startsWithOneValidClickAndIgnoresMissingOrInvalidVectors() {
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID wrongDimensions = UUID.randomUUID();
        UUID zero = UUID.randomUUID();
        UUID nonFinite = UUID.randomUUID();

        var clusters = RecommendationRanking.cluster(List.of(first, missing, wrongDimensions, zero, nonFinite),
                Map.of(first, List.of(2f, 0f, 0f), wrongDimensions, List.of(1f, 2f),
                        zero, List.of(0f, 0f, 0f), nonFinite, List.of(1f, Float.NaN, 0f)), 3, 0.8);

        assertThat(clusters).hasSize(1);
        assertThat(clusters.getFirst().center()).containsExactly(1f, 0f, 0f);
        assertThat(clusters.getFirst().weight()).isEqualTo(1);
    }

    @Test
    void reservesAnEarlyResultForEachInterestAndDeduplicatesCandidates() {
        UUID animal = UUID.randomUUID();
        UUID anotherAnimal = UUID.randomUUID();
        UUID portrait = UUID.randomUUID();
        UUID landscape = UUID.randomUUID();
        List<UUID> recent = List.of(animal, anotherAnimal, portrait, landscape);
        var clusters = RecommendationRanking.cluster(recent, Map.of(
                animal, List.of(1f, 0f, 0f), anotherAnimal, List.of(2f, 0f, 0f),
                portrait, List.of(0f, 1f, 0f), landscape, List.of(0f, 0f, 1f)), 3, 0.8);
        assertThat(clusters).hasSize(3);
        assertThat(clusters.get(0).center()).containsExactly(1f, 0f, 0f);
        assertThat(clusters.get(1).center()).containsExactly(0f, 1f, 0f);
        assertThat(clusters.get(2).center()).containsExactly(0f, 0f, 1f);

        var animalsFirst = new ScoredImage(UUID.randomUUID(), 0.97);
        var animalsSecond = new ScoredImage(UUID.randomUUID(), 0.90);
        var peopleFirst = new ScoredImage(UUID.randomUUID(), 0.85);
        var sceneryFirst = new ScoredImage(UUID.randomUUID(), 0.82);
        var unavailable = new ScoredImage(UUID.randomUUID(), 0.92);
        var matches = List.of(List.of(new ScoredImage(animal, 1.0), animalsFirst, animalsSecond),
                List.of(animalsFirst, unavailable, peopleFirst), List.of(sceneryFirst));
        var available = Set.of(animal, animalsFirst.id(), animalsSecond.id(), peopleFirst.id(), sceneryFirst.id());

        assertThat(RecommendationRanking.interleave(clusters, matches, available, recent, 20))
                .containsExactly(animalsFirst, peopleFirst, sceneryFirst, animalsSecond);
    }

    @Test
    void keepsOnlySixMostRecentInterestsWhenSupportIsEqual() {
        List<UUID> history = new java.util.ArrayList<>();
        Map<UUID, List<Float>> points = new java.util.HashMap<>();
        for (int i = 0; i < 7; i++) {
            UUID id = UUID.randomUUID();
            var direction = new java.util.ArrayList<>(java.util.Collections.nCopies(7, 0f));
            direction.set(i, 1f);
            history.add(id);
            points.put(id, direction);
        }
        var clusters = RecommendationRanking.cluster(history, points, 7, 0.8);
        assertThat(clusters).hasSize(6);
        assertThat(clusters).allSatisfy(cluster -> assertThat(cluster.center()[6]).isZero());
    }
}
