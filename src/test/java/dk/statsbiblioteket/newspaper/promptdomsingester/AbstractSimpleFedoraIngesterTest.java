package dk.statsbiblioteket.newspaper.promptdomsingester;

import dk.statsbiblioteket.doms.central.connectors.EnhancedFedora;
import dk.statsbiblioteket.doms.central.connectors.fedora.pidGenerator.PIDGeneratorException;
import org.testng.annotations.Test;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.net.MalformedURLException;

import static org.testng.Assert.assertTrue;

/**
 *
 */
public abstract class AbstractSimpleFedoraIngesterTest {

    protected abstract EnhancedFedora getEnhancedFedora() throws MalformedURLException, JAXBException, PIDGeneratorException;
    String pid;

    /**
     * Test that we can ingest a sample batch. Subclasses of this class should appropriate asserts to test that this
     * test does what it should.
     * @throws Exception
     */
    @Test
    public void testIngest() throws Exception {
        //SimpleFedoraIngester ingester = new SimpleFedoraIngester(getEnhancedFedora(), new String[]{".jp2"}, new String[]{"info:Batch"});
        SimpleFedoraIngester ingester = SimpleFedoraIngester.getNewspaperInstance(getEnhancedFedora());
        File rootTestdataDir = new File(System.getProperty("integration.test.newspaper.testdata"));
        File testRoot = new File(rootTestdataDir, "small-test-batch_contents-included/B400022028241-RT1");
        assertTrue(testRoot.exists(), testRoot.getAbsolutePath() + " does not exist.");
        String rootPid = ingester.ingest(testRoot);
        pid = rootPid;
        System.out.println("Created object tree rooted at " + rootPid);
    }
}