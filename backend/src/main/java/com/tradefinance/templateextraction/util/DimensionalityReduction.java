package com.tradefinance.templateextraction.util;

import java.util.Arrays;

/**
 * Simple PCA implementation for dimensionality reduction
 */
public class DimensionalityReduction {

    /**
     * Perform PCA to reduce embeddings to 2D
     * Simplified approach: project onto first 2 principal components
     */
    public static double[][] reduce2D(float[][] embeddings) {
        int numSamples = embeddings.length;
        int numFeatures = embeddings[0].length;

        // Convert to double and calculate mean
        double[][] data = new double[numSamples][numFeatures];
        double[] means = new double[numFeatures];

        for (int i = 0; i < numSamples; i++) {
            for (int j = 0; j < numFeatures; j++) {
                data[i][j] = embeddings[i][j];
                means[j] += data[i][j];
            }
        }

        for (int j = 0; j < numFeatures; j++) {
            means[j] /= numSamples;
        }

        // Center the data
        for (int i = 0; i < numSamples; i++) {
            for (int j = 0; j < numFeatures; j++) {
                data[i][j] -= means[j];
            }
        }

        // Simplified PCA: Use first 2 dimensions with highest variance
        // Calculate variance for each dimension
        double[] variances = new double[numFeatures];
        for (int j = 0; j < numFeatures; j++) {
            double sumSquared = 0;
            for (int i = 0; i < numSamples; i++) {
                sumSquared += data[i][j] * data[i][j];
            }
            variances[j] = sumSquared / numSamples;
        }

        // Find indices of top 2 dimensions by variance
        int[] topIndices = findTopTwoIndices(variances);

        // Project data onto these 2 dimensions
        double[][] projected = new double[numSamples][2];
        for (int i = 0; i < numSamples; i++) {
            projected[i][0] = data[i][topIndices[0]];
            projected[i][1] = data[i][topIndices[1]];
        }

        // Normalize to 0-100 range for visualization
        return normalizeToRange(projected, 0, 100);
    }

    private static int[] findTopTwoIndices(double[] values) {
        int first = 0, second = 1;

        if (values[1] > values[0]) {
            first = 1;
            second = 0;
        }

        for (int i = 2; i < values.length; i++) {
            if (values[i] > values[first]) {
                second = first;
                first = i;
            } else if (values[i] > values[second]) {
                second = i;
            }
        }

        return new int[]{first, second};
    }

    private static double[][] normalizeToRange(double[][] data, double min, double max) {
        int numSamples = data.length;

        // Find min and max for each dimension
        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;

        for (int i = 0; i < numSamples; i++) {
            minX = Math.min(minX, data[i][0]);
            maxX = Math.max(maxX, data[i][0]);
            minY = Math.min(minY, data[i][1]);
            maxY = Math.max(maxY, data[i][1]);
        }

        // Normalize
        double[][] normalized = new double[numSamples][2];
        for (int i = 0; i < numSamples; i++) {
            normalized[i][0] = (data[i][0] - minX) / (maxX - minX) * (max - min) + min;
            normalized[i][1] = (data[i][1] - minY) / (maxY - minY) * (max - min) + min;
        }

        return normalized;
    }
}
