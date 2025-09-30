package com.example.springAIDemo.Controller;

import com.example.springAIDemo.model.*;
import com.example.springAIDemo.new_redis_rag.EmbeddingServiceNew;
import com.example.springAIDemo.utility.Utility;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.round;

@RestController
@RequestMapping("/image")
public class ImageController {

    private Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private ChatClient ollamaChatClient;
    private ChatClient memoryChatClient;

    private SimpleVectorStore store = null;

    @Autowired
    public VectorDB vectorDB;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingServiceNew embeddingServiceNew;

    @Value("classpath:/images/reliance_3_month_chart_JanToMar.PNG")
    Resource sampleImage;

    public ImageController(ChatClient.Builder builder, ChatMemory chatMemory, OllamaChatModel ollamaChatModel) {
        this.ollamaChatClient = ChatClient.create(ollamaChatModel);
        this.memoryChatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    @GetMapping("/memory/analyse")
    public ResponseEntity<?> imageToTextInMemory() {
        var curr_price = "1248.70";
        var start_date = "31st December 2024";
        var last_date = "03rd April 2025";

        String systemInstruction = """
                You are a professional technical stock market analyst with deep expertise in candlestick chart interpretation and price action analysis.
                            
                You are analyzing a daily stock price chart image with the following characteristics:
                            
                Chart Metadata:
                - Date range: From %s to %s
                - Current price: %s INR (visible on the chart)
                - Chart interval: Daily candlesticks (1 candle = 1 trading day)
                - Horizontal blue lines are only visual reference aids; do not treat them as indicators.
                            
                Visual Reference:
                - The price scale is located on the right edge of the chart.
                - The date/time axis is located at the bottom.
                - Two horizontal blue lines mark reference price levels — use these to interpret scale, candle size, and relative price movement.
                - The blue-highlighted prices on the scale correspond to these reference lines.
                            
                Your Tasks:
                1. Perform technical analysis using the **visual chart image only**.
                2. Identify and interpret:
                   - Price trends (uptrend, downtrend, sideways)
                   - Key support and resistance levels
                   - Notable candlestick patterns (hammer, doji, engulfing, shooting star, etc.)
                   - Gaps, wicks, and candle body-to-wick ratio
                   - Volatility and price compression/expansion zones
                3. Use the current price (%s INR) as a reference anchor and evaluate its relation to recent highs/lows and trend direction.
                4. When referring to:
                   - A **candlestick pattern**, specify the **exact date or date range** it occurred (based on visible x-axis).
                   - A **support/resistance zone**, indicate the **dates where the price reacted** to those levels.
                   - A **breakout, gap, or reversal**, always mention the **specific day(s)**.
                5. Avoid using indicators like RSI or MACD unless they're visually inferable.
                6. Keep the output professional, insightful, and based purely on visual information from the image.
                            
                Output Structure:
                - **Trend Summary** (mention date range of trend)
                - **Support & Resistance Levels** (with dates of reaction)
                - **Detected Candlestick Patterns** (with dates and explanation)
                - **Price Action Insights** (with date-based references)
                - **Final Trading Outlook** (bullish / bearish / neutral) based on the visual chart
                            
                Be accurate, and always mention the **date(s)** tied to any price action or pattern when visible.
                """.formatted(start_date, last_date, curr_price, curr_price);

        return ResponseEntity.ok(memoryChatClient.prompt()
                .user(u -> {
                    u.text("Analyze this daily candlestick chart image and store relevant technical insights in memory for future Q&A.");
                    u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);
                })
                .system(systemInstruction)
                .call()
                .content());
    }

    @PostMapping("/memory/ask")
    public ResponseEntity<?> imageToTextAsk(@RequestBody String input) {
        String systemPrompt = """
                You are a professional technical stock market analyst.

                You have already performed prior analysis on:
                - A candlestick chart image (daily interval)
                - OHLC-based CSV price data (including volume)

                This prior analysis is stored in memory. Now, you're answering questions strictly using that stored analysis. 
                Do **not** re-analyze any chart or CSV data again.

                Answering Rules:
                - Use only insights previously stored from:
                    - Chart image (visual patterns, trendlines, support/resistance)
                    - CSV data (OHLC, volume, gaps, levels, dates)
                - Always include accurate data points in your answer:
                    - Dates of relevant candles or events (e.g., “Hammer on 14th Feb 2025”)
                    - Price levels involved (e.g., “Support at ₹1220”, “Volume spike at ₹1265”)
                    - Pattern type or volume behavior (e.g., “Bearish engulfing”, “Volume divergence”)

                Reasoning Requirements:
                - Every recommendation (buy, sell, wait) must be justified by:
                    - A specific chart pattern and/or price action from the image
                    - Or numerical evidence (e.g., breakout, gap, moving range, volume spike) from the CSV
                - Avoid generic responses. Make your answers precise and actionable for real-world trading decisions.

                Output Format (when applicable):
                - Direct Answer: Your main response
                - Context/Evidence:
                    - For chart patterns → pattern name, date, visual location
                    - For CSV patterns → price levels, volume change, gap, range
                - Final Insight or Recommendation: Clearly state your view, with reasons

                Clarify if:
                - The question is ambiguous
                - The memory lacks necessary information to give a confident answer

                Do NOT:
                - Re-analyze charts or CSV
                - Guess or assume beyond stored memory
                - Provide vague advice like “the trend is bullish” without support

                Stay concise, technically sound, and focused on the context previously stored.
                """;

        return ResponseEntity.ok(
                memoryChatClient.prompt()
                        .user(input)
                        .system(systemPrompt)
                        .call()
                        .content()
        );
    }

