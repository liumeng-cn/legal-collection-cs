package com.legalcs.service.rag;

import com.legalcs.dao.DocumentDAO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgVectorIndexer implements ApplicationRunner {

    private final DocumentDAO documentDao;

    @Override
    public void run(ApplicationArguments args) {
        if (documentDao.countChunks() == 0) {
            log.warn("document_chunk 为空，请运行 knowledge-ingest 管道离线入库");
        }
    }
}
