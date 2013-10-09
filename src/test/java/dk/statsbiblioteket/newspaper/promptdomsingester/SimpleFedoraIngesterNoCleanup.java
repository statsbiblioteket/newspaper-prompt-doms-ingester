package dk.statsbiblioteket.newspaper.promptdomsingester;

import dk.statsbiblioteket.doms.central.connectors.EnhancedFedora;
import dk.statsbiblioteket.doms.central.connectors.EnhancedFedoraImpl;
import dk.statsbiblioteket.doms.central.connectors.fedora.pidGenerator.PIDGeneratorException;
import dk.statsbiblioteket.doms.webservices.authentication.Credentials;
import org.testng.annotations.Test;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Properties;

import static org.testng.Assert.assertTrue;

/**
 * Created with IntelliJ IDEA.
 * User: csr
 * Date: 10/9/13
 * Time: 1:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class SimpleFedoraIngesterNoCleanup extends SimpleFedoraIngesterTest {

    @Override
    public EnhancedFedora getEnhancedFedora() throws JAXBException, PIDGeneratorException, MalformedURLException {
        Properties props = new Properties();
        try {
            props.load(new FileReader(new File(System.getProperty("integration.test.newspaper.properties"))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Credentials creds = new Credentials(props.getProperty("fedora.admin.username"), props.getProperty("fedora.admin.password"));
        String fedoraLocation = props.getProperty("doms.server");
        EnhancedFedoraImpl eFedora = new EnhancedFedoraImpl(creds, fedoraLocation, props.getProperty("pidgenerator.location") , null);
        return eFedora;
    }


    public void testIngest() throws Exception {
        //SimpleFedoraIngester ingester = new SimpleFedoraIngester(getEnhancedFedora(), new String[]{".jp2"}, new String[]{"info:Batch"});
        SimpleFedoraIngester ingester = SimpleFedoraIngester.getNewspaperInstance(getEnhancedFedora());
        File rootTestdataDir = new File(System.getProperty("integration.test.newspaper.testdata"));
        File testRoot = new File(rootTestdataDir, "small-test-batch_contents-included/B400022028242-RT3");
        assertTrue(testRoot.exists(), testRoot.getAbsolutePath() + " does not exist.");
        String rootPid = ingester.ingest(testRoot);
        pid = rootPid;
        System.out.println("Created object tree rooted at " + rootPid);
    }

}