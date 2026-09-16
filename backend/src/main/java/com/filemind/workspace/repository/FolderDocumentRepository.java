package com.filemind.workspace.repository;
import com.filemind.common.persistence.DynamoMetadataStore;
import com.filemind.common.persistence.DynamoRepository;
import com.filemind.workspace.entity.FolderDocument;
import org.springframework.stereotype.Repository;
@Repository public class FolderDocumentRepository extends DynamoRepository<FolderDocument> {
 public FolderDocumentRepository(DynamoMetadataStore store) { super(store, FolderDocument.class); }
 @Override protected String id(FolderDocument value) { return value.getId(); }
}
