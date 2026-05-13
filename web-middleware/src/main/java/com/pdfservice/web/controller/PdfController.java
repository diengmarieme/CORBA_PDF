package com.pdfservice.web;

import PDFService.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/pdf")
public class PdfController {

    private static final Logger log = Logger.getLogger(PdfController.class.getName());

    @Autowired
    private CORBAClient corbaClient;

    // ============================================================
    // HEALTH CHECK
    // ============================================================

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        try {
            String pong = corbaClient.getService().ping();
            Map<String, String> resp = new HashMap<>();
            resp.put("status", pong);
            resp.put("message", "Service CORBA opérationnel");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, String> err = new HashMap<>();
            err.put("status", "ERROR");
            err.put("message", e.getMessage());
            return ResponseEntity.status(503).body(err);
        }
    }

    // ============================================================
    // 1. MERGE
    // ============================================================

    @PostMapping("/merge")
    public ResponseEntity<?> merge(@RequestParam("files") MultipartFile[] files) {
        log.info("[REST] merge() — " + files.length + " fichiers");
        try {
            if (files.length < 2) {
                return errorResponse("Au moins 2 fichiers sont requis pour la fusion.");
            }
            byte[][] pdfBytes = toByteArrays(files);
            byte[] result = corbaClient.getService().merge(pdfBytes);
            return pdfResponse(result, "merged.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 2. SPLIT
    // ============================================================

    @PostMapping("/split")
    public ResponseEntity<?> split(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "pagesPerPart", defaultValue = "1") int pagesPerPart) {
        log.info("[REST] split() — pagesPerPart=" + pagesPerPart);
        try {
            byte[] pdfBytes = file.getBytes();
            SplitResult result = corbaClient.getService().split(pdfBytes, pagesPerPart);
            if (result.partCount == 1) {
                return pdfResponse(result.parts[0], "split_part_1.pdf");
            }
            byte[] zip = buildZip(result.parts, "part_", ".pdf");
            return zipResponse(zip, "split_result.zip");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("PDF protégé : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 3. EXTRACT PAGES
    // ============================================================

    @PostMapping("/extract-pages")
    public ResponseEntity<?> extractPages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pagesStr) {
        log.info("[REST] extractPages() — pages=" + pagesStr);
        try {
            byte[] pdfBytes = file.getBytes();
            int[] pages = parsePageList(pagesStr);
            byte[] result = corbaClient.getService().extractPages(pdfBytes, pages);
            return pdfResponse(result, "extracted_pages.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("PDF protégé : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 4. DELETE PAGES
    // ============================================================

    @PostMapping("/delete-pages")
    public ResponseEntity<?> deletePages(
            @RequestParam("file") MultipartFile file,
            @RequestParam("pages") String pagesStr) {
        log.info("[REST] deletePages() — pages=" + pagesStr);
        try {
            byte[] pdfBytes = file.getBytes();
            int[] pages = parsePageList(pagesStr);
            byte[] result = corbaClient.getService().deletePages(pdfBytes, pages);
            return pdfResponse(result, "pages_deleted.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("PDF protégé : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 5. ENCRYPT
    // ============================================================

    @PostMapping("/encrypt")
    public ResponseEntity<?> encrypt(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userPassword") String userPassword,
            @RequestParam(value = "ownerPassword", defaultValue = "") String ownerPassword) {
        log.info("[REST] encrypt()");
        try {
            byte[] pdfBytes = file.getBytes();
            byte[] result = corbaClient.getService().encrypt(pdfBytes, userPassword, ownerPassword);
            return pdfResponse(result, "encrypted.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFEncryptionException e) {
            return errorResponse("Erreur de chiffrement : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 6. TO IMAGES
    // ============================================================

    @PostMapping("/to-images")
    public ResponseEntity<?> toImages(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dpi", defaultValue = "150") int dpi,
            @RequestParam(value = "password", defaultValue = "") String password) {
        log.info("[REST] toImages() — dpi=" + dpi);
        try {
            byte[] pdfBytes = file.getBytes();
            ImageConversionResult result = corbaClient.getService().toImages(pdfBytes, dpi, password);
            if (result.pageCount == 1) {
                return imageResponse(result.images[0], "page_1.png");
            }
            byte[] zip = buildZip(result.images, "page_", ".png");
            return zipResponse(zip, "pdf_images.zip");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 7. EXTRACT TEXT
    // ============================================================

    @PostMapping("/extract-text")
    public ResponseEntity<?> extractText(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", defaultValue = "") String password) {
        log.info("[REST] extractText()");
        try {
            byte[] pdfBytes = file.getBytes();
            TextExtractionResult result = corbaClient.getService().extractText(pdfBytes, password);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("pageCount",  result.pageCount);
            resp.put("fullText",   result.fullText);
            resp.put("pageTexts",  result.pageTexts);
            return ResponseEntity.ok(resp);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // 8. CREATE PDF
    // ============================================================

    @PostMapping("/create")
    public ResponseEntity<?> createPDF(
            @RequestParam("title")   String title,
            @RequestParam("content") String content,
            @RequestParam(value = "author", defaultValue = "PDF Service") String author) {
        log.info("[REST] createPDF() — titre: " + title);
        try {
            byte[] result = corbaClient.getService().createPDF(title, content, author);
            return pdfResponse(result, sanitizeFilename(title) + ".pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // BONUS 9. COMPRESS
    // ============================================================

    @PostMapping("/compress")
    public ResponseEntity<?> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dpi",            defaultValue = "120")   int    dpi,
            @RequestParam(value = "compressImages", defaultValue = "true")  String compressImagesStr,
            @RequestParam(value = "removeMetadata", defaultValue = "false") String removeMetadataStr) {
        log.info("[REST] compress() — dpi=" + dpi);
        try {
            byte[] pdfBytes = file.getBytes();
            CompressionParams params = new CompressionParams();
            params.dpi            = dpi;
            params.compressImages = Boolean.parseBoolean(compressImagesStr);
            params.removeMetadata = Boolean.parseBoolean(removeMetadataStr);

            byte[] result = corbaClient.getService().compress(pdfBytes, params);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"compressed.pdf\"")
                .header("X-Original-Size",   String.valueOf(pdfBytes.length))
                .header("X-Compressed-Size", String.valueOf(result.length))
                .contentType(MediaType.APPLICATION_PDF)
                .body(new ByteArrayResource(result));
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // BONUS 10. WATERMARK
    // ============================================================

    @PostMapping("/watermark")
    public ResponseEntity<?> addWatermark(
            @RequestParam("file")      MultipartFile file,
            @RequestParam("text")      String text,
            @RequestParam(value = "fontSize",  defaultValue = "48")   String fontSizeStr,
            @RequestParam(value = "opacity",   defaultValue = "0.3")  String opacityStr,
            @RequestParam(value = "rotation",  defaultValue = "45")   String rotationStr,
            @RequestParam(value = "colorR",    defaultValue = "128")  int    colorR,
            @RequestParam(value = "colorG",    defaultValue = "128")  int    colorG,
            @RequestParam(value = "colorB",    defaultValue = "128")  int    colorB,
            @RequestParam(value = "pages",     defaultValue = "")     String pagesStr,
            @RequestParam(value = "password",  defaultValue = "")     String password) {
        log.info("[REST] addWatermark() — texte=" + text);
        try {
            byte[] pdfBytes = file.getBytes();
            WatermarkParams params = new WatermarkParams();
            params.text            = text;
            params.fontSize        = Float.parseFloat(fontSizeStr);
            params.opacity         = Math.min(1.0f, Math.max(0.0f, Float.parseFloat(opacityStr)));
            params.rotationDegrees = Float.parseFloat(rotationStr);
            params.colorR          = colorR;
            params.colorG          = colorG;
            params.colorB          = colorB;
            params.applyToAllPages = pagesStr.isEmpty();
            params.targetPages     = pagesStr.isEmpty() ? new int[0] : parsePageList(pagesStr);

            byte[] result = corbaClient.getService().addWatermark(pdfBytes, params, password);
            return pdfResponse(result, "watermarked.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // BONUS 11. ROTATE
    // ============================================================

    @PostMapping("/rotate")
    public ResponseEntity<?> rotatePages(
            @RequestParam("file")    MultipartFile file,
            @RequestParam("degrees") int degrees,
            @RequestParam(value = "pages",    defaultValue = "") String pagesStr,
            @RequestParam(value = "password", defaultValue = "") String password) {
        log.info("[REST] rotatePages() — degrés=" + degrees);
        try {
            byte[] pdfBytes = file.getBytes();
            RotationParams params = new RotationParams();
            params.degrees = degrees;
            params.pages   = pagesStr.isEmpty() ? new int[0] : parsePageList(pagesStr);
            byte[] result = corbaClient.getService().rotatePages(pdfBytes, params, password);
            return pdfResponse(result, "rotated.pdf");
        } catch (InvalidParameterException e) {
            return errorResponse("Paramètre invalide [" + e.paramName + "] : " + e.reason);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // BONUS 12. EXTRACT IMAGES
    // ============================================================

    @PostMapping("/extract-images")
    public ResponseEntity<?> extractImages(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", defaultValue = "") String password) {
        log.info("[REST] extractImages()");
        try {
            byte[] pdfBytes = file.getBytes();
            EmbeddedImageResult result = corbaClient.getService().extractImages(pdfBytes, password);
            if (result.imageCount == 0) {
                return errorResponse("Aucune image embarquée trouvée dans ce PDF.");
            }
            if (result.imageCount == 1) {
                return imageResponse(result.images[0], "image_1.png");
            }
            byte[] zip = buildZip(result.images, "image_", ".png");
            return zipResponse(zip, "extracted_images.zip");
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // METADATA
    // ============================================================

    @PostMapping("/metadata")
    public ResponseEntity<?> getMetadata(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "password", defaultValue = "") String password) {
        log.info("[REST] getMetadata()");
        try {
            byte[] pdfBytes = file.getBytes();
            PDFMetadata meta = corbaClient.getService().getMetadata(pdfBytes, password);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("title",         meta.title);
            resp.put("author",        meta.author);
            resp.put("subject",       meta.subject);
            resp.put("creator",       meta.creator);
            resp.put("pageCount",     meta.pageCount);
            resp.put("fileSizeBytes", meta.fileSizeBytes);
            resp.put("isEncrypted",   meta.isEncrypted);
            resp.put("pdfVersion",    meta.pdfVersion);
            return ResponseEntity.ok(resp);
        } catch (PDFProcessingException e) {
            return errorResponse("[" + e.errorCode + "] " + e.message);
        } catch (PDFPasswordException e) {
            return errorResponse("Mot de passe incorrect : " + e.message);
        } catch (Exception e) {
            return errorResponse("Erreur inattendue : " + e.getMessage());
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private byte[][] toByteArrays(MultipartFile[] files) throws IOException {
        byte[][] result = new byte[files.length][];
        for (int i = 0; i < files.length; i++) {
            result[i] = files[i].getBytes();
        }
        return result;
    }

    private int[] parsePageList(String pagesStr) {
        List<Integer> pages = new ArrayList<>();
        for (String token : pagesStr.split(",")) {
            token = token.trim();
            if (token.contains("-")) {
                String[] range = token.split("-");
                int from = Integer.parseInt(range[0].trim());
                int to   = Integer.parseInt(range[1].trim());
                for (int i = from; i <= to; i++) pages.add(i);
            } else if (!token.isEmpty()) {
                pages.add(Integer.parseInt(token));
            }
        }
        return pages.stream().mapToInt(Integer::intValue).toArray();
    }

    private byte[] buildZip(byte[][] files, String prefix, String extension) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < files.length; i++) {
                zos.putNextEntry(new ZipEntry(prefix + (i + 1) + extension));
                zos.write(files[i]);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private ResponseEntity<ByteArrayResource> pdfResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(data.length)
            .body(new ByteArrayResource(data));
    }

    private ResponseEntity<ByteArrayResource> imageResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.IMAGE_PNG)
            .contentLength(data.length)
            .body(new ByteArrayResource(data));
    }

    private ResponseEntity<ByteArrayResource> zipResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("application/zip"))
            .contentLength(data.length)
            .body(new ByteArrayResource(data));
    }

    private ResponseEntity<Map<String, String>> errorResponse(String message) {
        log.warning("[REST] Erreur : " + message);
        Map<String, String> err = new HashMap<>();
        err.put("error",   "true");
        err.put("message", message);
        return ResponseEntity.badRequest().body(err);
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }
}