    @PostMapping("/csv/analyze")
    public ResponseEntity<String> analyzeCsvFromPath(@RequestBody FilePathRequest request) {
        String filePath = request.getPath();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> rows = reader.readAll();

            if (rows.size() < 2) {
                return ResponseEntity.badRequest().body("CSV must contain at least one row of data.");
            }

            // Extract header and first 50 rows (LLMs don’t handle massive tables well)
            String[] headers = rows.get(0);
            List<String[]> dataRows = rows.subList(1, Math.min(rows.size(), 50));

            // Convert to text block
            String tableData = dataRows.stream()
                    .map(row -> String.join(" | ", row))
                    .collect(Collectors.joining("\n"));

            String headerLine = String.join(" | ", headers);
            String csvText = """
                    Here is stock price data (Daily):

                    %s
                    %s

                    Please analyze this data from a technical analysis point of view.
                    Include support/resistance, trend direction, patterns (e.g., bullish/bearish engulfing, hammer, etc.), and volume activity insights.
                    """.formatted(headerLine, tableData);

            var system = """
                    You are a technical stock market analyst.
                    Analyze the provided stock price data.
                    Include observations on trends, support/resistance levels, candlestick patterns, and volume changes.
                    Make your analysis readable and insightful.
                    """;

            LOGGER.info("Reading of the CSV file is done, now sending it to LLM.");

            // Call LLM
            String response = memoryChatClient.prompt()
                    .user(csvText)
                    .system(system)
                    .call()
                    .content();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing CSV: " + e.getMessage());
        }
    }

    @PostMapping("/csv/v1/analyze")
    public ResponseEntity<String> analyzeCsvFromPath_v1(@RequestBody FilePathRequest request) {
        String filePath = request.getPath();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> rows = reader.readAll();

            if (rows.size() < 2) {
                return ResponseEntity.badRequest().body("CSV must contain at least one row of data.");
            }

            String[] headers = rows.get(0);
            int maxRows = 70;
            List<String[]> dataRows = rows.subList(1, Math.min(rows.size(), maxRows + 1));

            // Identify column indices
            int dateIndex = 0;
            int openIndex = -1, highIndex = -1, lowIndex = -1, closeIndex = -1, volumeIndex = -1;

            for (int i = 0; i < headers.length; i++) {
                String h = headers[i].toLowerCase();
                if (h.contains("date")) dateIndex = i;
                else if (h.contains("open")) openIndex = i;
                else if (h.contains("high")) highIndex = i;
                else if (h.contains("low")) lowIndex = i;
                else if (h.contains("close") && closeIndex == -1) closeIndex = i;
                else if (h.contains("volume")) volumeIndex = i;
            }

            if (openIndex == -1 || highIndex == -1 || lowIndex == -1 || closeIndex == -1 || volumeIndex == -1) {
                return ResponseEntity.badRequest().body("CSV must include columns: OPEN, HIGH, LOW, CLOSE, VOLUME");
            }

            // Clean headers
            String cleanedHeader = Arrays.stream(headers)
                    .map(String::trim)
                    .collect(Collectors.joining(" | "));

            // Clean and format rows for LLM
            List<String> cleanedRows = new ArrayList<>();
            DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH);
            DateTimeFormatter outputFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (String[] row : dataRows) {
                StringBuilder cleanedRow = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    String cell = row[i].replace(",", "").trim();

                    // Convert date format if it's the date column
                    if (i == dateIndex) {
                        try {
                            cell = LocalDate.parse(cell, inputFormat).format(outputFormat);
                        } catch (Exception e) {
                            // keep original if parsing fails
                        }
                    }

                    cleanedRow.append(cell);
                    if (i < row.length - 1) {
                        cleanedRow.append(" | ");
                    }
                }
                cleanedRows.add(cleanedRow.toString());
            }

            String tableData = String.join("\n", cleanedRows);

            LOGGER.info("Cleaned table data is: \n" + tableData);

            // System prompt to guide LLM
            String systemInstruction = """
            You are an expert technical stock market analyst specializing in interpreting OHLCV (Open, High, Low, Close, Volume) data.

            Analyze the following data table and provide:
            1. **Trend Analysis**: Determine the overall trend (uptrend, downtrend, or sideways).
            2. **Support & Resistance**: List key levels with exact dates they were tested.
            3. **Candlestick Patterns**: Detect and name specific patterns (doji, engulfing, hammer, shooting star, etc.) with corresponding dates and what they imply.
            4. **Volume Analysis**: Identify volume spikes or drops and how they correlate with price movements, with dates.
            5. **Breakouts & Gaps**: Highlight any major breakouts, breakdowns, or price gaps and relevant dates.
            6. **Final Outlook**: Conclude with a concise trading outlook (bullish, bearish, or neutral), and why.

            Rules:
            - Always reference specific dates from the table for each insight.
            - Only use the data provided. No assumptions or external data.
            - Keep analysis structured and easy to follow using bullet points or short paragraphs.
        """;

            // User prompt with cleaned CSV data
            String userPrompt = """
                Here is the stock price data for the last %s trading days in the format:
                
                %s
                %s
                
                Act like a professional technical analyst and analyze this stock. Specifically provide:
                
                1. Short-term and medium-term trend direction
                2. Key support and resistance levels
                3. Moving average crossover signals (10-day, 20-day, 50-day)
                4. Volume analysis (any spikes or divergence)
                5. Bullish/bearish candlestick patterns (if any)
                6. Technical indicators or inferred overbought/oversold conditions
                7. A final summary with your outlook: bullish, bearish, or neutral
                
                Only use the data provided. Be concise and analytical.                                                                             
            """.formatted(maxRows, cleanedHeader, tableData);

            LOGGER.info("userPrompt is: " + userPrompt);

            // Call LLM
            String analysisResponse = memoryChatClient.prompt()
                    .user(userPrompt)
                    .system(systemInstruction)
                    .call()
                    .content();

            LOGGER.info("Received technical analysis response from LLM.");
            return ResponseEntity.ok(analysisResponse);

        } catch (Exception e) {
            LOGGER.error("Error analyzing CSV", e);
            return ResponseEntity.internalServerError().body("Error processing CSV: " + e.getMessage());
        }
    }

    @PostMapping("/csv/v2/analyze")
    public ResponseEntity<String> analyzeCsvFromPath_v2(@RequestBody FilePathRequest request) {
        String filePath = request.getPath();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            List<String[]> rows = reader.readAll();

            JSONArray schema = new JSONArray();
            schema.put("Date");
            schema.put("Open");
            schema.put("High");
            schema.put("Low");
            schema.put("Close");
            schema.put("Volume");
            schema.put("Change");
            schema.put("ChangePct");
            schema.put("MA7");
            schema.put("MA30");
            schema.put("EMA12");
            schema.put("EMA26");
            schema.put("MACD");
            schema.put("MACDSignal");
            schema.put("MACDHist");
            schema.put("RSI14");
            schema.put("ATR14");
            schema.put("BollingerUpper");
            schema.put("BollingerLower");
            schema.put("OBV");

            JSONArray dataArray = new JSONArray();

            // Skip header row
            rows.remove(0);

            List<Double> closes = new ArrayList<>();
            List<Double> highs = new ArrayList<>();
            List<Double> lows = new ArrayList<>();
            List<Long> volumes = new ArrayList<>();

            // OBV calculation
            double obv = 0;

            // For EMA
            Double ema12 = null, ema26 = null;
            double k12 = 2.0 / (12 + 1);
            double k26 = 2.0 / (26 + 1);

            // For MACD Signal (9-day EMA of MACD)
            Double macdSignal = null;
            double k9 = 2.0 / (9 + 1);
            List<Double> macdHistory = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i);

                String date = row[0].trim();
                double open = Double.parseDouble(row[2].replace(",", ""));
                double high = Double.parseDouble(row[3].replace(",", ""));
                double low = Double.parseDouble(row[4].replace(",", ""));
                double close = Double.parseDouble(row[7].replace(",", ""));
                long volume = Long.parseLong(row[11].replace(",", ""));

                // Save values
                closes.add(close);
                highs.add(high);
                lows.add(low);
                volumes.add(volume);

                // Precompute metrics
                double change = close - open;
                double changePct = (open != 0) ? (change / open) * 100.0 : 0.0;

                // Moving averages
                Double ma7 = Utility.movingAverage(closes, 7);
                Double ma30 = Utility.movingAverage(closes, 30);

                // EMA12 and EMA26
                if (ema12 == null) ema12 = close; else ema12 = (close - ema12) * k12 + ema12;
                if (ema26 == null) ema26 = close; else ema26 = (close - ema26) * k26 + ema26;

                // MACD
                double macd = ema12 - ema26;
                if (macdSignal == null) macdSignal = macd; else macdSignal = (macd - macdSignal) * k9 + macdSignal;
                double macdHist = macd - macdSignal;
                macdHistory.add(macd);

                // RSI14
                Double rsi14 = Utility.computeRSI(closes, 14);

                // ATR14
                Double atr14 = Utility.computeATR(highs, lows, closes, 14);

                // Bollinger Bands (20-day)
                Double[] bollinger = Utility.computeBollinger(closes, 20);

                // OBV
                if (i > 0) {
                    if (close > closes.get(i - 1)) obv += volume;
                    else if (close < closes.get(i - 1)) obv -= volume;
                }

                // Build compact row
                JSONArray record = new JSONArray();
                record.put(date);
                record.put(round(open));
                record.put(round(high));
                record.put(round(low));
                record.put(round(close));
                record.put(volume);
                record.put(round(change));
                record.put(round(changePct));
                record.put(ma7 != null ? round(ma7) : JSONObject.NULL);
                record.put(ma30 != null ? round(ma30) : JSONObject.NULL);
                record.put(round(ema12));
                record.put(round(ema26));
                record.put(round(macd));
                record.put(round(macdSignal));
                record.put(round(macdHist));
                record.put(rsi14 != null ? round(rsi14) : JSONObject.NULL);
                record.put(atr14 != null ? round(atr14) : JSONObject.NULL);
                record.put(bollinger[0] != null ? round(bollinger[0]) : JSONObject.NULL);
                record.put(bollinger[1] != null ? round(bollinger[1]) : JSONObject.NULL);
                record.put(round(obv));

                dataArray.put(record);
            }

            JSONObject result = new JSONObject();
            result.put("schema", schema);
            result.put("data", dataArray);

            return ResponseEntity.ok(result.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing CSV: " + e.getMessage());
        }
    }

    @PostMapping("/csv/v3/analyze")
    public ResponseEntity<String> analyzeCsvFromPath_v3(@RequestBody FilePathRequest request) {
        String filePath = request.getPath();

        JSONObject preComputedData = computeInvestmentView(filePath);

        LOGGER.info("Precomputed Data: " + preComputedData);

        // Extract data for prompt
        String recentDailyJson = preComputedData.getJSONArray("recent_daily").toString();
        String monthlySummaryJson = preComputedData.getJSONArray("monthly_average_of_all_OHLCV").toString();
//        String signalsJson = preComputedData.getJSONArray("signals").toString();

        // Placeholder user profile (replace with real user data if available)
        String userRiskTolerance = "medium";
        String userInvestmentHorizon = "6 months";
        String userPortfolioNotes = "No previous portfolio data";
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String formattedDate = today.format(formatter);

        // System instruction
//        String systemInstruction = """
//            You are a highly skilled technical stock market analyst.
//
//            You specialize in interpreting OHLCV (Open, High, Low, Close, Volume) data and technical indicators such as:
//            - Moving averages (SMA, EMA)
//            - MACD
//            - RSI
//            - Bollinger Bands
//            - Candlestick patterns (doji, hammer, engulfing, shooting star, etc.)
//
//            Your task is to provide detailed, date-specific, actionable analysis of the stock using only the data provided.
//
//            Rules:
//            1. Always reference exact dates from the data.
//            2. Only use the supplied data. Do not assume or fetch external info.
//            3. Structure your output in a clear, organized way:
//               - Trend Analysis
//               - Support & Resistance levels
//               - Candlestick Patterns
//               - Volume Analysis
//               - Breakouts, Gaps, and Signals
//               - Technical Indicator insights (RSI, MACD, Bollinger, etc.)
//               - Final Outlook (Bullish, Bearish, Neutral) with reasoning
//            4. If no signals or patterns are detected, explicitly mention that.
//            5. Keep your explanation concise, analytical, and professional.
//            """;

//        String systemInstruction = String.format("""
//            You are a highly skilled technical stock market analyst.
//            Analyze the provided OHLCV data of %s company and generate detailed, date-specific, actionable insights.
//            Focus only on the data provided.
//            Include:
//            - Trend analysis (short/medium/long term)
//            - Support/resistance levels (date-specific)
//            - Candlestick patterns
//            - Volume analysis
//            - Technical indicator signals
//            - Breakouts/gaps
//            - Final outlook (bullish/bearish/neutral)
//        """, request.getCompanyName());
//
//        // User prompt
//        String userPrompt = String.format("""
//            Today's date is: %s
//
//            User Profile:
//            - Risk Tolerance: %s
//            - Investment Horizon: %s
//            - Previous Portfolio Notes: %s
//
//            Stock Data:
//            - Recent Daily (last 60 OHLCV data): %s
//            - Monthly Summary: %s
//            - Signals: %s
//
//            Your task:
//            1. Short-term (days to weeks) trend analysis
//            2. Medium-term (weeks to months) trend analysis
//            3. Identify key support and resistance levels (based on given recent_daily data refer the recent_daily_columns to understand find support and resistences also all the amount is in rupees)
//            4. Given the 60 days data in recent_daily data, detect moving average crossovers (10, 20, 50-day). Columns names are present in recent_daily_columnss
//            5. Analyse and find out the candlestick patterns with exact dates based on the data given 60 days data in recent_daily. Columns names are present in recent_daily_columns
//            6. Volume spikes or divergence patterns
//            7. Technical indicator insights (RSI, MACD, Bollinger Bands, overbought/oversold)
//            8. Highlight any breakout or gap events
//            9. Provide a final trading outlook (buy/sell/hold) and reasoning
//            10. If no signals are present, explicitly note that
//
//            Only use the data provided above. Be analytical, concise, and reference exact dates.
//            While doing analysis keep in mind today's date: %s and do the analysis based on that as well.
//            """,
//                formattedDate,
//                userRiskTolerance,
//                userInvestmentHorizon,
//                userPortfolioNotes,
//                recentDailyJson,
//                monthlySummaryJson,
//                signalsJson,
//                formattedDate
//        );
        String systemInstruction = String.format("""
            You are a highly skilled technical stock market analyst.
            Analyze the provided OHLCV data of %s company and generate detailed, date-specific, actionable insights.
            Focus only on the data provided.
            Include:
            - Trend analysis (short/medium/long term)
            - Support/resistance levels (date-specific)
            - Candlestick patterns
            - Volume analysis
            - Breakouts/gaps
            - Final outlook (bullish/bearish/neutral)
        """, request.getCompanyName());

        // User prompt
        String userPrompt = String.format("""
            Today's date is: %s
            
            User Profile:
            - Risk Tolerance: %s
            - Investment Horizon: %s
            - Previous Portfolio Notes: %s

            Stock Data:
            - Recent Daily (last 60 OHLCV data): %s
            - monthly_average_of_all_OHLCV: %s

            Your task:
            1. Short-term (days to weeks) trend analysis
            2. Medium-term (weeks to months) trend analysis
            3. Identify key support and resistance levels (based on given recent_daily data refer the recent_daily_columns to understand find support and resistences also all the amount is in rupees)
            4. Given the 60 days data in recent_daily data, detect moving average crossovers (10, 20, 50-day). Columns names are present in recent_daily_columnss
            5. Analyse and find out the candlestick patterns with exact dates based on the data given 60 days data in recent_daily. Columns names are present in recent_daily_columns
            6. Volume spikes or divergence patterns
            8. Highlight any breakout or gap events
            9. Provide a final trading outlook (buy/sell/hold) and reasoning
            10. If no signals are present, explicitly note that

            Only use the data provided above. Be analytical, concise, and reference exact dates.
            While doing analysis keep in mind today's date: %s and do the analysis based on that as well.
            """,
                formattedDate,
                userRiskTolerance,
                userInvestmentHorizon,
                userPortfolioNotes,
                recentDailyJson,
                monthlySummaryJson,
                formattedDate
        );

        LOGGER.info("systemInstruction : " + systemInstruction);
        LOGGER.info("userPrompt: " + userPrompt);

        // Call your LLM
        try {
            String analysisResponse = memoryChatClient.prompt()
                    .system(systemInstruction)
                    .user(userPrompt)
                    .call()
                    .content();

            return ResponseEntity.ok(analysisResponse);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error generating analysis: " + e.getMessage());
        }
    }

    private JSONObject computeInvestmentView(String path) {

        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            List<String[]> rows = reader.readAll();
            rows.remove(0); // skip header

            List<Double> closes = new ArrayList<>();
            List<Double> highs = new ArrayList<>();
            List<Double> lows = new ArrayList<>();
            JSONArray recentDaily = new JSONArray();
            JSONArray monthlySummary = new JSONArray();
            JSONArray signals = new JSONArray();

            JSONArray recentDailyColumns = new JSONArray(Arrays.asList(
                    "Date","Open","High","Low","Close","Volume",
                    "Change","ChangePct",
                    "MA7","MA10","MA20","MA30","MA50",
                    "RSI14",
                    "MACD","MACDSignal","MACDHistogram",
                    "BollUpper","BollLower"
            ));
            JSONArray monthlySummaryColumns = new JSONArray(Arrays.asList(
                    "Month","Open","High","Low","Close","Volume"
            ));
            JSONArray signalsColumns = new JSONArray(Arrays.asList(
                    "Date","Type","Event"
            ));
            JSONArray investmentViewColumns = new JSONArray(Arrays.asList(
                    "Term","Trend","Risk","Signal","RSI"
            ));

            // Maps for monthly aggregation
            Map<String, List<String[]>> monthlyMap = new LinkedHashMap<>();

            int totalRows = rows.size();
            List<Double> macdSeries = new ArrayList<>();

//            for (int i = totalRows - 1; i >= 0; i--)
            for (int i = 0; i < totalRows; i++)
            {
                String[] row = rows.get(i);

                double high = Double.parseDouble(row[3].replace(",", ""));
                double low = Double.parseDouble(row[4].replace(",", ""));
                double close = Double.parseDouble(row[7].replace(",", ""));

                closes.add(close);
                highs.add(high);
                lows.add(low);
            }

//            for (int i = totalRows - 1; i >= 0; i--) {
            for (int i = 0; i < totalRows; i++){
                String[] row = rows.get(i);

                LocalDate parsedDate = LocalDate.parse(row[0].trim(), Utility.INPUT_DATE_FORMAT);
                String isoDate = parsedDate.format(Utility.ISO_DATE_FORMAT);

                double open = Double.parseDouble(row[2].replace(",", ""));
                double high = Double.parseDouble(row[3].replace(",", ""));
                double low = Double.parseDouble(row[4].replace(",", ""));
                double close = Double.parseDouble(row[7].replace(",", ""));
                long volume = Long.parseLong(row[11].replace(",", ""));

                double change = close - open;
                double changePct = (open != 0) ? (change / open) * 100.0 : 0.0;

                Double ma7 = null;
                if(i < totalRows-8)
                    ma7 = movingAverage(closes.subList(i, i+7), 7);

                Double ma10 = null;
                if(i < totalRows-11)
                    ma10 = movingAverage(closes.subList(i, i+10), 10);

                Double ma20 = null;
                if(i < totalRows-21)
                    ma20 = movingAverage(closes.subList(i, i+20), 20);

                Double ma30 = null;

                if(i < totalRows-31)
                    ma30 = movingAverage(closes.subList(i, i+30), 30);

                Double ma50 = null;
                if(i < totalRows-51)
                    ma50 = movingAverage(closes.subList(i, i+50), 50);

                Double rsi14 = null;
                if(i < totalRows-16)
                    rsi14 = computeRSI(closes.subList(i, i+15), 14);

                // MACD (12,26,9)
//                Double macd = computeEMA(closes, 12) - computeEMA(closes, 26);
//                Double ema12 = computeEMA(closes, 12);
//                Double ema26 = computeEMA(closes, 26);
//                Double macd = null;
//                if (ema12 != null && ema26 != null) {
//                    macd = ema12 - ema26;
//                }
//                Double signal = computeEMA(Collections.singletonList(macd), 9); // placeholder, better: maintain EMA history
//                Double histogram = (signal != null) ? macd - signal : null;

                Double ema12 = null;
                Double ema26 = null;

                if(i < totalRows-13)
                    ema12 = computeEMA(closes.subList(i, i+12), 12);

                if(i < totalRows-27)
                    ema26 = computeEMA(closes.subList(i, i+26), 26);

                Double macd = null;
                Double signal = null;
                Double histogram = null;

                if (ema12 != null && ema26 != null) {
                    macd = ema12 - ema26;
                    macdSeries.add(macd);

                    if (macdSeries.size() >= 9) {
                        signal = computeEMA(macdSeries, 9);
                        histogram = macd - signal;
                    }
                }

                // Bollinger Bands (20-period, 2 std dev)
                Double[] boll = null;
                Double bollUpper = null;
                Double bollLower = null;
                if(i < totalRows-21) {
                    boll = computeBollinger(closes.subList(i, i+20), 20, 2.0);
                    bollUpper= boll[0];
                    bollLower = boll[1];
                }

                // Build recent daily row
//                if (recentDaily.length() < 60) { // last 60 days
//                if (i <= totalRows - 60) {
                if (i < 60) {
                    JSONArray record = new JSONArray();
                    record.put(isoDate);
                    record.put(round(open));
                    record.put(round(high));
                    record.put(round(low));
                    record.put(round(close));
                    record.put(volume);
                    record.put(round(change));
                    record.put(round(changePct));
                    record.put(ma7 != null ? round(ma7) : JSONObject.NULL);
                    record.put(ma10 != null ? round(ma10) : JSONObject.NULL);
                    record.put(ma20 != null ? round(ma20) : JSONObject.NULL);
                    record.put(ma30 != null ? round(ma30) : JSONObject.NULL);
                    record.put(ma50 != null ? round(ma50) : JSONObject.NULL);
                    record.put(rsi14 != null ? round(rsi14) : JSONObject.NULL);
                    record.put(macd != null ? round(macd) : JSONObject.NULL);
                    record.put(signal != null ? round(signal) : JSONObject.NULL);
                    record.put(histogram != null ? round(histogram) : JSONObject.NULL);
                    record.put(bollUpper != null ? round(bollUpper) : JSONObject.NULL);
                    record.put(bollLower != null ? round(bollLower) : JSONObject.NULL);
                    recentDaily.put(record);
                }

                // Collect monthly data for aggregation
                String monthKey = parsedDate.format(Utility.ISO_MONTH_FORMAT); // e.g., "Apr-25"
                monthlyMap.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(row);

                if(i < 8)
                {
                    if (macd != null && signal != null) {
                        if (Math.abs(macd-signal) >= 1 && macd > signal) {
                            JSONObject sig = new JSONObject();
                            sig.put("date", isoDate);
                            sig.put("type", "MACD");
                            sig.put("event", "Bullish crossover");
                            signals.put(sig);
                        } else if (Math.abs(macd-signal) >= 1 && macd < signal) {
                            JSONObject sig = new JSONObject();
                            sig.put("date", isoDate);
                            sig.put("type", "MACD");
                            sig.put("event", "Bearish crossover");
                            signals.put(sig);
                        }
                    }
                }

                if(i < 8)
                {
                    if(bollUpper < close)
                    {
                        JSONObject sig = new JSONObject();
                        sig.put("date", isoDate);
                        sig.put("type", "Bollingerband crossover");
                        sig.put("event", "upper crossover");
                        signals.put(sig);
                    }
                    else if(bollLower > close)
                    {
                        JSONObject sig = new JSONObject();
                        sig.put("date", isoDate);
                        sig.put("type", "Bollingerband crossover");
                        sig.put("event", "lower crossover");
                        signals.put(sig);
                    }
                }

                // RSI, etc. can be computed similarly
            }

            if (signals.length() == 0) {
                JSONObject noSignal = new JSONObject();
                noSignal.put("note", "No MACD crossovers detected in the processed period.");
                signals.put(noSignal);
            }

            // Compute monthly summary
            // Monthly summary with realistic support/resistance
            for (Map.Entry<String, List<String[]>> entry : monthlyMap.entrySet()) {
                String month = entry.getKey();
                List<String[]> monthRows = entry.getValue();

                double open = Double.parseDouble(monthRows.get(0)[2].replace(",", ""));
                double close = Double.parseDouble(monthRows.get(monthRows.size() - 1)[7].replace(",", ""));
                double high = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[3].replace(",", ""))).max().orElse(0);
                double low = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[4].replace(",", ""))).min().orElse(0);
                long volume = monthRows.stream().mapToLong(r -> Long.parseLong(r[11].replace(",", ""))).sum();

                JSONArray monthlyRow = new JSONArray();
                monthlyRow.put(month);
                monthlyRow.put(round(open));
                monthlyRow.put(round(high));
                monthlyRow.put(round(low));
                monthlyRow.put(round(close));
                monthlyRow.put(volume);
                monthlySummary.put(monthlyRow);
            }

            // Build investment_view (very basic example)
            JSONObject investmentView = new JSONObject();
            investmentView.put("short_term", computeInvestmentView(recentDaily));
            investmentView.put("medium_term", computeInvestmentView(monthlySummary));
            investmentView.put("long_term", computeInvestmentView(monthlySummary)); // could be 1-year aggregate

            // Meta information
