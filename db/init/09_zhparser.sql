-- ============================================================
-- 09_zhparser.sql —— 中文全文检索（zhparser + tsvector + GIN）
-- 依赖：镜像已编译安装 zhparser 扩展（见 db/Dockerfile）
-- 作用：为关键词召回提供真正可用的中文 tsvector，与向量召回做 RRF 融合
-- ============================================================

\c knowledge

CREATE EXTENSION IF NOT EXISTS zhparser;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'zh') THEN
        CREATE TEXT SEARCH CONFIGURATION zh (PARSER = zhparser);
        ALTER TEXT SEARCH CONFIGURATION zh
            ADD MAPPING FOR n,v,a,i,e,l,j WITH simple;
    END IF;
END $$;

-- 中文 tsvector 生成列 + GIN 索引（查询时不再现算）
ALTER TABLE document_chunk
    ADD COLUMN IF NOT EXISTS chunk_text_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('zh', chunk_text)) STORED;

CREATE INDEX IF NOT EXISTS idx_chunk_text_tsv_gin
    ON document_chunk USING gin (chunk_text_tsv);
