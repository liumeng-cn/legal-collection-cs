package com.legalcs.service.rag;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.DocumentDAO;
import com.legalcs.service.knowledge.EmbeddingClient;
import com.legalcs.entity.RagChunk;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PgVectorRetriever implements Retriever {

    private final DocumentDAO documentDao;
    private final EmbeddingClient embeddingClient;
    private final CaseScopeResolver caseScopeResolver;

    @Override
    public List<RagChunk> retrieve(String query, AuthContext authContext, int topK) {
        float[] embedding = embeddingClient.embed(query);
        return documentDao.search(embedding, topK,
                authContext.getRole().name(), caseScopeResolver.resolve(authContext));
    }
}
