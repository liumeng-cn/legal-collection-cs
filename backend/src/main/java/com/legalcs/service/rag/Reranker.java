package com.legalcs.service.rag;

import com.legalcs.entity.RagChunk;
import java.util.List;

public interface Reranker {

    List<RagChunk> rerank(String query, List<RagChunk> candidates, int topK);
}
