package marc.dev.secure_document_system.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import marc.dev.secure_document_system.dto.api.IDocument;
import marc.dev.secure_document_system.dto.Document;
import marc.dev.secure_document_system.entity.DocumentEntity;
import marc.dev.secure_document_system.exception.ApiException;
import marc.dev.secure_document_system.repository.DocumentRepository;
import marc.dev.secure_document_system.repository.UserRepository;
import marc.dev.secure_document_system.service.DocumentService;
import marc.dev.secure_document_system.service.UserService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static marc.dev.secure_document_system.constant.Constants.FILE_STORAGE;
import static marc.dev.secure_document_system.utils.DocumentUtils.*;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.springframework.util.StringUtils.cleanPath;

@Service
@RequiredArgsConstructor
@Transactional(rollbackOn = Exception.class)
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final UserService userService;


    @Override
    public Page<IDocument> getDocuments(int page, int size) {
        return documentRepository.findDocuments(PageRequest.of(page, size, Sort.by("name")));
    }

    @Override
    public Page<IDocument> getDocuments(int page, int size, String name) {
        return documentRepository.findDocumentsByName(name,PageRequest.of(page, size, Sort.by("name")));

    }

    @Override
    public Collection<Document> saveDocuments(String userId, List<MultipartFile> documents) {

        List<Document> newDocuments = new ArrayList<>();
        var userEntity = userRepository.findUserByUserId(userId).get();


        var storage = Paths.get(FILE_STORAGE).toAbsolutePath().normalize();

        try{
            for(MultipartFile document:documents){
                var filename = cleanPath(Objects.requireNonNull(document.getOriginalFilename()));
                if("..".contains(filename)){
                    throw new ApiException(String.format("Invalid file Name: %s", filename));
                }

                var documentEntity = DocumentEntity
                        .builder().documentId(UUID.randomUUID().toString())
                        .name(filename)
                        .owner(userEntity)
                        .extension(getExtension(filename))
                        .uri(getDocumentUri(filename))
                        .formattedSize(byteCountToDisplaySize(document.getSize()))
                        .icon(setIcon((getExtension(filename))))
                        .build();

                var savedDocument = documentRepository.save(documentEntity);
                log.info(String.valueOf(savedDocument.getCreatedBy()));
                Files.copy(document.getInputStream(), storage.resolve(filename), REPLACE_EXISTING);
                Document newDocument = fromDocumentEntity(savedDocument, userService.getUserById(savedDocument.getCreatedBy()), userService.getUserById(savedDocument.getUpdatedBy()));
                newDocuments.add(newDocument);

            }
            return newDocuments;

        }catch(Exception exception){
            throw new ApiException(exception.getMessage());

        }

    }



    @Override
    public IDocument updateDocument(String documentId, String name, String description) {

        try{
            var documentEntity = getDocumentEntity(documentId);
            var document = Paths.get(FILE_STORAGE).resolve(documentEntity.getName()).toAbsolutePath().normalize();
            Files.move(document, document.resolveSibling(name), REPLACE_EXISTING);
            documentEntity.setName(name);
            documentEntity.setDescription(description);
            documentRepository.save(documentEntity);
            return  getDocumentByDocumentId(documentId);
        }catch(Exception exception){
            throw new ApiException("Unable to update document");
        }


    }

    private DocumentEntity getDocumentEntity(String documentId) {
        return documentRepository.findByDocumentId(documentId).orElseThrow(()-> new ApiException("Document not found"));
    }

    @Override
    public void deleteDocument(String documentId) {

    }

    @Override
    public IDocument getDocumentByDocumentId(String documentId) {

        return documentRepository.findDocumentByDocumentId(documentId).orElseThrow(()-> new ApiException("Document not found"));
    }

    @Override
    public Resource getResource(String documentName) {
        try{

            var filePath = Paths.get(FILE_STORAGE).toAbsolutePath().normalize().resolve(documentName);
           if(!Files.exists(filePath)){  throw new ApiException("Document not found");}
            return  new UrlResource(filePath.toUri());
        }catch(Exception exception){
            throw new ApiException("Unable to download document");
        }
    }
}