//            JSONObject meta = new JSONObject();
//            meta.put("symbol", "XYZ");
//            meta.put("currency", "INR");
//            meta.put("data_range", rows.size() + " days");

            JSONObject result = new JSONObject();
            result.put("recent_daily_columns", recentDailyColumns);
            result.put("recent_daily", recentDaily);
            result.put("monthly_average_of_all_OHLCV_columns", monthlySummaryColumns);
            result.put("monthly_average_of_all_OHLCV", monthlySummary);
            result.put("signals_columns", signalsColumns);
            result.put("signals", signals);
            result.put("investment_view_columns", investmentViewColumns);
            result.put("investment_view", investmentView);
//            result.put("meta", meta);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.info("Error processing CSV: " + e.getMessage());

            return new JSONObject();
        }
    }

    private JSONObject computeInvestmentView_v1(String path) {

        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            List<String[]> rows = reader.readAll();
            rows.remove(0); // skip header

            List<Double> closes = new ArrayList<>();
            List<Double> highs = new ArrayList<>();
            List<Double> lows = new ArrayList<>();
            JSONArray recentDaily = new JSONArray();
            JSONArray monthlySummary = new JSONArray();
            JSONArray signals = new JSONArray();

            JSONArray recentDailyColumns = new JSONArray(Arrays.asList(
                    "Date","Open","High","Low","Close","Volume",
                    "Change","ChangePct",
                    "MA7","MA10","MA20","MA30","MA50",
                    "RSI14",
                    "MACD","MACDSignal","MACDHistogram",
                    "BollUpper","BollLower"
            ));
            JSONArray monthlySummaryColumns = new JSONArray(Arrays.asList(
                    "Month","Open","High","Low","Close","Volume"
            ));
            JSONArray signalsColumns = new JSONArray(Arrays.asList(
                    "Date","Type","Event"
            ));
            JSONArray investmentViewColumns = new JSONArray(Arrays.asList(
                    "Term","Trend","Risk","Signal","RSI"
            ));

            // Maps for monthly aggregation
            Map<String, List<String[]>> monthlyMap = new LinkedHashMap<>();

            List<DailyOHLCRecords> dailyOHLCRecords = new ArrayList<>();

            // Process rows in reverse to easily get last 60 days
            int totalRows = rows.size();
//            int startIndex = Math.max(0, totalRows - 60); // get the last 60 rows

//            for (int i = startIndex; i < totalRows; i++) {
//            for (int i = 0; i < totalRows; i++) {
            for (int i = totalRows - 1; i >= 0; i--) {
                String[] row = rows.get(i);

                DailyOHLCRecords ohlcRecords = new DailyOHLCRecords();

                LocalDate parsedDate = LocalDate.parse(row[0].trim(), Utility.INPUT_DATE_FORMAT);
                String isoDate = parsedDate.format(Utility.ISO_DATE_FORMAT);

                ohlcRecords.setDate(isoDate);

                double open = Double.parseDouble(row[2].replace(",", ""));
                double high = Double.parseDouble(row[3].replace(",", ""));
                double low = Double.parseDouble(row[4].replace(",", ""));
                double close = Double.parseDouble(row[7].replace(",", ""));
                long volume = Long.parseLong(row[11].replace(",", ""));

                ohlcRecords.setOpen(open);
                ohlcRecords.setHigh(high);
                ohlcRecords.setLow(low);
                ohlcRecords.setClose(close);
                ohlcRecords.setVolume(volume);

                closes.add(close);
                highs.add(high);
                lows.add(low);

                // Precompute daily metrics
                double change = close - open;
                double changePct = (open != 0) ? (change / open) * 100.0 : 0.0;

                // Moving averages
                Double ma7 = movingAverage(closes, 7);
                Double ma10 = movingAverage(closes, 10);
                Double ma20 = movingAverage(closes, 20);
                Double ma30 = movingAverage(closes, 30);
                Double ma50 = movingAverage(closes, 50);

                Double rsi14 = computeRSI(closes, 14);

                // MACD (12,26,9)
//                Double macd = computeEMA(closes, 12) - computeEMA(closes, 26);
//                Double ema12 = computeEMA(closes, 12);
//                Double ema26 = computeEMA(closes, 26);
//                Double macd = null;
//                if (ema12 != null && ema26 != null) {
//                    macd = ema12 - ema26;
//                }
//                Double signal = computeEMA(Collections.singletonList(macd), 9); // placeholder, better: maintain EMA history
//                Double histogram = (signal != null) ? macd - signal : null;

                List<Double> macdSeries = new ArrayList<>();
                Double ema12 = computeEMA(closes, 12);
                Double ema26 = computeEMA(closes, 26);
                Double macd = null;
                Double signal = null;
                Double histogram = null;

                if (ema12 != null && ema26 != null) {
                    macd = ema12 - ema26;
                    macdSeries.add(macd);

                    if (macdSeries.size() >= 9) {
                        signal = computeEMA(macdSeries, 9);
                        histogram = macd - signal;
                    }
                }

                // Bollinger Bands (20-period, 2 std dev)
                Double[] boll = computeBollinger(closes, 20, 2.0);
                Double bollUpper = boll[0];
                Double bollLower = boll[1];

//                if (closes.size() >= 7) {
//                    ma7 = closes.subList(closes.size() - 7, closes.size()).stream()
//                            .mapToDouble(Double::doubleValue).average().orElse(0.0);
//                }
//                if (closes.size() >= 30) {
//                    ma30 = closes.subList(closes.size() - 30, closes.size()).stream()
//                            .mapToDouble(Double::doubleValue).average().orElse(0.0);
//                }

                // Build recent daily row
//                if (recentDaily.length() < 60) { // last 60 days
                if (i <= totalRows - 60) {
                    JSONArray record = new JSONArray();
                    record.put(isoDate);
                    record.put(round(open));
                    record.put(round(high));
                    record.put(round(low));
                    record.put(round(close));
                    record.put(volume);
                    record.put(round(change));
                    record.put(round(changePct));
                    record.put(ma7 != null ? round(ma7) : JSONObject.NULL);
                    record.put(ma10 != null ? round(ma10) : JSONObject.NULL);
                    record.put(ma20 != null ? round(ma20) : JSONObject.NULL);
                    record.put(ma30 != null ? round(ma30) : JSONObject.NULL);
                    record.put(ma50 != null ? round(ma50) : JSONObject.NULL);
                    record.put(rsi14 != null ? round(rsi14) : JSONObject.NULL);
                    record.put(macd != null ? round(macd) : JSONObject.NULL);
                    record.put(signal != null ? round(signal) : JSONObject.NULL);
                    record.put(histogram != null ? round(histogram) : JSONObject.NULL);
                    record.put(bollUpper != null ? round(bollUpper) : JSONObject.NULL);
                    record.put(bollLower != null ? round(bollLower) : JSONObject.NULL);
                    recentDaily.put(record);
                }

                // Collect monthly data for aggregation
                String monthKey = parsedDate.format(Utility.ISO_MONTH_FORMAT); // e.g., "Apr-25"
                monthlyMap.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(row);

                // Example: precompute signals (simple version)
                // MACD crossover placeholder: bullish if close > ma12 (you can implement real MACD)
//                if (ma12(closes) != null && ma26(closes) != null) {
//                    double macd = ma12(closes) - ma26(closes);
//                    double signal = ma9(macd);
//                    if (macd > signal) {
//                        JSONObject sig = new JSONObject();
//                        sig.put("date", isoDate);
//                        sig.put("type", "MACD");
//                        sig.put("event", "Bullish crossover");
//                        signals.put(sig);
//                    } else if (macd < signal) {
//                        JSONObject sig = new JSONObject();
//                        sig.put("date", isoDate);
//                        sig.put("type", "MACD");
//                        sig.put("event", "Bearish crossover");
//                        signals.put(sig);
//                    }
//                }

                if (macd != null && signal != null) {
                    if (macd > signal) {
                        JSONObject sig = new JSONObject();
                        sig.put("date", isoDate);
                        sig.put("type", "MACD");
                        sig.put("event", "Bullish crossover");
                        signals.put(sig);
                    } else if (macd < signal) {
                        JSONObject sig = new JSONObject();
                        sig.put("date", isoDate);
                        sig.put("type", "MACD");
                        sig.put("event", "Bearish crossover");
                        signals.put(sig);
                    }
                }

                // RSI, Bollinger, etc. can be computed similarly
            }

            if (signals.length() == 0) {
                JSONObject noSignal = new JSONObject();
                noSignal.put("note", "No MACD crossovers detected in the processed period.");
                signals.put(noSignal);
            }

            // Compute monthly summary
            // Monthly summary with realistic support/resistance
            for (Map.Entry<String, List<String[]>> entry : monthlyMap.entrySet()) {
                String month = entry.getKey();
                List<String[]> monthRows = entry.getValue();

                double open = Double.parseDouble(monthRows.get(0)[2].replace(",", ""));
                double close = Double.parseDouble(monthRows.get(monthRows.size() - 1)[7].replace(",", ""));
                double high = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[3].replace(",", ""))).max().orElse(0);
                double low = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[4].replace(",", ""))).min().orElse(0);
                long volume = monthRows.stream().mapToLong(r -> Long.parseLong(r[11].replace(",", ""))).sum();

                JSONArray monthlyRow = new JSONArray();
                monthlyRow.put(month);
                monthlyRow.put(round(open));
                monthlyRow.put(round(high));
                monthlyRow.put(round(low));
                monthlyRow.put(round(close));
                monthlyRow.put(volume);
                monthlySummary.put(monthlyRow);
            }

            // Build investment_view (very basic example)
            JSONObject investmentView = new JSONObject();
            investmentView.put("short_term", computeInvestmentView(recentDaily));
            investmentView.put("medium_term", computeInvestmentView(monthlySummary));
            investmentView.put("long_term", computeInvestmentView(monthlySummary)); // could be 1-year aggregate

            // Meta information
            JSONObject meta = new JSONObject();
            meta.put("symbol", "XYZ");
            meta.put("currency", "INR");
            meta.put("data_range", rows.size() + " days");

            JSONObject result = new JSONObject();
            result.put("recent_daily_columns", recentDailyColumns);
            result.put("recent_daily", recentDaily);
            result.put("monthly_summary_columns", monthlySummaryColumns);
            result.put("monthly_summary", monthlySummary);
            result.put("signals_columns", signalsColumns);
            result.put("signals", signals);
            result.put("investment_view_columns", investmentViewColumns);
            result.put("investment_view", investmentView);
            result.put("meta", meta);

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.info("Error processing CSV: " + e.getMessage());

            return new JSONObject();
        }
    }

    // Helper rounding
    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

