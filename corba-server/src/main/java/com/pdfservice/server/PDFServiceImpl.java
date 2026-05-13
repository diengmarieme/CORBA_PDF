package com.pdfservice.server;
import PDFService.*;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Implémentation CORBA du service PDF.
 * Toutes les opérations définies dans service.idl sont implémentées ici
 * avec Apache PDFBox 2.x.
 */
public class PDFServiceImpl extends IPDFServicePOA {

    // ================================================================
    // UTILITAIRES INTERNES
    // ================================================================

    /** Charge un PDDocument depuis un tableau de bytes. */
    private PDDocument loadDocument(byte[] data) throws IOException {
        return PDDocument.load(data);
    }

    /** Charge un PDDocument protégé par mot de passe. */
    private PDDocument loadDocument(byte[] data, String password) throws IOException {
        if (password == null || password.isEmpty()) {
            return PDDocument.load(data);
        }
        return PDDocument.load(data, password);
    }

    /** Sérialise un PDDocument en tableau de bytes puis le ferme. */
    private byte[] saveAndClose(PDDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        doc.close();
        return baos.toByteArray();
    }

    /** Sérialise un BufferedImage en PNG bytes. */
    private byte[] imageToBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    /** Convertit int[] (IDL long[]) en List<Integer>. */
    private List<Integer> toIntList(int[] arr) {
        List<Integer> list = new ArrayList<>();
        for (int v : arr) list.add(v);
        return list;
    }

    // ================================================================
    // HEALTH CHECK
    // ================================================================

    @Override
    public String ping() {
        System.out.println("[PDFService] ping() appelé");
        return "OK";
    }

    // ================================================================
    // 1. MERGE — Fusion de PDFs
    // ================================================================

    @Override
    public byte[] merge(byte[][] pdfFiles)
            throws PDFProcessingException, InvalidParameterException {

        System.out.println("[PDFService] merge() — " + pdfFiles.length + " fichiers");

        if (pdfFiles == null || pdfFiles.length < 2) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "pdfFiles";
            ex.reason = "Au moins 2 fichiers PDF sont requis pour la fusion.";
            throw ex;
        }

        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        merger.setDestinationStream(baos);

        try {
            for (byte[] pdf : pdfFiles) {
                merger.addSource(new ByteArrayInputStream(pdf));
            }
            merger.mergeDocuments(null);
            return baos.toByteArray();
        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la fusion : " + e.getMessage();
            ex.errorCode = "MERGE_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 2. SPLIT — Découpage de PDF
    // ================================================================

    @Override
    public SplitResult split(byte[] pdfFile, int pagesPerPart)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] split() — " + pagesPerPart + " pages/partie");

