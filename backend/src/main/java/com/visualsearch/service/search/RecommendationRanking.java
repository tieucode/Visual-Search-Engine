package com.visualsearch.service.search;

import com.visualsearch.client.QdrantSearchClient.ScoredImage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RecommendationRanking {
    private static final int MAX_CLUSTERS = 6;
    private static final double CLICK_HALF_LIFE = 30.0;

    private RecommendationRanking() {}

    static List<Interest> cluster(List<UUID> recent, Map<UUID, List<Float>> vectors,
                                  int dimensions, double threshold) {
        List<Cluster> clusters = new ArrayList<>();
        for (int rank = 0; rank < recent.size(); rank++) {
            float[] vector = unitVector(vectors.get(recent.get(rank)), dimensions);
            if (vector == null) continue;

            Cluster closest = null;
            double best = threshold;
            for (Cluster cluster : clusters) {
                double score = dot(vector, cluster.center);
                if (score >= threshold && (closest == null || score > best)) {
                    closest = cluster;
                    best = score;
                }
            }
            if (closest == null) {
                closest = new Cluster(dimensions, rank);
                clusters.add(closest);
            }
            closest.add(vector, Math.pow(2, -rank / CLICK_HALF_LIFE));
        }
        return clusters.stream()
                .sorted(Comparator.comparingDouble((Cluster cluster) -> cluster.weight).reversed()
                        .thenComparingInt(cluster -> cluster.firstRank))
                .limit(MAX_CLUSTERS)
                .map(cluster -> new Interest(cluster.center, cluster.weight))
                .toList();
    }

    static List<ScoredImage> interleave(List<Interest> interests, List<List<ScoredImage>> matches,
                                        Set<UUID> available, List<UUID> recent, int limit) {
        List<Candidates> groups = new ArrayList<>(interests.size());
        for (int i = 0; i < interests.size(); i++) {
            var points = matches.get(i).stream().filter(hit -> available.contains(hit.id())).toList();
            groups.add(new Candidates(points, interests.get(i).weight()));
        }

        List<ScoredImage> results = new ArrayList<>(limit);
        Set<UUID> seen = new HashSet<>(recent);
        for (Candidates group : groups) {
            if (results.size() == limit) break;
            group.take(seen, results);
        }
        while (results.size() < limit) {
            Candidates next = groups.stream().filter(group -> !group.points.isEmpty())
                    .min(Comparator.comparingDouble(Candidates::position)).orElse(null);
            if (next == null) break;
            next.take(seen, results);
        }
        return results;
    }

    private static float[] unitVector(List<Float> values, int dimensions) {
        if (values == null || values.size() != dimensions) return null;
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            Float value = values.get(i);
            if (value == null || !Float.isFinite(value)) return null;
            vector[i] = value;
        }
        return dot(vector, vector) == 0 ? null : normalize(vector);
    }

    private static float[] normalize(float[] vector) {
        double norm = Math.sqrt(dot(vector, vector));
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }

    private static double dot(float[] left, float[] right) {
        double result = 0;
        for (int i = 0; i < left.length; i++) {
            result += (double) left[i] * right[i];
        }
        return result;
    }

    record Interest(float[] center, double weight) {}

    private static final class Cluster {
        private final float[] sum;
        private final int firstRank;
        private float[] center;
        private double weight;

        private Cluster(int dimensions, int firstRank) {
            sum = new float[dimensions];
            this.firstRank = firstRank;
        }

        private void add(float[] vector, double clickWeight) {
            for (int i = 0; i < sum.length; i++) {
                sum[i] += vector[i];
            }
            center = normalize(sum);
            weight += clickWeight;
        }
    }

    private static final class Candidates {
        private final Deque<ScoredImage> points;
        private final double weight;
        private int taken;

        private Candidates(List<ScoredImage> points, double weight) {
            this.points = new ArrayDeque<>(points);
            this.weight = Math.sqrt(weight);
        }

        private double position() {
            return taken / weight;
        }

        private void take(Set<UUID> seen, List<ScoredImage> results) {
            while (!points.isEmpty()) {
                ScoredImage hit = points.removeFirst();
                if (seen.add(hit.id())) {
                    results.add(hit);
                    taken++;
                    return;
                }
            }
        }
    }
}