//    private Double movingAverage(List<Double> data, int period) {
//        if (data.size() < period) return null;
//        return data.subList(data.size() - period, data.size()).stream()
//                .mapToDouble(Double::doubleValue).average().orElse(0.0);
//    }

    public static Double movingAverage(List<Double> closes, int period) {
        if (closes == null || closes.size() < period) {
            return null;  // Not enough data points
        }

        // Sum the last 'period' closes
        double sum = 0.0;
        int startIndex = closes.size() - period;
        for (int i = startIndex; i < closes.size(); i++) {
            sum += closes.get(i);
        }

        return sum / period;
    }

//    private Double computeEMA(List<Double> data, int period) {
//        if (data.size() < period) return null;
//        double k = 2.0 / (period + 1);
//        double ema = data.get(0);
//        for (int i = 1; i < data.size(); i++) {
//            ema = (data.get(i) * k) + (ema * (1 - k));
//        }
//        return ema;
//    }

    public static Double computeEMA(List<Double> closes, int period) {
        if (closes == null || closes.size() < period) {
            return null; // Not enough data to calculate EMA
        }

        // Calculate initial SMA for first 'period' closes
        double sma = closes.subList(0, period).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        double k = 2.0 / (period + 1);
        double ema = sma;

        // Calculate EMA for days after initial period
        for (int i = period; i < closes.size(); i++) {
            double price = closes.get(i);
            ema = price * k + ema * (1 - k);
        }

        return ema;
    }

