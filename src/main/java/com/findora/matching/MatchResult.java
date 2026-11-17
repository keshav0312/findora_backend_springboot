package com.findora.matching;

import com.findora.model.MatchBreakdown;

/** Similarity score (0-100) plus the per-field breakdown that explains it. */
public record MatchResult(int score, MatchBreakdown breakdown) {}
