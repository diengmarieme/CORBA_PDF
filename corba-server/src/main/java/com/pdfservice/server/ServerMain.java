package com.pdfservice.server;

import org.omg.CosNaming.*;
import org.omg.CORBA.*;
import org.omg.PortableServer.*;
import PDFService.IPDFService;
import PDFService.IPDFServiceHelper;

public class ServerMain {
    public static void main(String[] args) {
        try {
            ORB orb = ORB.init(args, null);
            POA rootpoa = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootpoa.the_POAManager().activate();

            PDFServiceImpl pdfImpl = new PDFServiceImpl();
            org.omg.CORBA.Object ref = rootpoa.servant_to_reference(pdfImpl);
            IPDFService href = IPDFServiceHelper.narrow(ref);

            org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
            NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
            ncRef.rebind(ncRef.to_name("IPDFService"), href);

            System.out.println("=== Serveur CORBA PDF prêt (Nom: IPDFService) ===");
            orb.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