//    private Double computeRSI(List<Double> closes, int period) {
//        if (closes.size() < period + 1) return null;
//        double gains = 0, losses = 0;
//        for (int i = closes.size() - period; i < closes.size(); i++) {
//            double change = closes.get(i) - closes.get(i - 1);
//            if (change >= 0) gains += change;
//            else losses -= change;
//        }
//        double rs = (losses == 0) ? 100 : (gains / losses);
//        return 100 - (100 / (1 + rs));
//    }

    public static Double computeRSI(List<Double> closes, int period) {
//        if (closes == null || closes.size() < period) {
//            // Need at least period + 1 closes to calculate RSI
//            return null;
//        }

        double gainSum = 0;
        double lossSum = 0;

        // Calculate gains and losses for the first 'period' days
        for (int i = 1; i <= period; i++) {
            double change = closes.get(i) - closes.get(i - 1);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum += -change;  // Loss is positive value
            }
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        // Avoid division by zero
        if (avgLoss == 0) {
            return 100.0;  // RSI is 100 if no losses
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));

        return rsi;
    }

    public static double calculateRSIFor14Days(List<Double> closes) {

        double gainSum = 0;
        double lossSum = 0;

        for (int i = 1; i < closes.size(); i++) {
            double change = closes.get(i-1) - closes.get(i);
            if (change > 0) {
                gainSum += change;
            } else {
                lossSum += -change;
            }
        }

        double avgGain = gainSum / 14;
        double avgLoss = lossSum / 14;

        if (avgLoss == 0) {
            return 100.0; // RSI is 100 if no losses
        }

        double rs = avgGain / avgLoss;
        double rsi = 100 - (100 / (1 + rs));
        return rsi;
    }

    private Double[] computeBollinger(List<Double> closes, int period, double numStdDev) {
        if (closes.size() < period) return new Double[]{null, null};
        List<Double> window = closes.subList(closes.size() - period, closes.size());
        double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = window.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / period;
        double stdDev = Math.sqrt(variance);
        return new Double[]{mean + numStdDev * stdDev, mean - numStdDev * stdDev};
    }

    //    private double round(double value) {
