package PDFService;

public abstract class IPDFServicePOA
    extends org.omg.PortableServer.Servant
    implements org.omg.CORBA.portable.InvokeHandler {

    public String[] _all_interfaces(org.omg.PortableServer.POA poa, byte[] id) {
        return new String[]{"IDL:PDFService/IPDFService:1.0"};
    }

    public org.omg.CORBA.portable.OutputStream _invoke(
            String method,
            org.omg.CORBA.portable.InputStream _in,
            org.omg.CORBA.portable.ResponseHandler rh) {
        try {
            switch (method) {
                case "ping": {
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_string(ping());
                    return out;
                }
                case "merge": {
                    int len = _in.read_long();
                    byte[][] pdfFiles = new byte[len][];
                    for (int i = 0; i < len; i++) {
                        int l = _in.read_long();
                        pdfFiles[i] = new byte[l];
                        _in.read_octet_array(pdfFiles[i], 0, l);
                    }
                    byte[] result = merge(pdfFiles);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length);
                    out.write_octet_array(result, 0, result.length);
                    return out;
                }
                case "split": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    int p = _in.read_long();
                    SplitResult r = split(f, p);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(r.parts.length);
                    for (byte[] part : r.parts) { out.write_long(part.length); out.write_octet_array(part,0,part.length); }
                    out.write_long(r.partCount);
                    return out;
                }
                case "extractPages": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    int pl = _in.read_long(); int[] pages = new int[pl];
                    for (int i=0;i<pl;i++) pages[i]=_in.read_long();
                    byte[] result = extractPages(f, pages);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "deletePages": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    int pl = _in.read_long(); int[] pages = new int[pl];
                    for (int i=0;i<pl;i++) pages[i]=_in.read_long();
                    byte[] result = deletePages(f, pages);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "encrypt": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    String up = _in.read_string(); String op = _in.read_string();
                    byte[] result = encrypt(f, up, op);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "toImages": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    int dpi = _in.read_long(); String pw = _in.read_string();
                    ImageConversionResult r = toImages(f, dpi, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(r.images.length);
                    for (byte[] img : r.images) { out.write_long(img.length); out.write_octet_array(img,0,img.length); }
                    out.write_long(r.mimeTypes.length);
                    for (String mt : r.mimeTypes) out.write_string(mt);
                    out.write_long(r.pageCount);
                    return out;
                }
                case "extractText": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    String pw = _in.read_string();
                    TextExtractionResult r = extractText(f, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_string(r.fullText);
                    out.write_long(r.pageTexts.length);
                    for (String pt : r.pageTexts) out.write_string(pt);
                    out.write_long(r.pageCount);
                    return out;
                }
                case "createPDF": {
                    String title = _in.read_string();
                    String content = _in.read_string();
                    String author = _in.read_string();
                    byte[] result = createPDF(title, content, author);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "compress": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    CompressionParams p = new CompressionParams();
                    p.dpi = _in.read_long(); p.compressImages = _in.read_boolean(); p.removeMetadata = _in.read_boolean();
                    byte[] result = compress(f, p);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "addWatermark": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    WatermarkParams p = new WatermarkParams();
                    p.text = _in.read_string(); p.fontSize = _in.read_float();
                    p.opacity = _in.read_float(); p.rotationDegrees = _in.read_float();
                    p.colorR = _in.read_long(); p.colorG = _in.read_long(); p.colorB = _in.read_long();
                    p.applyToAllPages = _in.read_boolean();
                    int tl = _in.read_long(); p.targetPages = new int[tl];
                    for (int i=0;i<tl;i++) p.targetPages[i]=_in.read_long();
                    String pw = _in.read_string();
                    byte[] result = addWatermark(f, p, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "rotatePages": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    RotationParams p = new RotationParams();
                    int pl = _in.read_long(); p.pages = new int[pl];
                    for (int i=0;i<pl;i++) p.pages[i]=_in.read_long();
                    p.degrees = _in.read_long(); String pw = _in.read_string();
                    byte[] result = rotatePages(f, p, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result,0,result.length);
                    return out;
                }
                case "extractImages": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    String pw = _in.read_string();
                    EmbeddedImageResult r = extractImages(f, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(r.images.length);
                    for (byte[] img : r.images) { out.write_long(img.length); out.write_octet_array(img,0,img.length); }
                    out.write_long(r.mimeTypes.length);
                    for (String mt : r.mimeTypes) out.write_string(mt);
                    out.write_long(r.imageCount);
                    return out;
                }
                case "getMetadata": {
                    int l = _in.read_long(); byte[] f = new byte[l]; _in.read_octet_array(f,0,l);
                    String pw = _in.read_string();
                    PDFMetadata m = getMetadata(f, pw);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_string(m.title); out.write_string(m.author);
                    out.write_string(m.subject); out.write_string(m.creator);
                    out.write_long(m.pageCount); out.write_long(m.fileSizeBytes);
                    out.write_boolean(m.isEncrypted); out.write_string(m.pdfVersion);
                    return out;
                }
                case "imagesToPdf": {
                    int len = _in.read_long();
                    byte[][] images = new byte[len][];
                    for (int i = 0; i < len; i++) {
                        int l = _in.read_long(); images[i] = new byte[l];
                        _in.read_octet_array(images[i], 0, l);
                    }
                    int mlen = _in.read_long();
                    String[] mimeTypes = new String[mlen];
                    for (int i = 0; i < mlen; i++) mimeTypes[i] = _in.read_string();
                    byte[] result = imagesToPdf(images, mimeTypes);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result, 0, result.length);
                    return out;
                }
                    int mlen = _in.read_long();
                    String[] mimeTypes = new String[mlen];
                    for (int i = 0; i < mlen; i++) mimeTypes[i] = _in.read_string();
                    byte[] result = imagesToPdf(images, mimeTypes);
                    org.omg.CORBA.portable.OutputStream out = rh.createReply();
                    out.write_long(result.length); out.write_octet_array(result, 0, result.length);
                    return out;
                }
                default:
                    throw new org.omg.CORBA.BAD_OPERATION(0, org.omg.CORBA.CompletionStatus.COMPLETED_MAYBE);
            }
        } catch (org.omg.CORBA.SystemException se) {
            throw se;
        } catch (Exception e) {
            throw new org.omg.CORBA.UNKNOWN(e.toString());
        }
    }

    public IPDFService _this() { return IPDFServiceHelper.narrow(super._this_object()); }
    public IPDFService _this(org.omg.CORBA.ORB orb) { return IPDFServiceHelper.narrow(super._this_object(orb)); }

    public abstract byte[] merge(byte[][] pdfFiles) throws PDFProcessingException, InvalidParameterException;
    public abstract SplitResult split(byte[] pdfFile, int pagesPerPart) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract byte[] extractPages(byte[] pdfFile, int[] pages) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract byte[] deletePages(byte[] pdfFile, int[] pages) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract byte[] encrypt(byte[] pdfFile, String userPassword, String ownerPassword) throws PDFProcessingException, InvalidParameterException, PDFEncryptionException;
    public abstract ImageConversionResult toImages(byte[] pdfFile, int dpi, String password) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract TextExtractionResult extractText(byte[] pdfFile, String password) throws PDFProcessingException, PDFPasswordException;
    public abstract byte[] createPDF(String title, String content, String author) throws PDFProcessingException, InvalidParameterException;
    public abstract byte[] compress(byte[] pdfFile, CompressionParams params) throws PDFProcessingException, InvalidParameterException;
    public abstract byte[] addWatermark(byte[] pdfFile, WatermarkParams params, String password) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract byte[] rotatePages(byte[] pdfFile, RotationParams params, String password) throws PDFProcessingException, InvalidParameterException, PDFPasswordException;
    public abstract EmbeddedImageResult extractImages(byte[] pdfFile, String password) throws PDFProcessingException, PDFPasswordException;
    public abstract PDFMetadata getMetadata(byte[] pdfFile, String password) throws PDFProcessingException, PDFPasswordException;
    public abstract String ping();
    public abstract byte[] imagesToPdf(byte[][] images, String[] mimeTypes) throws PDFProcessingException, InvalidParameterException;
}
