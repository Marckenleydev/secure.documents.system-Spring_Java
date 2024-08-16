package marc.dev.secure_document_system.service;

import marc.dev.secure_document_system.dto.api.IDocument;
import marc.dev.secure_document_system.dto.Document;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;


public interface DocumentService {
    Page<IDocument> getDocuments(int page, int size);
    Page<IDocument> getDocuments(int page, int size, String name);
    Collection<Document> saveDocuments(String userId, List<MultipartFile> documents);
    IDocument updateDocument(String documentId, String name, String description);
    void deleteDocument(String documentId);
    IDocument getDocumentByDocumentId(String documentId);
    Resource getResource(String documentName);
}