//        return Math.round(value * 100.0) / 100.0;
//    }
    // Compute investment view (trend, risk, signal)
    private JSONObject computeInvestmentView(JSONArray rows) {
        JSONObject view = new JSONObject();
        // Basic example: trend = bullish if last close > first close
        if (rows.length() > 1) {
            double firstClose = rows.getJSONArray(0).getDouble(4);
            double lastClose = rows.getJSONArray(rows.length()-1).getDouble(4);
            view.put("trend", lastClose >= firstClose ? "bullish" : "bearish");
            view.put("rsi", 50); // placeholder
            view.put("signal", "MACD bullish crossover"); // placeholder
            view.put("risk", "medium"); // placeholder
        }
        return view;
    }

    @GetMapping("/memory/analyseImageAndCSV")
    public ResponseEntity<?> imageToTextWithCSVInMemory() {

        var curr_price = "1248.70";
        var start_date = "31st December 2024";
        var last_date = "03rd April 2025";

        try (CSVReader reader = new CSVReader(new FileReader("C:Users//nikhil.neosoft//Downloads//reliance-JanToMar.csv"))) {
            List<String[]> rows = reader.readAll();

            if (rows.size() < 2) {
                return ResponseEntity.badRequest().body("CSV must contain at least one row of data.");
            }

            String[] headers = rows.get(0);
            // Limit rows to last 50 for faster processing
            List<String[]> dataRows = rows.subList(1, Math.min(rows.size(), 70));

            // Preprocess CSV data to generate summary statistics
            int n = dataRows.size();

            // Parse numeric values safely after removing commas
            double avgVolume = dataRows.stream()
                    .mapToDouble(row -> {
                        try {
                            return Double.parseDouble(row[8].replace(",", ""));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .average().orElse(0);

            double maxHigh = dataRows.stream()
                    .mapToDouble(row -> {
                        try {
                            return Double.parseDouble(row[2].replace(",", ""));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .max().orElse(0);

            double minLow = dataRows.stream()
                    .mapToDouble(row -> {
                        try {
                            return Double.parseDouble(row[3].replace(",", ""));
                        } catch (Exception e) {
                            return 0;
                        }
                    })
                    .min().orElse(0);

            // Create a concise summary of CSV data
            String csvSummary = String.format("""
                    Summary of recent %d days trading data:
                    - Average Volume: %.0f
                    - Highest High: %.2f
                    - Lowest Low: %.2f
                    - Price range: %.2f to %.2f

                    Now here are some detailed daily data points:
                    """, n, avgVolume, maxHigh, minLow, minLow, maxHigh);

            // Convert a few rows (e.g. last 10) to text for detailed context
//            List<String[]> detailedRows = dataRows.subList(Math.max(0, n - 10), n);
            List<String[]> detailedRows = dataRows;
            String headerLine = String.join(" | ", headers);


            String detailedCsvText = detailedRows.stream()
                    .map(row -> String.join(" | ", row))
                    .collect(Collectors.joining("\n"));

            // Combine summary + detailed snippet
            String csvText = csvSummary + "\n" + headerLine + "\n" + detailedCsvText + "\n\n" +
                    "Please analyze the above data from a technical analysis point of view. " +
                    "Focus on support/resistance, trends, candlestick patterns, volume changes, and overall trading outlook.";

            LOGGER.info("the CSV text is: " + csvText);

            // System instruction with clear modular tasks for image + CSV combined analysis
            String imageSystemInstruction = """
                    You are a highly experienced technical stock market analyst who specializes in combining candlestick chart image analysis with OHLC + volume CSV data.

                    You are provided with:
                    - A candlestick chart image (daily interval, 1 candle = 1 day)
                    - A CSV file containing daily trading data: Date, OPEN, HIGH, LOW, PREV. CLOSE, LTP, CLOSE, VWAP, and VOLUME
                    - A date range from %s to %s
                    - The current price is %s INR (visible on the chart)

                    Use the chart image for visual patterns, and the CSV data for precise confirmation.

                    Your Tasks:
                    1. Analyze the chart image:
                       - Identify trends (uptrend/downtrend/sideways)
                       - Detect candlestick patterns (hammer, engulfing, doji, etc.)
                       - Highlight key support/resistance levels based on candle wicks and closings
                       - Use reference lines and price scale for interpreting levels

                    2. Analyze the CSV data:
                       - Look for spikes/drops in volume
                       - Confirm price gaps, reversals, and consistent support/resistance levels
                       - Identify volatile or compressing price action

                    3. Combine both sources for:
                       - Cross-validation of chart patterns using numerical data
                       - Confirmation of support/resistance levels using volume + price action
                       - Final trend confirmation or contradiction between chart vs. CSV

                    **Important**:
                    - For **every candlestick pattern**, **support/resistance level**, **gap**, or **breakout** you mention:
                      - **Specify the exact date(s)** (from the CSV) it occurred
                      - If it’s visible in the chart, say “visible in chart” (or "not clear in chart") so the user can verify visually
                      - Always tie your insights to **dates and price levels** clearly

                    Output Format:
                    - **Summary of trend** (with date range)
                    - **Support & resistance levels** (with dates of interaction)
                    - **Candlestick patterns found** (with dates and whether visible in chart)
                    - **Volume and price action insights** (include dates for spikes, breakouts)
                    - **Final trading outlook** (bullish / bearish / neutral), with brief rationale

                    Be clear, specific, and accurate. Mention dates wherever possible.
                    """.formatted(start_date, last_date, curr_price);

            LOGGER.info("Sending summary and detailed CSV snippet along with image for analysis...");
            LOGGER.info("system instruction: " + imageSystemInstruction);

            String fullAnalysis = memoryChatClient.prompt()
                    .user(u -> {
//                        u.text("Analyze the chart image and the following summarized CSV data together for a comprehensive technical analysis.");
                        u.text("Analyze the chart image and the following summarized CSV data together for a comprehensive technical analysis. Always mention the exact date(s) when describing patterns, reversals, or volume changes.");
                        u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);
                        u.text(csvText);
                    })
                    .system(imageSystemInstruction)
                    .call()
                    .content();

            LOGGER.info("Received full analysis response from LLM.");

//            SimpleVectorStore store = simpleVectorStore(embeddingModel, fullAnalysis);

            vectorDB.embedAndStore(fullAnalysis);

            LOGGER.info("Stored structured analysis and embeddings successfully.");

            return ResponseEntity.ok(fullAnalysis);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing CSV: " + e.getMessage());
        }
    }

    @GetMapping("/memory/enhanced/analyseImageAndCSV")
    public ResponseEntity<?> analyzeImageAndCSV() {
        try {
            List<String[]> csvRows = Utility.loadCSV("C:Users//nikhil.neosoft//Downloads//reliance-JanToMar.csv");
            List<TechnicalPattern> patterns = Utility.extractPatternsFromCSV(csvRows);
//            List<SupportResistanceLevel> srLevels = extractSupportResistance(csvRows);
            List<SupportResistanceLevel> srLevels = null;
            List<String> volumeSpikes = Utility.detectVolumeSpikes(csvRows);

            String currPrice = "1248.70";
            String startDate = "31st December 2024";
            String endDate = "03rd April 2025";

            double[] visibleRange = Utility.getVisiblePriceRangeFromCSV(csvRows);

            // Mark visibility of patterns after analyzing image (pseudo)
            double visibleMinPrice = visibleRange[0];
            double visibleMaxPrice = visibleRange[1];

//            markPatternsVisibleInChart(patterns, visibleMinPrice, visibleMaxPrice);

            String chainOfThoughtPrompt = buildChainOfThoughtPrompt(patterns, srLevels, volumeSpikes, currPrice, startDate, endDate);

            List<TechnicalPattern> extractedPattern = Utility.extractPatternsFromCSV(csvRows);
            String csvSummaryText = Utility.buildCsvSummaryText(extractedPattern);

            String fullPrompt = chainOfThoughtPrompt + "\n\nCSV Summary:\n" + csvSummaryText + "\n\nAnalyze the attached candlestick chart image and the above CSV summary data for a complete technical analysis.";

            String fullAnalysis = memoryChatClient.prompt()
                    .system("Be precise, technical, and date-specific in your analysis. Reason step-by-step.")
                    .user(u -> {
                        u.text(fullPrompt);
                        u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);
                    })
                    .call()
                    .content();

            // Store analysis and embeddings (for memory retrieval)
//            storeAnalysisInVectorDB(fullAnalysis);
            Utility.simpleVectorStore(embeddingModel, fullAnalysis);

            return ResponseEntity.ok(fullAnalysis);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing CSV or image: " + e.getMessage());
        }
    }

    @PostMapping("memory/extracted/analyseImageAndCSV")
    public ResponseEntity<?> analyzeImageAndextractedCSV(@RequestBody FilePathRequest request) throws IOException {
        try (CSVReader reader = new CSVReader(new FileReader(request.getPath()))) {
            List<String[]> rows = reader.readAll();
            JSONObject data = new JSONObject();
//            rows.remove(0);

            List<DailyOHLCRecords> dailyOHLCRecords = new ArrayList<>();
            List<Double> closes = new ArrayList<>();

            //ohlcv calculation
            for (int i = rows.size() - 1; i >= 1; i--) {
                String[] row = rows.get(i);

                DailyOHLCRecords ohlcRecord = new DailyOHLCRecords();

                LocalDate parsedDate = LocalDate.parse(row[0].trim(), Utility.INPUT_DATE_FORMAT);
                String isoDate = parsedDate.format(Utility.ISO_DATE_FORMAT);

                ohlcRecord.setDate(isoDate);
                ohlcRecord.setOpen(Double.parseDouble(row[2].replace(",", "")));
                ohlcRecord.setHigh(Double.parseDouble(row[3].replace(",", "")));
                ohlcRecord.setLow(Double.parseDouble(row[4].replace(",", "")));
                ohlcRecord.setClose(Double.parseDouble(row[7].replace(",", "")));
                closes.add(ohlcRecord.getClose());
                ohlcRecord.setPrevClose(Double.parseDouble(row[5].replace(",", "")));
                ohlcRecord.setLtp(Double.parseDouble(row[6].replace(",", "")));
                ohlcRecord.setVolume(Long.parseLong(row[11].replace(",", "")));

                dailyOHLCRecords.add(ohlcRecord);
            }

            //change and perc change calculation
            for (DailyOHLCRecords record : dailyOHLCRecords) {
                double close = record.getClose(), open = record.getOpen();
                double change = close - open;
                change = Math.round(change * 100.0) / 100.0;
                record.setChange(change);
                double changePct = (open != 0) ? (change / open) * 100.0 : 0.0;
                changePct = Math.round(changePct * 100.0) / 100.0;
                record.setChangePct(changePct);
            }

            //ma calculation
            for (int i = 1; i < dailyOHLCRecords.size(); i++) {
                DailyOHLCRecords record = dailyOHLCRecords.get(i - 1);

                if (i >= 7) {
                    double l = movingAverage(closes.subList(i - 7, i), 7);
                    record.setMa7((double) Math.round(l * 100) / 100);
                }

                if (i >= 10) {
                    double l = movingAverage(closes.subList(i - 10, i), 10);
                    record.setMa10((double) Math.round(l * 100) / 100);
                }

                if (i >= 20) {
                    double l = movingAverage(closes.subList(i - 20, i), 20);
                    record.setMa20((double) Math.round(l * 100) / 100);
                }

                if (i >= 30) {
                    double l = movingAverage(closes.subList(i - 30, i), 30);
                    record.setMa30((double) Math.round(l * 100) / 100);
                }

                if (i >= 50) {
                    double l = movingAverage(closes.subList(i - 50, i), 50);
                    record.setMa50((double) Math.round(l * 100) / 100);
                }
            }

            //rsi calculation
//            for(int i = 0; i < dailyOHLCRecords.size(); i++) {
//                System.out.println(dailyOHLCRecords.size());
//                DailyOHLCRecords record = dailyOHLCRecords.get(i);
//
//                if (i >= 15)
//                    record.setRsi14((double)Math.round((calculateRSIFor14Days(closes.subList(i-15, i-1)))*100)/100);
//            }

            List<Double> macdSeries = new ArrayList<>();

//            //macd calculation
//            for(int i = 0; i < dailyOHLCRecords.size(); i++)
//            {
//                DailyOHLCRecords record = dailyOHLCRecords.get(i);
//
//                Double ema12 = null;
//                Double ema26 = null;
//
//                if(i>=12)
//                    ema12 = computeEMA(closes.subList(i-12, i), 12);
//
//                if(i>=26)
//                    ema26 = computeEMA(closes.subList(i-26, i), 26);
//
//                Double macd = null;
//
//                if (ema12 != null && ema26 != null) {
//                    macd = ema12 - ema26;
//                    record.setMacd((double)Math.round(macd*100)/100);
//                    macdSeries.add((double)Math.round(macd*100)/100);
//                }
//                else
//                    macdSeries.add((double)0);
//            }

//            for(int i = 0; i < dailyOHLCRecords.size(); i++) {
//                DailyOHLCRecords record = dailyOHLCRecords.get(i);
//
//                if(i>=26) {
//                    double signal = (double) Math.round(computeEMA(macdSeries.subList(i - 9, i), 9) * 100) / 100;
//                    record.setMacdSignal(signal);
//                    record.setMacdHistogram((double)Math.round((macdSeries.get(i) - signal)*100)/100);
//                }
//
//                System.out.println(record.toString());
//            }

            //bollinger band
            for (int i = 1; i < dailyOHLCRecords.size(); i++) {
                DailyOHLCRecords record = dailyOHLCRecords.get(i - 1);

                Double[] boll = null;

                if (i >= 20) {
                    boll = computeBollinger(closes.subList(i - 20, i), 20, 2.0);
                    record.setBollUpper((double) Math.round(boll[0] * 100) / 100);
                    record.setBollLower((double) Math.round(boll[1] * 100) / 100);
                }
            }

            Map<String, List<String[]>> monthlyMap = new LinkedHashMap<>();
            JSONObject monthlySummaryJson = new JSONObject();

            for (int i = 1; i < rows.size()-1; i++) {
                String[] row = rows.get(i);
                DailyOHLCRecords record = dailyOHLCRecords.get(i);

                LocalDate parsedDate = LocalDate.parse(row[0].trim(), Utility.INPUT_DATE_FORMAT);
                String monthKey = parsedDate.format(Utility.ISO_MONTH_FORMAT); // e.g., "Apr-25"
                monthlyMap.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(row);
            }

            JSONArray monthlyRow = new JSONArray();

            for (Map.Entry<String, List<String[]>> entry : monthlyMap.entrySet()) {
                String month = entry.getKey();
                List<String[]> monthRows = entry.getValue();

                double open = Double.parseDouble(monthRows.get(0)[2].replace(",", ""));
                double close = Double.parseDouble(monthRows.get(monthRows.size() - 1)[7].replace(",", ""));
                double high = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[3].replace(",", ""))).max().orElse(0);
                double low = monthRows.stream().mapToDouble(r -> Double.parseDouble(r[4].replace(",", ""))).min().orElse(0);
                long volume = monthRows.stream().mapToLong(r -> Long.parseLong(r[11].replace(",", ""))).sum();

//
//                monthlyRow.put(month);
//                monthlyRow.put(round(open));
//                monthlyRow.put(round(high));
//                monthlyRow.put(round(low));
//                monthlyRow.put(round(close));
//                monthlyRow.put(volume);
//                monthlySummaryJson.put("MonthlySummary",monthlyRow);

                JSONObject js = new JSONObject();
                js.put("month", month);
                js.put("open", open);
                js.put("high", high);
                js.put("low", low);
                js.put("close", close);
                js.put("volume", volume);

                monthlyRow.put(js);
            }

            JSONArray jsonArray = new JSONArray();

            for (int i = dailyOHLCRecords.size() - 90; i < dailyOHLCRecords.size(); i++) {
                DailyOHLCRecords record = dailyOHLCRecords.get(i - 1);

                JSONObject js = new JSONObject();
                js.put("date", record.getDate());
                js.put("open", record.getOpen());
                js.put("high", record.getHigh());
                js.put("low", record.getLow());
                js.put("close", record.getClose());
                js.put("volume", record.getVolume());
                js.put("change percentage", record.getChangePct());
                js.put("Moving Average of 7 days", record.getMa7());
                js.put("Moving Average of 10 days", record.getMa10());
                js.put("Moving Average of 20 days", record.getMa20());
                js.put("Moving Average of 30 days", record.getMa30());
                js.put("Moving Average of 50 days", record.getMa50());

                if (record.getClose() < record.getBollLower())
                    js.put("bollingerband crossover", "lower band crossed");
                else if (record.getClose() > record.getBollUpper())
                    js.put("bollingerband crossover", "upper band crossed");
                else
                    js.put("bollingerband crossover", "In range");

                jsonArray.put(js);
            }

            data.put("Recent_Data", jsonArray);
            data.put("Monthly_summary", monthlyRow);

            LocalDate today = LocalDate.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            String formattedDate = today.format(formatter);

            String systemInstruction = String.format("""
                        You are a highly skilled technical stock market analyst.
                        Analyze the provided OHLCV data of %s company and generate detailed, date-specific, actionable insights.
                        Focus only on the data provided.
                        Include:
                        - Trend analysis (short/medium/long term)
                        - Support/resistance levels (date-specific)
                        - Candlestick patterns
                        - Volume analysis
                        - Breakouts/gaps
                        - Final outlook (bullish/bearish/neutral)
                    """, request.getCompanyName());

            // User prompt
            String userPrompt = String.format("""
                            Today's date is: %s

                            Stock Data:
                            - Recent 90 OHLCV Daily records with moving averages and bollinger band crossover is given: %s
                            - MonthlySummary OHLCV: %s

                            Your task:
                            1. Short-term (days to weeks) trend analysis
                            2. Medium-term (weeks to months) trend analysis
                            3. Identify key support and resistance levels (based on given recent_daily data refer the recent_daily_columns to understand find support and resistences also all the amount is in rupees)
                            4. Given the 60 days data in recent_daily data, detect moving average crossovers (10, 20, 50-day). Columns names are present in recent_daily_columnss
                            5. Analyse and find out the candlestick patterns with exact dates based on the data given 60 days data in recent_daily. Columns names are present in recent_daily_columns
                            6. Volume spikes or divergence patterns
                            8. Highlight any breakout or gap events
                            9. Provide a final trading outlook (buy/sell/hold) and reasoning
                            10. If no signals are present, explicitly note that

                            Only use the data provided above. Be analytical, concise, and reference exact dates.
                            While doing analysis keep in mind today's date: %s and do the analysis based on that as well.
                            """,
                    formattedDate,
                    data.get("Recent_Data"),
                    data.get("Monthly_summary"),
                    formattedDate
            );

            LOGGER.info("systemInstruction : " + systemInstruction);
            LOGGER.info("userPrompt: " + userPrompt);


            String analysisResponse = memoryChatClient.prompt()
                    .system(systemInstruction)
                    .user(userPrompt)
                    .call()
                    .content();

            return ResponseEntity.ok(analysisResponse);
        }

        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @PostMapping("/memory/askAboutChart")
    public ResponseEntity<?> imageToTextAskAboutChart(@RequestBody String input) {
        try {
//            float[] questionEmbedding = embeddingModel.embed(input);

            // Get top 10 relevant chunks
//            embeddingServiceNew.semanticSearch(input)
//            List<String> relevantChunks = vectorDB.search(questionEmbedding, 10);

//            String relevantChunks = vectorDB.answerWithContext(input);
//            String combinedContext = String.join("\n\n", relevantChunks);
//
//            LOGGER.info("relevant chunks: " + relevantChunks);

            String combinedContext = "";

            String systemPrompt = """
                        You are a professional technical analyst.
                        
                        You are answering user questions using only the analysis context below. 
                        Do NOT make up or guess new patterns or data. Base all your reasoning strictly on the information in the context.

                        Your answer MUST include:
                        - Exact date(s) for any event mentioned (patterns, volume spikes, price moves)
                        - Specific price levels when talking about support/resistance
                        - Reasoning based on the chart or CSV summary from the context
                        - Clear explanation for any trading suggestion (bullish/bearish/neutral)

                        Be accurate, specific, and helpful for a trader.
                    """;

            String userPrompt = """
                        Context:
                        %s

                        User Question:
                        %s

                        Based only on this context, answer with full explanation and date-specific evidence.
                    """.formatted(combinedContext, input);

            LOGGER.info("/memory/askAboutChart user prompt: " + userPrompt);

            String answer = memoryChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing request: " + e.getMessage());
        }
    }

//    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel, String fullText) {
//        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
//        TextReader textReader = new TextReader(fullText);
//        List<Document> documents = List.of(new Document(fullText));
//        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
//        List<Document> splitDocuments = tokenTextSplitter.apply(documents);
//
//        simpleVectorStore.add(splitDocuments);
//
//        LOGGER.info("stroed simple vector is: " + simpleVectorStore.toString());
//
//        return simpleVectorStore;
//    }

//    public List<TechnicalPattern> extractPatternsFromCSV(List<String[]> csvRows) {
//        List<TechnicalPattern> patterns = new ArrayList<>();
//        SimpleDateFormat inputDateFormat = new SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH);
//        SimpleDateFormat outputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
//
//        for (int i = 1; i < csvRows.size(); i++) {  // Start at 1 to compare with previous row
//            String[] today = csvRows.get(i);
//            String[] yesterday = csvRows.get(i - 1);
//
//            try {
//                // Make sure arrays have expected length to avoid IndexOutOfBoundsException
//                if (today.length < 7 || yesterday.length < 7) {
//                    System.err.println("Skipping row " + i + " due to insufficient columns");
//                    continue;
//                }
//
//                double todayOpen = Utility.parseDouble(today[1]);
//                double todayClose = Utility.parseDouble(today[6]);
//                double yesterdayOpen = Utility.parseDouble(yesterday[1]);
//                double yesterdayClose = Utility.parseDouble(yesterday[6]);
//
//                if (todayOpen < todayClose &&  // today bullish
//                        yesterdayOpen > yesterdayClose && // yesterday bearish
//                        todayOpen < yesterdayClose &&
//                        todayClose > yesterdayOpen) {
//
//                    TechnicalPattern tp = new TechnicalPattern();
//                    tp.patternName = "Bullish Engulfing";
//
//                    Date parsedDate = inputDateFormat.parse(today[0].trim());
//                    tp.date = outputDateFormat.format(parsedDate);
//
//                    tp.priceLevel = todayClose;
//                    tp.visibleInChart = false;
//                    tp.notes = "Strong bullish reversal candidate";
//
//                    patterns.add(tp);
//                }
//            } catch (ParseException | NumberFormatException e) {
//                System.err.println("Skipping row " + i + " due to parsing error: " + e.getMessage());
//            }
//        }
//
//        return patterns;
//    }

//    private double parseDouble(String value) throws NumberFormatException {
//        // Remove quotes and commas from the numeric string before parsing
//        String cleaned = value.replace("\"", "").replace(",", "");
//        return Double.parseDouble(cleaned);
//    }

//    public List<SupportResistanceLevel> extractSupportResistance(List<String[]> csvRows) {
//        double tolerance = 2.0;  // Price band to cluster levels
//        List<SupportResistanceLevel> levels = new ArrayList<>();
//
//        for (int i = 2; i < csvRows.size(); i++) {  // skip header
//            String[] row = csvRows.get(i);
//            try {
//                double high = Utility.parseDouble(row[2]);
//                double low = Utility.parseDouble(row[3]);
//                String date = row[0];
//
//                // Cluster resistance (high)
//                SupportResistanceLevel resistanceLevel = findLevel(levels, high, tolerance, "Resistance");
//                if (resistanceLevel == null) {
//                    resistanceLevel = new SupportResistanceLevel();
//                    resistanceLevel.priceLevel = high;
//                    resistanceLevel.levelType = "Resistance";
//                    levels.add(resistanceLevel);
//                }
//                resistanceLevel.touchedDates.add(date);
//
//                // Cluster support (low)
//                SupportResistanceLevel supportLevel = findLevel(levels, low, tolerance, "Support");
//                if (supportLevel == null) {
//                    supportLevel = new SupportResistanceLevel();
//                    supportLevel.priceLevel = low;
//                    supportLevel.levelType = "Support";
//                    levels.add(supportLevel);
//                }
//                supportLevel.touchedDates.add(date);
//
//            } catch (NumberFormatException e) {
//                System.err.println("Skipping row " + i + " due to number parse error: " + e.getMessage());
//            }
//        }
//
//        return levels;
//    }

//    private SupportResistanceLevel findLevel(List<SupportResistanceLevel> levels, double price, double tolerance, String levelType) {
//        for (SupportResistanceLevel lvl : levels) {
//            if (lvl.levelType.equals(levelType) && Math.abs(lvl.priceLevel - price) <= tolerance) {
//                // Optionally: update priceLevel to average or keep original
//                return lvl;
//            }
//        }
//        return null;
//    }

//    public List<String> detectVolumeSpikes(List<String[]> csvRows) {
//        List<String> spikeDates = new ArrayList<>();
//
//        for (int i = 6; i < csvRows.size(); i++) {  // Start from 6 to ensure at least 5 previous rows
//            try {
//                double currentVol = Utility.parseDouble(csvRows.get(i)[8]);
//                double avgPrevVol = 0;
//
//                for (int j = i - 5; j < i; j++) {
//                    avgPrevVol += Utility.parseDouble(csvRows.get(j)[8]);
//                }
//                avgPrevVol /= 5;
//
//                if (currentVol > 1.5 * avgPrevVol) {
//                    spikeDates.add(csvRows.get(i)[0]);
//                }
//
//            } catch (NumberFormatException e) {
//                System.err.println("Skipping row " + i + " due to volume parse error: " + e.getMessage());
//            }
//        }
//
//        return spikeDates;
//    }

    public String buildChainOfThoughtPrompt(List<TechnicalPattern> patterns, List<SupportResistanceLevel> levels, List<String> volumeSpikes, String currPrice, String startDate, String endDate) {
        StringBuilder sb = new StringBuilder();

        sb.append("You are an expert technical stock market analyst.\n");
        sb.append("Analyze the following structured trading data from ").append(startDate).append(" to ").append(endDate).append(", with current price at ").append(currPrice).append(".\n\n");

        sb.append("Step 1: Identify trends based on support and resistance levels.\n");
//        sb.append("Support and Resistance Levels (price with dates):\n");
//        for (SupportResistanceLevel lvl : levels) {
//            sb.append(String.format("- %s at %.2f touched on dates %s\n", lvl.levelType, lvl.priceLevel, String.join(", ", lvl.touchedDates)));
//        }
//        sb.append("\n");

        sb.append("Step 2: Detect candlestick patterns with exact dates and notes:\n");
        for (TechnicalPattern tp : patterns) {
            sb.append(String.format("- %s on %s at price %.2f (%s)\n", tp.patternName, tp.date, tp.priceLevel, tp.notes));
        }
        sb.append("\n");

        sb.append("Step 3: Note volume spikes on dates:\n");
        for (String date : volumeSpikes) {
            sb.append("- Volume spike on ").append(date).append("\n");
        }
        sb.append("\n");

        sb.append("Step 4: Cross-validate these with the chart image and note if patterns are visible or not.\n");
        sb.append("Provide a concise final trading outlook, specifying dates, price levels, and reasoning.\n");

        sb.append("\nAlways explain your reasoning step-by-step and refer to exact dates and price points.\n");

        return sb.toString();
    }

//    public void markPatternsVisibleInChart(List<TechnicalPattern> patterns, double visibleMinPrice, double visibleMaxPrice) {
//        for (TechnicalPattern tp : patterns) {
//            if (tp.priceLevel >= visibleMinPrice && tp.priceLevel <= visibleMaxPrice) {
//                tp.visibleInChart = true;
//            } else {
//                tp.visibleInChart = false;
//            }
//        }
//    }

//    public String buildCsvSummaryText(List<TechnicalPattern> patterns) {
//        if (patterns == null || patterns.isEmpty()) {
//            return "No technical patterns detected.";
//        }
//
//        StringBuilder sb = new StringBuilder();
//        sb.append("Detected Technical Patterns Summary:\n");
//        sb.append("------------------------------------------------\n");
//
//        for (TechnicalPattern tp : patterns) {
//            sb.append("Pattern: ").append(tp.patternName).append("\n");
//            sb.append("Date: ").append(tp.date).append("\n");
//            sb.append("Price Level: ").append(String.format("%.2f", tp.priceLevel)).append("\n");
//            sb.append("Visible in Chart: ").append(tp.visibleInChart ? "Yes" : "No").append("\n");
//            if (tp.notes != null && !tp.notes.isEmpty()) {
//                sb.append("Notes: ").append(tp.notes).append("\n");
//            }
//            sb.append("------------------------------------------------\n");
//        }
//
//        return sb.toString();
//    }

//    public double[] getVisiblePriceRangeFromCSV(List<String[]> csvRows) {
//        double minPrice = Double.MAX_VALUE;
//        double maxPrice = Double.MIN_VALUE;
//
//        // Assuming your LOW price is in column index 3 and HIGH price is in column index 2
//        // (based on your CSV format: Date, OPEN, HIGH, LOW, PREV. CLOSE, ltp, close, vwap, VOLUME)
//        for (int i = 1; i < csvRows.size(); i++) { // skip header at 0
//            String[] row = csvRows.get(i);
//
//            // Remove commas and parse as double
//            double low = Double.parseDouble(row[3].replace(",", ""));
//            double high = Double.parseDouble(row[2].replace(",", ""));
//
//            if (low < minPrice) minPrice = low;
//            if (high > maxPrice) maxPrice = high;
//        }
//
//        return new double[]{minPrice, maxPrice};
//    }
}