package com.legalcs.knowledge;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class KnowledgeDocument {

    private final long id;
    private final String title;
    private final String content;
    private final List<String> allowedRoles;
    private final Long caseId;
}
