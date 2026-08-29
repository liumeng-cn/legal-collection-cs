package com.legalcs.service.rag;

import com.legalcs.dao.DocumentDAO;
import com.legalcs.service.knowledge.EmbeddingClient;
import com.legalcs.entity.KnowledgeDocument;
import com.legalcs.service.knowledge.TextChunker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorIndexer implements Indexer, ApplicationRunner {

    private final DocumentDAO documentDao;
    private final EmbeddingClient embeddingClient;
    private final TextChunker textChunker;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (documentDao.countChunks() > 0) {
                log.info("知识库已有向量数据，跳过初始化");
                return;
            }
            ingestAll();
        } catch (Exception e) {
            log.warn("知识库初始化失败，RAG 检索暂不可用: {}", e.getMessage());
        }
    }

    @Override
    public void ingestAll() {
        List<KnowledgeDocument> documents = documentDao.findAll();
        for (KnowledgeDocument document : documents) {
            List<String> chunks = textChunker.chunk(document.getContent());
            for (int i = 0; i < chunks.size(); i++) {
                float[] embedding = embeddingClient.embed(chunks.get(i));
                documentDao.insertChunk(document.getId(), i, chunks.get(i), embedding);
            }
        }
        log.info("知识库初始化完成，共 {} 篇文档", documents.size());
    }
}
