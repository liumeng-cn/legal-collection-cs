package com.legalcs.knowledge;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIngestionService implements ApplicationRunner {

    private final DocumentDao documentDao;
    private final EmbeddingClient embeddingClient;
    private final TextChunker textChunker;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (documentDao.countChunks() > 0) {
                log.info("知识库已有向量数据，跳过初始化");
                return;
            }
            List<KnowledgeDocument> documents = documentDao.findAll();
            for (KnowledgeDocument document : documents) {
                for (String chunk : textChunker.chunk(document.getContent())) {
                    float[] embedding = embeddingClient.embed(chunk);
                    documentDao.insertChunk(document.getId(), chunk, embedding);
                }
            }
            log.info("知识库初始化完成，共 {} 篇文档", documents.size());
        } catch (Exception e) {
            log.warn("知识库初始化失败，RAG 检索暂不可用: {}", e.getMessage());
        }
    }
}
