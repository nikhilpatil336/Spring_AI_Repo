package com.example.springAIDemo.Controller;

import com.example.springAIDemo.model.FilePathRequest;
import com.example.springAIDemo.model.VectorDB;
import com.opencsv.CSVReader;
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

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Value("classpath:/images/reliance_3_month_chart_JanToMar.PNG")
    Resource sampleImage;

    public ImageController(ChatClient.Builder builder, ChatMemory chatMemory, OllamaChatModel ollamaChatModel) {
        this.ollamaChatClient = ChatClient.create(ollamaChatModel);
        this.memoryChatClient = builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    @GetMapping("/analyse")
    public ResponseEntity<?> imageToText()
    {
        return ResponseEntity.ok(ollamaChatClient.prompt().user(u-> {u.text("can you please describe what you see in the image.");
        u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);})
                .call()
                .content());
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

//    @PostMapping("/memory/ask")
//    public ResponseEntity<?> imageToTextAsk(@RequestBody String input) {
//        String systemPrompt = """
//        You are a professional technical stock market analyst.
//
//        You already have prior knowledge of the chart image and historical price data,
//        previously analyzed and stored in memory.
//
//        When answering questions:
//        - Use only the information previously analyzed and stored in memory.
//        - Rely on visual and numerical patterns stored from the chart image and CSV.
//        - Avoid assumptions not backed by past analysis.
//        - Keep answers technically sound, trader-focused, and concise.
//        - If the question is ambiguous, ask for clarification.
//
//        Output structure (if applicable):
//        - Direct answer
//        - Relevant chart/price context
//        - Final recommendation or insight
//
//        If the question is not related to stock chart analysis or price data, respond accordingly.
//        """;
//
//        return ResponseEntity.ok(
//                memoryChatClient.prompt()
//                        .user(input)
//                        .system(systemPrompt)
//                        .call()
//                        .content()
//        );
//    }

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


//    @GetMapping("/memory/analyseImageAndCSV")
//    public ResponseEntity<?> imageToTextWithCSVInMemory()
//    {
//        try (CSVReader reader = new CSVReader(new FileReader("C:/Users/nikhil.neosoft/Downloads/reliance-JanToMar.csv"))) {
//            List<String[]> rows = reader.readAll();
//
//            if (rows.size() < 2) {
//                return ResponseEntity.badRequest().body("CSV must contain at least one row of data.");
//            }
//
//            String[] headers = rows.get(0);
//            List<String[]> dataRows = rows.subList(1, Math.min(rows.size(), 70));
//
//            // Convert to text block
//            String tableData = dataRows.stream()
//                    .map(row -> String.join(" | ", row))
//                    .collect(Collectors.joining("\n"));
//
//            String headerLine = String.join(" | ", headers);
//
//            String csvText = """
//                    Here is stock price data (Daily):
//
//                    %s
//                    %s
//
//                    Please analyze this data from a technical analysis point of view.
//                    Include support/resistance, trend direction, patterns (e.g., bullish/bearish engulfing, hammer, etc.), and volume activity insights.
//                    """.formatted(headerLine, tableData);
//
//            var curr_price = "1248.70";
//            var start_date = "31st December 2024";
//            var last_date = "03rd April 2025";
//
//            var imageSystemInstruction = """
//                You are a technical stock market analyst.
//                we will provide you the image and the tableData of Open, High, Low, close ,volumn and date.
//                Analyze both the image and tableData to do the technical analysis. Combine both the resources to do the techincal analysis.
//                Include observations on trends, support/resistance levels, candlestick patterns, and volume changes.
//                Make your analysis readable and insightful.
//                The given image is of stock market chart with daily interval.
//                At the rightmost side of the image there is a price scale.
//                At bottom of the chart there is a date scale.
//                Start date of the chart is """ + start_date + " and last date is " + last_date + """
//                .
//                The current trading price in the chart is """ + curr_price + """
//                rupees.
//                Take this price as current price and based on that analyse the other prices.
//                analyse the price, candle stick and chart.
//                Analyse the image in detail from technical analysis point of view.
//                """;
//
//            LOGGER.info("table data is: "+ tableData);
//            LOGGER.info("Reading of the CSV file is done, now sending it to LLM.");
//
//            // Call LLM
////            String response = memoryChatClient.prompt()
////                    .user(csvText)
////                    .system(system)
////                    .call()
////                    .content();
//
//            return ResponseEntity.ok(memoryChatClient.prompt().user(u-> {u.text("analyse the image and keep that in memory.");
//                        u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);
//                        u.text(csvText);})
//                    .system(imageSystemInstruction)
//                    .call()
//                    .content());
//
//        } catch (Exception e) {
//            return ResponseEntity.internalServerError().body("Error processing CSV: " + e.getMessage());
//        }
//    }

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

            // Optional: Parse structured parts (or just store full text)
//            String analysisJson = extractStructuredOutput(fullAnalysis); // Or use fullAnalysis directly

            // Store analysis summary in DB or memory
//            storeStructuredAnalysis(analysisJson);

            // Split analysis into chunks for embedding
//            List<String> chunks = splitTextForEmbedding(fullAnalysis);
//            List<String> chunks = simpleVectorStore(embeddingModel, fullAnalysis);
//
//            for (String chunk : chunks) {
//                float[] embedding = embeddingClient.embed(chunk);
//                vectorDB.storeEmbedding(embedding, chunk, Map.of("source", "stock_analysis", "date", last_date));
//            }
            SimpleVectorStore store = simpleVectorStore(embeddingModel, fullAnalysis);

// If you still want to get chunks back from vector store:
//            List<Document> documents = store.similaritySearch("technical analysis", 10); // or whatever your query is
//
//// Optional: Index it into your custom VectorDB too (if you're not relying only on SimpleVectorStore)
//            for (Document doc : documents) {
//                float[] embedding = embeddingModel.embed(doc.getText()); // or use your own EmbeddingClient if needed
//                vectorDB.storeEmbedding(embedding, doc.getText(), Map.of("source", "stock_analysis", "date", last_date));
//            }

            LOGGER.info("Stored structured analysis and embeddings successfully.");

            return ResponseEntity.ok(fullAnalysis);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing CSV: " + e.getMessage());
        }
    }