        if (pagesPerPart < 1) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "pagesPerPart";
            ex.reason = "Le nombre de pages par partie doit être >= 1.";
            throw ex;
        }

        try (PDDocument doc = loadDocument(pdfFile)) {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(pagesPerPart);
            List<PDDocument> parts = splitter.split(doc);

            byte[][] resultParts = new byte[parts.size()][];
            for (int i = 0; i < parts.size(); i++) {
                resultParts[i] = saveAndClose(parts.get(i));
            }

            SplitResult result = new SplitResult();
            result.parts = resultParts;
            result.partCount = resultParts.length;
            return result;

        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors du découpage : " + e.getMessage();
            ex.errorCode = "SPLIT_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 3. EXTRACT PAGES — Extraction de pages spécifiques
    // ================================================================

    @Override
    public byte[] extractPages(byte[] pdfFile, int[] pages)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] extractPages() — pages: " + Arrays.toString(pages));

        if (pages == null || pages.length == 0) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "pages";
            ex.reason = "La liste de pages ne peut pas être vide.";
            throw ex;
        }

        try (PDDocument source = loadDocument(pdfFile)) {
            PDDocument result = new PDDocument();
            int totalPages = source.getNumberOfPages();

            for (int pageNum : pages) {
                if (pageNum < 1 || pageNum > totalPages) {
                    InvalidParameterException ex = new InvalidParameterException();
                    ex.paramName = "pages";
                    ex.reason = "Page " + pageNum + " hors limites (1-" + totalPages + ").";
                    throw ex;
                }
                result.addPage(source.getPage(pageNum - 1));
            }

            return saveAndClose(result);

        } catch (InvalidParameterException e) {
            throw e;
        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de l'extraction : " + e.getMessage();
            ex.errorCode = "EXTRACT_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 4. DELETE PAGES — Suppression de pages
    // ================================================================

    @Override
    public byte[] deletePages(byte[] pdfFile, int[] pages)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] deletePages() — pages: " + Arrays.toString(pages));

        if (pages == null || pages.length == 0) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "pages";
            ex.reason = "La liste de pages à supprimer ne peut pas être vide.";
            throw ex;
        }

        try (PDDocument source = loadDocument(pdfFile)) {
            int totalPages = source.getNumberOfPages();
            Set<Integer> toDelete = new HashSet<>();
            for (int p : pages) {
                if (p < 1 || p > totalPages) {
                    InvalidParameterException ex = new InvalidParameterException();
                    ex.paramName = "pages";
                    ex.reason = "Page " + p + " hors limites (1-" + totalPages + ").";
                    throw ex;
                }
                toDelete.add(p - 1); // base 0
            }

            if (toDelete.size() >= totalPages) {
                InvalidParameterException ex = new InvalidParameterException();
                ex.paramName = "pages";
                ex.reason = "Impossible de supprimer toutes les pages du PDF.";
                throw ex;
            }

            PDDocument result = new PDDocument();
            for (int i = 0; i < totalPages; i++) {
                if (!toDelete.contains(i)) {
                    result.addPage(source.getPage(i));
                }
            }

            return saveAndClose(result);

        } catch (InvalidParameterException e) {
            throw e;
        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la suppression de pages : " + e.getMessage();
            ex.errorCode = "DELETE_PAGES_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 5. ENCRYPT — Chiffrement AES-256
    // ================================================================

    @Override
    public byte[] encrypt(byte[] pdfFile, String userPassword, String ownerPassword)
            throws PDFProcessingException, InvalidParameterException, PDFEncryptionException {

        System.out.println("[PDFService] encrypt()");

        if (userPassword == null || userPassword.isEmpty()) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "userPassword";
            ex.reason = "Le mot de passe utilisateur ne peut pas être vide.";
            throw ex;
        }
        if (ownerPassword == null || ownerPassword.isEmpty()) {
            ownerPassword = userPassword + "_owner";
        }

        try (PDDocument doc = loadDocument(pdfFile)) {
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(true);
            ap.setCanExtractContent(false);
            ap.setCanModify(false);

            StandardProtectionPolicy policy =
                new StandardProtectionPolicy(ownerPassword, userPassword, ap);
            policy.setEncryptionKeyLength(256); // AES-256
            policy.setPreferAES(true);

            doc.protect(policy);
            return saveAndClose(doc);

        } catch (IOException e) {
            PDFEncryptionException ex = new PDFEncryptionException();
            ex.message = "Erreur lors du chiffrement : " + e.getMessage();
            throw ex;
        }
    }

    // ================================================================
    // 6. TO IMAGES — Conversion PDF → PNG
    // ================================================================

    @Override
    public ImageConversionResult toImages(byte[] pdfFile, int dpi, String password)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] toImages() — dpi=" + dpi);

        if (dpi < 72 || dpi > 600) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "dpi";
            ex.reason = "DPI doit être compris entre 72 et 600.";
            throw ex;
        }

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();

            byte[][] images    = new byte[pageCount][];
            String[] mimeTypes = new String[pageCount];

            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                images[i]    = imageToBytes(img);
                mimeTypes[i] = "image/png";
            }

            ImageConversionResult result = new ImageConversionResult();
            result.images    = images;
            result.mimeTypes = mimeTypes;
            result.pageCount = pageCount;
            return result;

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la conversion en images : " + e.getMessage();
            ex.errorCode = "TO_IMAGES_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 7. EXTRACT TEXT — Extraction de texte
    // ================================================================

    @Override
    public TextExtractionResult extractText(byte[] pdfFile, String password)
            throws PDFProcessingException, PDFPasswordException {

        System.out.println("[PDFService] extractText()");

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            int pageCount = doc.getNumberOfPages();
            String[] pageTexts = new String[pageCount];

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();

            for (int i = 0; i < pageCount; i++) {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(doc);
                pageTexts[i] = text;
                fullText.append(text);
            }

            TextExtractionResult result = new TextExtractionResult();
            result.fullText  = fullText.toString();
            result.pageTexts = pageTexts;
            result.pageCount = pageCount;
            return result;

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de l'extraction du texte : " + e.getMessage();
            ex.errorCode = "EXTRACT_TEXT_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // 8. CREATE PDF — Création d'un PDF depuis du texte
    // ================================================================

    @Override
    public byte[] createPDF(String title, String content, String author)
            throws PDFProcessingException, InvalidParameterException {

        System.out.println("[PDFService] createPDF() — titre: " + title);

        if (title == null || title.isEmpty()) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "title";
            ex.reason = "Le titre ne peut pas être vide.";
            throw ex;
        }
        if (content == null || content.isEmpty()) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "content";
            ex.reason = "Le contenu ne peut pas être vide.";
            throw ex;
        }

        try {
            PDDocument doc = new PDDocument();
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle(title);
            info.setAuthor(author != null ? author : "PDF Service");
            info.setCreator("PDF CORBA Service");

            // Découpe le contenu en lignes
            String[] lines = content.split("\n");
            float margin     = 50f;
            float yStart     = PDRectangle.A4.getHeight() - margin;
            float lineHeight = 15f;
            float fontSize   = 12f;
            float titleSize  = 18f;

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            // Titre
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, titleSize);
            cs.newLineAtOffset(margin, yStart);
            cs.showText(title);
            cs.endText();

            float y = yStart - titleSize - 10f;

            // Corps du texte avec pagination automatique
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, fontSize);
            cs.newLineAtOffset(margin, y);

            for (String line : lines) {
                if (y < margin + lineHeight) {
                    // Nouvelle page
                    cs.endText();
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, fontSize);
                    y = yStart;
                    cs.newLineAtOffset(margin, y);
                }
                // Tronque les lignes trop longues
                String safeLine = line.length() > 100 ? line.substring(0, 100) : line;
                cs.showText(safeLine);
                cs.newLineAtOffset(0, -lineHeight);
                y -= lineHeight;
            }

            cs.endText();
            cs.close();

            return saveAndClose(doc);

        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la création du PDF : " + e.getMessage();
            ex.errorCode = "CREATE_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // BONUS 9. COMPRESS — Compression du PDF
    // ================================================================

    @Override
    public byte[] compress(byte[] pdfFile, CompressionParams params)
            throws PDFProcessingException, InvalidParameterException {

        System.out.println("[PDFService] compress() — dpi=" + params.dpi);

        if (params.dpi < 50 || params.dpi > 300) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "dpi";
            ex.reason = "DPI de compression doit être entre 50 et 300.";
            throw ex;
        }

        try (PDDocument doc = loadDocument(pdfFile)) {
            if (params.removeMetadata) {
                PDDocumentInformation info = new PDDocumentInformation();
                doc.setDocumentInformation(info);
            }

            if (params.compressImages) {
                for (PDPage page : doc.getPages()) {
                    PDResources resources = page.getResources();
                    if (resources == null) continue;
                    for (COSName xObjectName : resources.getXObjectNames()) {
                        try {
                            org.apache.pdfbox.pdmodel.graphics.PDXObject xObject =
                                resources.getXObject(xObjectName);
                            if (xObject instanceof PDImageXObject) {
                                PDImageXObject image = (PDImageXObject) xObject;
                                BufferedImage bImage = image.getImage();
                                // Redimensionne selon DPI cible
                                int newW = Math.max(1, (int)(bImage.getWidth()  * params.dpi / 150.0));
                                int newH = Math.max(1, (int)(bImage.getHeight() * params.dpi / 150.0));
                                BufferedImage resized = new BufferedImage(newW, newH,
                                    BufferedImage.TYPE_INT_RGB);
                                Graphics2D g2d = resized.createGraphics();
                                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g2d.drawImage(bImage, 0, 0, newW, newH, null);
                                g2d.dispose();

                                PDImageXObject compressed =
                                    PDImageXObject.createFromByteArray(doc,
                                        imageToBytes(resized), "img");
                                resources.put(xObjectName, compressed);
                            }
                        } catch (Exception ignored) {
                            // On ignore les XObjects non-images
                        }
                    }
                }
            }

            return saveAndClose(doc);

        } catch (IOException e) {
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la compression : " + e.getMessage();
            ex.errorCode = "COMPRESS_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // BONUS 10. WATERMARK — Filigrane personnalisable
    // ================================================================

    @Override
    public byte[] addWatermark(byte[] pdfFile, WatermarkParams params, String password)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] addWatermark() — texte: " + params.text);

        if (params.text == null || params.text.isEmpty()) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "text";
            ex.reason = "Le texte du filigrane ne peut pas être vide.";
            throw ex;
        }

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            int pageCount = doc.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                boolean shouldApply = params.applyToAllPages;
                if (!shouldApply) {
                    for (int target : params.targetPages) {
                        if (target - 1 == i) { shouldApply = true; break; }
                    }
                }
                if (!shouldApply) continue;

                PDPage page = doc.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();
                float cx = mediaBox.getWidth()  / 2f;
                float cy = mediaBox.getHeight() / 2f;

                PDPageContentStream cs = new PDPageContentStream(
                    doc, page,
                    PDPageContentStream.AppendMode.APPEND,
                    true, true
                );

                // Transparence
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(params.opacity);
                gs.setAlphaSourceFlag(true);
                cs.setGraphicsStateParameters(gs);

                // Couleur
                cs.setNonStrokingColor(
                    params.colorR / 255f,
                    params.colorG / 255f,
                    params.colorB / 255f
                );

                // Rotation et positionnement au centre
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, params.fontSize);

                double rad = Math.toRadians(params.rotationDegrees);
                float cosA = (float) Math.cos(rad);
                float sinA = (float) Math.sin(rad);

                cs.setTextMatrix(cosA, sinA, -sinA, cosA, cx, cy);
                cs.showText(params.text);
                cs.endText();
                cs.close();
            }

            return saveAndClose(doc);

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de l'ajout du filigrane : " + e.getMessage();
            ex.errorCode = "WATERMARK_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // BONUS 11. ROTATE PAGES — Rotation de pages
    // ================================================================

    @Override
    public byte[] rotatePages(byte[] pdfFile, RotationParams params, String password)
            throws PDFProcessingException, InvalidParameterException, PDFPasswordException {

        System.out.println("[PDFService] rotatePages() — degrés=" + params.degrees);

        int deg = params.degrees;
        if (deg != 90 && deg != 180 && deg != 270) {
            InvalidParameterException ex = new InvalidParameterException();
            ex.paramName = "degrees";
            ex.reason = "La rotation doit être 90, 180 ou 270 degrés.";
            throw ex;
        }

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            int totalPages = doc.getNumberOfPages();
            boolean allPages = (params.pages == null || params.pages.length == 0);

            for (int i = 0; i < totalPages; i++) {
                boolean apply = allPages;
                if (!allPages) {
                    for (int p : params.pages) {
                        if (p - 1 == i) { apply = true; break; }
                    }
                }
                if (!apply) continue;

                PDPage page = doc.getPage(i);
                int current = page.getRotation();
                page.setRotation((current + deg) % 360);
            }

            return saveAndClose(doc);

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la rotation : " + e.getMessage();
            ex.errorCode = "ROTATE_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // BONUS 12. EXTRACT IMAGES — Extraction d'images embarquées
    // ================================================================

    @Override
    public EmbeddedImageResult extractImages(byte[] pdfFile, String password)
            throws PDFProcessingException, PDFPasswordException {

        System.out.println("[PDFService] extractImages()");

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            List<byte[]>  imageList    = new ArrayList<>();
            List<String>  mimeTypeList = new ArrayList<>();

            for (PDPage page : doc.getPages()) {
                PDResources resources = page.getResources();
                if (resources == null) continue;
                for (COSName xObjectName : resources.getXObjectNames()) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.PDXObject xObject =
                            resources.getXObject(xObjectName);
                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject img = (PDImageXObject) xObject;
                            BufferedImage bImg = img.getImage();
                            imageList.add(imageToBytes(bImg));
                            mimeTypeList.add("image/png");
                        }
                    } catch (Exception ignored) { /* XObject non-image, on passe */ }
                }
            }

            EmbeddedImageResult result = new EmbeddedImageResult();
            result.images     = imageList.toArray(new byte[0][]);
            result.mimeTypes  = mimeTypeList.toArray(new String[0]);
            result.imageCount = imageList.size();
            return result;

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de l'extraction d'images : " + e.getMessage();
            ex.errorCode = "EXTRACT_IMAGES_ERROR";
            throw ex;
        }
    }

    // ================================================================
    // UTILITAIRE — Métadonnées PDF
    // ================================================================

    @Override
    public PDFMetadata getMetadata(byte[] pdfFile, String password)
            throws PDFProcessingException, PDFPasswordException {

        System.out.println("[PDFService] getMetadata()");

        try (PDDocument doc = loadDocument(pdfFile, password)) {
            PDDocumentInformation info = doc.getDocumentInformation();

            PDFMetadata meta = new PDFMetadata();
            meta.title         = nvl(info.getTitle());
            meta.author        = nvl(info.getAuthor());
            meta.subject       = nvl(info.getSubject());
            meta.creator       = nvl(info.getCreator());
            meta.pageCount     = doc.getNumberOfPages();
            meta.fileSizeBytes = pdfFile.length;
            meta.isEncrypted   = doc.isEncrypted();
            meta.pdfVersion    = String.valueOf(doc.getVersion());
            return meta;

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                PDFPasswordException ex = new PDFPasswordException();
                ex.message = "Mot de passe incorrect ou manquant.";
                throw ex;
            }
            PDFProcessingException ex = new PDFProcessingException();
            ex.message = "Erreur lors de la lecture des métadonnées : " + e.getMessage();
            ex.errorCode = "METADATA_ERROR";
            throw ex;
        }
    }

    /** Null-safe string helper. */
    private String nvl(String s) {
        return s != null ? s : "";
    }
}
