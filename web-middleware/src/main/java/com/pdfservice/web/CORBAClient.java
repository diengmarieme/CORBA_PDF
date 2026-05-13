package com.pdfservice.web;

import PDFService.IPDFService;
import PDFService.IPDFServiceHelper;
import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Properties;
import java.util.logging.Logger;

@Component
public class CORBAClient {
    private static final Logger log = Logger.getLogger(CORBAClient.class.getName());

    @Value("${corba.naming.host:naming}")
    private String namingHost;
    @Value("${corba.naming.port:2809}")
    private String namingPort;
    @Value("${corba.retry.max:20}")
    private int maxRetries;
    @Value("${corba.retry.delay-ms:3000}")
    private long retryDelayMs;

    private ORB orb;
    private IPDFService pdfService;

    @PostConstruct
    public void init() {
        log.info("=== CORBAClient initialisation ===");
        log.info("Naming Service : " + namingHost + ":" + namingPort);

        // Passer InitRef exactement comme le serveur : args à ORB.init()
        String corbaloc = "corbaloc::"+namingHost+":"+namingPort+"/NameService";
        log.info("[CORBA] ORBInitRef.NameService=" + corbaloc);

        String[] orbArgs = new String[]{
            "-ORBInitRef", "NameService=" + corbaloc
        };

        Properties props = new Properties();
        props.setProperty("org.omg.CORBA.ORBClass",          "org.jacorb.orb.ORB");
        props.setProperty("org.omg.CORBA.ORBSingletonClass", "org.jacorb.orb.ORBSingleton");
        props.setProperty("jacorb.log.default.verbosity",    "0");

        orb = ORB.init(orbArgs, props);
        pdfService = resolveWithRetry();
        log.info("=== CORBAClient prêt ===");
    }

    private IPDFService resolveWithRetry() {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[CORBA] Tentative " + attempt + "/" + maxRetries + "...");
                org.omg.CORBA.Object nsRef = orb.resolve_initial_references("NameService");
                NamingContextExt nc = NamingContextExtHelper.narrow(nsRef);
                org.omg.CORBA.Object objRef = nc.resolve_str("IPDFService");
                IPDFService stub = IPDFServiceHelper.narrow(objRef);
                if (stub == null) throw new RuntimeException("narrow() null");
                log.info("[CORBA] Ping: " + stub.ping());
                return stub;
            } catch (org.omg.CORBA.SystemException se) {
                log.warning("[CORBA] Tentative " + attempt + " : "
                    + se.getClass().getSimpleName() + " minor=" + se.minor);
            } catch (Exception e) {
                log.warning("[CORBA] Tentative " + attempt + " : " + e.getMessage());
            }
            if (attempt < maxRetries) {
                try { Thread.sleep(retryDelayMs); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new RuntimeException("IPDFService inaccessible.");
    }

    public IPDFService getService() { return pdfService; }

    @PreDestroy
    public void destroy() { if (orb != null) try { orb.shutdown(true); } catch (Exception e) {} }
}
