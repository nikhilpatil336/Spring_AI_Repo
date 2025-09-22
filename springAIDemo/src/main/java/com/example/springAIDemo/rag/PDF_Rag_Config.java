package com.example.springAIDemo.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPooled;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
public class PDF_Rag_Config {

    private static final Logger log = LoggerFactory.getLogger(PDF_Rag_Config.class);

    private final String vectorStoreName = "PDF_Vectorstore.json";

    @Value("classpath:/data/SEBI_PDF.pdf")
    private Resource models;

    @Bean
    @Qualifier("PDF_VectorStore")
    SimpleVectorStore PDF_VectorStore(EmbeddingModel embeddingModel)
    {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        var vectorStoreFile = getVectorStoreFile();
        if(vectorStoreFile.exists())
        {
            log.info("Vector store already exists at {}", vectorStoreFile.getAbsolutePath());
            simpleVectorStore.load(vectorStoreFile);
        }
        else
        {
            log.info("Creating vector store at {}", vectorStoreFile.getAbsolutePath());
            var config = PdfDocumentReaderConfig.builder().withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder().build()).build();
            var pdfReader = new PagePdfDocumentReader(models, config);
            TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
            List<Document> splitDocuments = tokenTextSplitter.apply(pdfReader.get());
            simpleVectorStore.accept(splitDocuments);
            simpleVectorStore.save(vectorStoreFile);
        }

        return simpleVectorStore;
    }

    private File getVectorStoreFile()
    {
        Path path = Paths.get("src", "main", "resources", "data");
        String absolutePath = path.toFile().getAbsolutePath() + "/" + vectorStoreName;
        return new File(absolutePath);
    }
}