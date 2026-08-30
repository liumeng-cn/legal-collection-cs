package com.legalcs.service.rag;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.dao.DocumentDAO;
import com.legalcs.client.DashScopeClient;
import com.legalcs.entity.RagChunk;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PgVectorRetriever implements Retriever {

    private final DocumentDAO documentDao;
    private final DashScopeClient dashScopeClient;
    private final CaseScopeResolver caseScopeResolver;

    @Override
    public List<RagChunk> retrieve(String query, AuthContext authContext, int topK) {
        float[] embedding = dashScopeClient.embed(query);
        return documentDao.search(embedding, topK,
                authContext.getRole().name(), caseScopeResolver.resolve(authContext));
    }
}
