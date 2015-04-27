package com.linkedin.thirdeye.dashboard.views;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkedin.thirdeye.dashboard.api.MetricTable;
import com.linkedin.thirdeye.dashboard.api.MetricTableRow;
import com.linkedin.thirdeye.dashboard.api.QueryResult;
import io.dropwizard.views.View;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

public class MetricViewTabular extends View {
  private static final TypeReference<List<String>> STRING_LIST_REF = new TypeReference<List<String>>(){};

  private final ObjectMapper objectMapper;
  private final QueryResult result;
  private final List<MetricTable> metricTables;
  private final long baselineOffsetMillis;
  private final long intraDayPeriod;

  public MetricViewTabular(ObjectMapper objectMapper,
                           QueryResult result,
                           long baselineOffsetMillis,
                           long intraDayPeriod) throws Exception {
    super("metric/table.ftl");
    this.objectMapper = objectMapper;
    this.result = result;
    this.baselineOffsetMillis = baselineOffsetMillis;
    this.intraDayPeriod = intraDayPeriod;
    this.metricTables = generateMetricTables();
  }

  public List<MetricTable> getMetricTables() {
    return metricTables;
  }

  public List<String> getMetricNames() {
    return result.getMetrics();
  }

  private Map<String, String> getDimensionValues(String dimensionKey) throws Exception {
    Map<String, String> valueMap = new TreeMap<>();
    List<String> dimensionNames = result.getDimensions();
    List<String> dimensionValues = objectMapper.readValue(dimensionKey.getBytes(), STRING_LIST_REF);

    for (int i = 0; i < dimensionNames.size(); i++) {
      valueMap.put(dimensionNames.get(i), dimensionValues.get(i));
    }

    return valueMap;
  }

  private List<MetricTable> generateMetricTables() throws Exception {
    List<MetricTable> tables = new ArrayList<>();

    for (Map.Entry<String, Map<String, Number[]>> entry : result.getData().entrySet()) {
      List<MetricTableRow> rows = new LinkedList<>();
      List<Long> times = getReverseSortedTimes(entry.getValue().keySet());

      long windowFilled = 0;
      int idx = 0;
      while (windowFilled < intraDayPeriod && idx < times.size() - 1) {
        long current = times.get(idx);
        long next = times.get(idx + 1);

        // n.b. this is inefficient, but prevents us from having to pass around aggregation granularity info
        long baseline = times.get(times.indexOf(current - baselineOffsetMillis) - 1);

        windowFilled += (current - next);
        idx++;

        Number[] currentData = entry.getValue().get(String.valueOf(current));
        Number[] baselineData = entry.getValue().get(String.valueOf(baseline));

        rows.add(0, new MetricTableRow(
            new DateTime(baseline).toDateTime(DateTimeZone.UTC), baselineData,
            new DateTime(current).toDateTime(DateTimeZone.UTC), currentData));
      }

      tables.add(new MetricTable(getDimensionValues(entry.getKey()), rows));
    }

    return tables;
  }

  private static List<Long> getReverseSortedTimes(Set<String> timeStrings) {
    List<Long> sortedTimes = new ArrayList<>();
    for (String timeString : timeStrings) {
      sortedTimes.add(Long.valueOf(timeString));
    }
    Collections.sort(sortedTimes, Collections.reverseOrder());
    return sortedTimes;
  }
}
