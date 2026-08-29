package com.legalcs.service.rag;

import java.util.List;

public interface QueryRewriter {

    List<String> rewrite(String query);
}
