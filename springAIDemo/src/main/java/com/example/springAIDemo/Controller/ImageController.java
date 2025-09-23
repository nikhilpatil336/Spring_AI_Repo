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
    public ResponseEntity<?> imageToTextInMemory()
    {
        var curr_price = "1248.70";
        var start_date = "31st December 2024";
        var last_date = "03rd April 2025";

        var systemInstruction = """
                The given image is of stock market chart with daily interval.
                At the rightmost side of the image there is a price scale.
                At bottom of the chart there is a date scale.
                Start date of the chart is """ + start_date + " and last date is " + last_date + """
                .
                The current trading price in the chart is """ + curr_price + """ 
                rupees.
                Take this price as current price and based on that analyse the other prices.
                The chart is divided into 2 parts using a blue horizontal lines, it's not a support or any other things.
                Its just a horizontal line for your to take the reference or price and shape of candle.
                This is added for you to understand better scale of image and candle stick and prices.
                At the rightmost side of the horizontal lines on price scale price highlighted in blue for that line.
                Use that price as reference to analyse the price, candle stick and chart.
                Analyse the image in detail from technical analysis point of view.
                """;

        return ResponseEntity.ok(memoryChatClient.prompt().user(u-> {u.text("analyse the image and keep that in memory.");
                    u.media(MimeTypeUtils.IMAGE_PNG, sampleImage);})
                .system(systemInstruction)
                .call()
                .content());
    }

    @PostMapping("/memory/ask")
    public ResponseEntity<?> imageToTextAsk(@RequestBody String input)
    {
        return ResponseEntity.ok(memoryChatClient.prompt().user(input)
                .call()
                .content());
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
                You are a highly experienced technical stock market analyst specialized in combining chart image analysis and numerical OHLC data analysis for comprehensive insights.

                Data Overview:
                - Chart image represents daily candlestick price action for a stock.
                - CSV data contains daily Date, OPEN, HIGH, LOW, PREV. CLOSE, LTP, CLOSE, VWAP, and VOLUME for the same period.
                - The date range starts from """+ start_date + """
                 and ends on """ + last_date + """
                .
                - The current trading price is """ + curr_price + """
                 rupees (given in the image).

                Your Tasks:
                1. Analyze the candlestick chart image for patterns, trends, and key support/resistance levels. Consider candle shapes (doji, hammer, engulfing, etc.), wick length, and closing price position.
                2. Analyze the CSV data for price movement, volume trends, gaps, and statistical support/resistance levels.
                3. Combine insights from both sources to provide:
                   - Confirmation or contradictions between chart visual and numerical data
                   - Volume activity insights and implications
                   - Clear trend direction (uptrend, downtrend, consolidation)
                   - Identification of significant technical patterns (bullish/bearish engulfing, hammer, shooting star, etc.)
                   - Important price levels acting as support or resistance
                   - Potential trade signals or cautionary signals
                   - **For each identified chart pattern, specify the corresponding date(s) or data point(s) from the CSV where it appeared.**
                4. Keep the analysis concise, focused, and actionable, suitable for informed trading decisions.
                5. Consider the current price (""" + curr_price + """
                 rupees) and analyze relative to historical data.
                6. If discrepancies arise between image and CSV, note and reason about them.

                Note: Use the image primarily for visual pattern recognition and the CSV for precise numerical confirmation. Combine both effectively.

                When responding, structure your output as:
                - Summary of trend
                - Key support/resistance levels
                - Important candlestick patterns found
                - Volume and price action insights
                - Trading outlook and recommendations based on combined data

                Provide clear, professional, and data-backed technical analysis.

                End of instruction.
                """;

            LOGGER.info("Sending summary and detailed CSV snippet along with image for analysis...");

            String fullAnalysis = memoryChatClient.prompt()
                    .user(u -> {
                        u.text("Analyze the chart image and the following summarized CSV data together for a comprehensive technical analysis.");
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
            List<Document> documents = textReader.get();
            TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            simpleVectorStore.add(splitDocuments);

            LOGGER.info("stroed simple vector is: " + simpleVectorStore.toString());
//            simpleVectorStore.save(vectorStoreFile);
//        }

        return simpleVectorStore;
    }


    @PostMapping("/memory/askAboutChart")
    public ResponseEntity<?> imageToTextAskAboutChart(@RequestBody String input) {
        try {
            float[] questionEmbedding = embeddingModel.embed(input);

            // Retrieve top-k relevant analysis chunks from vector DB
            List<String> relevantChunks = vectorDB.search(questionEmbedding, 10);

            // Combine retrieved chunks as context
            String combinedContext = String.join("\n\n", relevantChunks);

            String prompt = """
            You are an expert technical stock market analyst.
            Use the following previously extracted analysis info to answer the user's question.
            Do not re-analyze raw data or charts. Base your answer solely on the given context.

            Context:
            %s

            User question:
            %s
            """.formatted(combinedContext, input);

            String answer = memoryChatClient.prompt()
                    .system("Answer based on provided context, be concise and actionable.")
                    .user(prompt)
                    .call()
                    .content();

            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error processing request: " + e.getMessage());
        }
    }
}