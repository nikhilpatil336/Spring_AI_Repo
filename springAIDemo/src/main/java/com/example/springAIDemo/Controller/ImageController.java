package com.example.springAIDemo.Controller;

import com.example.springAIDemo.model.FilePathRequest;
import com.opencsv.CSVReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.*;

import java.io.FileReader;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/image")
public class ImageController {

    private Logger LOGGER = LoggerFactory.getLogger(ImageController.class);

    private ChatClient ollamaChatClient;
    private ChatClient memoryChatClient;

    @Value("classpath:/images/reliance_chart_with lines_1.PNG")
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
        var curr_price = "1377.90";
        var start_date = "18th march 2025";
        var last_date = "9th Sept 2025";

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
}