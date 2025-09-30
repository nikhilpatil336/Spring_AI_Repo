package com.example.springAIDemo.utility;

import com.example.springAIDemo.Controller.ImageController;
import com.example.springAIDemo.model.SupportResistanceLevel;
import com.example.springAIDemo.model.TechnicalPattern;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Utility {

    private static Logger LOGGER = LoggerFactory.getLogger(Utility.class);

    public static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    // Formatter for outputting full date in ISO format
    public static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Formatter for outputting just month (ISO-like)
    public static final DateTimeFormatter ISO_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    public static List<String[]> loadCSV(String filePath) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            LOGGER.info("Reading CSV");
            return reader.readAll();
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    public static SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel, String fullText) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        TextReader textReader = new TextReader(fullText);
        List<Document> documents = List.of(new Document(fullText));
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = tokenTextSplitter.apply(documents);

        simpleVectorStore.add(splitDocuments);

        LOGGER.info("stroed simple vector is: " + simpleVectorStore.toString());

        return simpleVectorStore;
    }

    public static double parseDouble(String value) throws NumberFormatException {
        // Remove quotes and commas from the numeric string before parsing
        String cleaned = value.replace("\"", "").replace(",", "");
        return Double.parseDouble(cleaned);
    }

    public static List<SupportResistanceLevel> extractSupportResistance(List<String[]> csvRows) {
        double tolerance = 2.0;  // Price band to cluster levels
        List<SupportResistanceLevel> levels = new ArrayList<>();

        for (int i = 2; i < csvRows.size(); i++) {  // skip header
            String[] row = csvRows.get(i);
            try {
                double high = Utility.parseDouble(row[2]);
                double low = Utility.parseDouble(row[3]);
                String date = row[0];

                // Cluster resistance (high)
                SupportResistanceLevel resistanceLevel = findLevel(levels, high, tolerance, "Resistance");
                if (resistanceLevel == null) {
                    resistanceLevel = new SupportResistanceLevel();
                    resistanceLevel.priceLevel = high;
                    resistanceLevel.levelType = "Resistance";
                    levels.add(resistanceLevel);
                }
                resistanceLevel.touchedDates.add(date);

                // Cluster support (low)
                SupportResistanceLevel supportLevel = findLevel(levels, low, tolerance, "Support");
                if (supportLevel == null) {
                    supportLevel = new SupportResistanceLevel();
                    supportLevel.priceLevel = low;
                    supportLevel.levelType = "Support";
                    levels.add(supportLevel);
                }
                supportLevel.touchedDates.add(date);

            } catch (NumberFormatException e) {
                System.err.println("Skipping row " + i + " due to number parse error: " + e.getMessage());
            }
        }

        return levels;
    }

    private static SupportResistanceLevel findLevel(List<SupportResistanceLevel> levels, double price, double tolerance, String levelType) {
        for (SupportResistanceLevel lvl : levels) {
            if (lvl.levelType.equals(levelType) && Math.abs(lvl.priceLevel - price) <= tolerance) {
                // Optionally: update priceLevel to average or keep original
                return lvl;
            }
        }
        return null;
    }

    public static List<String> detectVolumeSpikes(List<String[]> csvRows) {
        List<String> spikeDates = new ArrayList<>();

        for (int i = 6; i < csvRows.size(); i++) {  // Start from 6 to ensure at least 5 previous rows
            try {
                double currentVol = Utility.parseDouble(csvRows.get(i)[8]);
                double avgPrevVol = 0;

                for (int j = i - 5; j < i; j++) {
                    avgPrevVol += Utility.parseDouble(csvRows.get(j)[8]);
                }
                avgPrevVol /= 5;

                if (currentVol > 1.5 * avgPrevVol) {
                    spikeDates.add(csvRows.get(i)[0]);
                }

            } catch (NumberFormatException e) {
                System.err.println("Skipping row " + i + " due to volume parse error: " + e.getMessage());
            }
        }

        return spikeDates;
    }

    public static void markPatternsVisibleInChart(List<TechnicalPattern> patterns, double visibleMinPrice, double visibleMaxPrice) {
        for (TechnicalPattern tp : patterns) {
            if (tp.priceLevel >= visibleMinPrice && tp.priceLevel <= visibleMaxPrice) {
                tp.visibleInChart = true;
            } else {
                tp.visibleInChart = false;
            }
        }
    }

    public static String buildCsvSummaryText(List<TechnicalPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return "No technical patterns detected.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Detected Technical Patterns Summary:\n");
        sb.append("------------------------------------------------\n");

        for (TechnicalPattern tp : patterns) {
            sb.append("Pattern: ").append(tp.patternName).append("\n");
            sb.append("Date: ").append(tp.date).append("\n");
            sb.append("Price Level: ").append(String.format("%.2f", tp.priceLevel)).append("\n");
            sb.append("Visible in Chart: ").append(tp.visibleInChart ? "Yes" : "No").append("\n");
            if (tp.notes != null && !tp.notes.isEmpty()) {
                sb.append("Notes: ").append(tp.notes).append("\n");
            }
            sb.append("------------------------------------------------\n");
        }

        return sb.toString();
    }

    public static double[] getVisiblePriceRangeFromCSV(List<String[]> csvRows) {
        double minPrice = Double.MAX_VALUE;
        double maxPrice = Double.MIN_VALUE;

        // Assuming your LOW price is in column index 3 and HIGH price is in column index 2
        // (based on your CSV format: Date, OPEN, HIGH, LOW, PREV. CLOSE, ltp, close, vwap, VOLUME)
        for (int i = 1; i < csvRows.size(); i++) { // skip header at 0
            String[] row = csvRows.get(i);

            // Remove commas and parse as double
            double low = Double.parseDouble(row[3].replace(",", ""));
            double high = Double.parseDouble(row[2].replace(",", ""));

            if (low < minPrice) minPrice = low;
            if (high > maxPrice) maxPrice = high;
        }

        return new double[]{minPrice, maxPrice};
    }

    public static List<TechnicalPattern> extractPatternsFromCSV(List<String[]> csvRows) {
        List<TechnicalPattern> patterns = new ArrayList<>();
        SimpleDateFormat inputDateFormat = new SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH);
        SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 1; i < csvRows.size(); i++) {  // Start at 1 to compare with previous row
            String[] today = csvRows.get(i);
            String[] yesterday = csvRows.get(i - 1);

            try {
                // Make sure arrays have expected length to avoid IndexOutOfBoundsException
                if (today.length < 7 || yesterday.length < 7) {
                    System.err.println("Skipping row " + i + " due to insufficient columns");
                    continue;
                }

                double todayOpen = Utility.parseDouble(today[1]);
                double todayClose = Utility.parseDouble(today[6]);
                double yesterdayOpen = Utility.parseDouble(yesterday[1]);
                double yesterdayClose = Utility.parseDouble(yesterday[6]);

                if (todayOpen < todayClose &&  // today bullish
                        yesterdayOpen > yesterdayClose && // yesterday bearish
                        todayOpen < yesterdayClose &&
                        todayClose > yesterdayOpen) {

                    TechnicalPattern tp = new TechnicalPattern();
                    tp.patternName = "Bullish Engulfing";

                    Date parsedDate = inputDateFormat.parse(today[0].trim());
                    tp.date = outputDateFormat.format(parsedDate);

                    tp.priceLevel = todayClose;
                    tp.visibleInChart = false;
                    tp.notes = "Strong bullish reversal candidate";

                    patterns.add(tp);
                }
            } catch (ParseException | NumberFormatException e) {
                System.err.println("Skipping row " + i + " due to parsing error: " + e.getMessage());
            }
        }

        return patterns;
    }

    public static Double movingAverage(List<Double> values, int period) {
        if (values.size() < 1) return null;
        int size = values.size();
        int start = Math.max(0, size - period);
        return values.subList(start, size).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // RSI calculation
    public static Double computeRSI(List<Double> closes, int period) {
        if (closes.size() <= period) return null;
        double gains = 0, losses = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) gains += change; else losses -= change;
        }
        double rs = (losses == 0) ? 100 : gains / losses;
        return 100 - (100 / (1 + rs));
    }

    // ATR calculation
    public static Double computeATR(List<Double> highs, List<Double> lows, List<Double> closes, int period) {
        if (highs.size() <= period) return null;
        List<Double> trs = new ArrayList<>();
        for (int i = 1; i < highs.size(); i++) {
            double tr = Math.max(highs.get(i) - lows.get(i),
                    Math.max(Math.abs(highs.get(i) - closes.get(i - 1)),
                            Math.abs(lows.get(i) - closes.get(i - 1))));
            trs.add(tr);
        }
        return movingAverage(trs, period);
    }

    // Bollinger Bands (upper/lower)
    public static Double[] computeBollinger(List<Double> closes, int period) {
        if (closes.size() < period) return new Double[]{null, null};
        int size = closes.size();
        List<Double> window = closes.subList(size - period, size);
        double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = window.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / period;
        double stdDev = Math.sqrt(variance);
        return new Double[]{mean + 2 * stdDev, mean - 2 * stdDev};
    }

    // Utility to round numbers
    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }


}
