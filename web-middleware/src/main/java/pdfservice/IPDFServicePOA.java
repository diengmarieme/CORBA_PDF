package PDFService;

public abstract class IPDFServicePOA extends org.omg.PortableServer.Servant 
    implements PDFService.IPDFServiceOperations, org.omg.CORBA.portable.InvokeHandler {

    private static String[] _ids = {"IDL:PDFService/IPDFService:1.0"};

    public String[] _all_interfaces(org.omg.PortableServer.POA poa, byte[] objectId) {
        return (String[])_ids.clone();
    }

    public org.omg.CORBA.portable.OutputStream _invoke(String method, org.omg.CORBA.portable.InputStream in, org.omg.CORBA.portable.ResponseHandler rh) {
        throw new org.omg.CORBA.BAD_OPERATION();
    }
}
