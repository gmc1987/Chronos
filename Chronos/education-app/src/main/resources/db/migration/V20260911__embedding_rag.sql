ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS embedding_dimension integer;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS embedding_default boolean NOT NULL DEFAULT false;
ALTER TABLE kb_knowledge_base ADD COLUMN IF NOT EXISTS retrieval_mode varchar(16) NOT NULL DEFAULT 'KEYWORD';
ALTER TABLE kb_knowledge_base ADD COLUMN IF NOT EXISTS embedding_model_id varchar(64);
ALTER TABLE kb_knowledge_base ADD COLUMN IF NOT EXISTS allow_keyword_fallback boolean NOT NULL DEFAULT true;
ALTER TABLE kb_knowledge_base ADD COLUMN IF NOT EXISTS index_status varchar(24) NOT NULL DEFAULT 'NOT_INDEXED';
ALTER TABLE kb_knowledge_base ADD COLUMN IF NOT EXISTS index_error text;
ALTER TABLE kb_document_chunk ADD COLUMN IF NOT EXISTS embedding_indexed boolean NOT NULL DEFAULT false;