//    public String extractStructuredOutput(String fullAnalysis) {
//        // A basic implementation. You can enhance it to return a proper JSON object or Map.
//        StringBuilder structured = new StringBuilder();
//        String[] sections = {
//                "Summary of trend",
//                "Key support/resistance levels",
//                "Important candlestick patterns found",
//                "Volume and price action insights",
//                "Trading outlook and recommendations"
//        };
//
//        for (String section : sections) {
//            int index = fullAnalysis.indexOf(section);
//            if (index >= 0) {
//                int nextSectionIndex = Arrays.stream(sections)
//                        .filter(s -> !s.equals(section))
//                        .mapToInt(s -> fullAnalysis.indexOf(s))
//                        .filter(i -> i > index)
//                        .min().orElse(fullAnalysis.length());
//
//                structured.append("### ").append(section).append("\n");
//                structured.append(fullAnalysis, index, nextSectionIndex).append("\n\n");
//            }
//        }
//
//        return structured.toString();
//    }

//    public void storeStructuredAnalysis(String structuredAnalysis) {
//        try {
//            Path path = Paths.get("structured_analysis.txt");
//            Files.writeString(path, structuredAnalysis, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//            LOGGER.info("Structured analysis stored at: " + path.toAbsolutePath());
//        } catch (IOException e) {
//            LOGGER.error("Failed to store structured analysis", e);
//        }
//    }

//    public List<String> splitTextForEmbedding(String fullText) {
//        return Arrays.stream(fullText.split("\n\n"))
//                .map(String::trim)
//                .filter(s -> s.length() > 30)
//                .collect(Collectors.toList());
//    }

    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel, String fullText)
    {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
            TextReader textReader = new TextReader(fullText);
//            textReader.getCustomMetadata().put("Filename", "models.txt");
//            List<Document> documents = textReader.get();
            List<Document> documents = List.of(new Document(fullText));
            TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            simpleVectorStore.add(splitDocuments);

            LOGGER.info("stroed simple vector is: " + simpleVectorStore.toString());
//            simpleVectorStore.save(vectorStoreFile);
//        }

        return simpleVectorStore;
    }


//    @PostMapping("/memory/askAboutChart")
//    public ResponseEntity<?> imageToTextAskAboutChart(@RequestBody String input) {
//        try {
//            float[] questionEmbedding = embeddingModel.embed(input);
//
//            // Retrieve top-k relevant analysis chunks from vector DB
//            List<String> relevantChunks = vectorDB.search(questionEmbedding, 10);
//
//            // Combine retrieved chunks as context
//            String combinedContext = String.join("\n\n", relevantChunks);
//
//            String prompt = """
//            You are an expert technical stock market analyst.
//            Use the following previously extracted analysis info to answer the user's question.
//            Do not re-analyze raw data or charts. Base your answer solely on the given context.
//
//            Context:
//            %s
//
//            User question:
//            %s
//            """.formatted(combinedContext, input);
//
//            String answer = memoryChatClient.prompt()
//                    .system("Answer based on provided context, be concise and actionable.")
//                    .user(prompt)
//                    .call()
//                    .content();
//
//            return ResponseEntity.ok(answer);
//        } catch (Exception e) {
//            return ResponseEntity.internalServerError().body("Error processing request: " + e.getMessage());
//        }
//    }

    @PostMapping("/memory/askAboutChart")
    public ResponseEntity<?> imageToTextAskAboutChart(@RequestBody String input) {
        try {
            float[] questionEmbedding = embeddingModel.embed(input);

            // Get top 10 relevant chunks
            List<String> relevantChunks = vectorDB.search(questionEmbedding, 10);
            String combinedContext = String.join("\n\n", relevantChunks);

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
}