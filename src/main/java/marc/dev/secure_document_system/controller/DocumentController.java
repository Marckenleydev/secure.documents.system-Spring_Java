package marc.dev.secure_document_system.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import marc.dev.secure_document_system.domain.Response;
import marc.dev.secure_document_system.dto.User;
import marc.dev.secure_document_system.dtorequest.UpdateDocRequest;
import marc.dev.secure_document_system.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;


import static marc.dev.secure_document_system.utils.RequestUtils.getResponse;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/documents" })
public class DocumentController {
    private final DocumentService documentService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('document:create') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> saveDocuments(@AuthenticationPrincipal User user, @RequestParam("files")List<MultipartFile> documents, HttpServletRequest request){

        var newDocuments = documentService.saveDocuments(user.getUserId(), documents);
        return ResponseEntity.created(URI.create("")).body(getResponse(request, Map.of("documents", newDocuments), "Document(s) uploaded Successfully", CREATED));
    }

    @GetMapping()
    @PreAuthorize("hasAnyAuthority('document:read') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> getDocuments(@AuthenticationPrincipal User user,HttpServletRequest request,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "5") int size){
        var documents = documentService.getDocuments(page, size);
        return ResponseEntity.ok().body(getResponse(request, Map.of("documents", documents), "Document(s) retrieved", OK));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('document:read') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> searchDocuments(@AuthenticationPrincipal User user,HttpServletRequest request,
                                                 @RequestParam(value = "page", defaultValue = "0") int page,
                                                 @RequestParam(value = "size", defaultValue = "5") int size,
                                                    @RequestParam(value = "name", defaultValue = "") String name){
        var documents = documentService.getDocuments(page, size,name);
        return ResponseEntity.ok().body(getResponse(request, Map.of("documents", documents), "Document(s) retrieved", OK));
    }

    @GetMapping("{documentId}")
    @PreAuthorize("hasAnyAuthority('document:read') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> getDocument(@AuthenticationPrincipal User user,HttpServletRequest request, @PathVariable("documentId") String documentId){
        var document = documentService.getDocumentByDocumentId(documentId);
        return ResponseEntity.ok().body(getResponse(request, Map.of("document", document), "Document retrieved", OK));
    }
    @PatchMapping()
    @PreAuthorize("hasAnyAuthority('document:update') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Response> updateDocument(@AuthenticationPrincipal User user, @RequestBody UpdateDocRequest document, HttpServletRequest request){
        var updateDocument = documentService.updateDocument(document.getDocumentId(), document.getName(), document.getDescription());
        return ResponseEntity.ok().body(getResponse(request, Map.of("documents", updateDocument), "Document Updated", OK));
    }

    @GetMapping("/download/{documentName}")
    @PreAuthorize("hasAnyAuthority('document:read') or hasAnyRole( 'ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Resource> downloadDocument(@AuthenticationPrincipal User user, @PathVariable("documentName") String documentName) throws IOException {
        var resource = documentService.getResource(documentName);
        var httpHeaders = new HttpHeaders();
        httpHeaders.add("File-Name", documentName);
        httpHeaders.add(CONTENT_DISPOSITION, String.format("attachment;File-Name=%s", resource.getFilename()));

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(Files.probeContentType(resource.getFile().toPath()))).headers(httpHeaders).body(resource);

    }


}
