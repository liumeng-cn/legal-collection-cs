package com.legalcs.service.rag;

import com.legalcs.service.auth.AuthContext;
import com.legalcs.entity.RagChunk;
import java.util.List;

public interface Retriever {

    List<RagChunk> retrieve(String query, AuthContext authContext, int topK);
}
