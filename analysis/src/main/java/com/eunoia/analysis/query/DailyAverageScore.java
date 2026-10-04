package com.eunoia.analysis.query;

import java.time.LocalDate;

public record DailyAverageScore(LocalDate date, double averageScore) {
}
