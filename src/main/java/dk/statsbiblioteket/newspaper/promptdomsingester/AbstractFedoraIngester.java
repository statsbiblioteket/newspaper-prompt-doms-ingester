package dk.statsbiblioteket.newspaper.promptdomsingester;

import com.google.common.io.CharStreams;
import dk.statsbiblioteket.doms.central.connectors.BackendInvalidCredsException;
import dk.statsbiblioteket.doms.central.connectors.BackendInvalidResourceException;
import dk.statsbiblioteket.doms.central.connectors.BackendMethodFailedException;
import dk.statsbiblioteket.doms.central.connectors.EnhancedFedora;
import dk.statsbiblioteket.doms.central.connectors.fedora.pidGenerator.PIDGeneratorException;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.DataFileNodeBeginsParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeBeginsParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeEndParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.ParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.TreeIterator;
import dk.statsbiblioteket.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class containing the actual logic for traversing the directory tree and ingesting the data to
 * DOMS. Concrete implementing subclasses need only specify the logic for determining which files are
 * data/checksums, as well as providing a connection to Fedora.
 */
public abstract class AbstractFedoraIngester implements IngesterInterface {

    String hasPartRelation = "info:fedora/fedora-system:def/relations-external#hasPart";
    String hasFileRelation = "http://doms.statsbiblioteket.dk/relations/default/0/1/#hasFile";
    private Logger log = LoggerFactory.getLogger(getClass());

    private static String getDatastreamName(String attributeName) throws DomsIngesterException {
        String[] splitName = attributeName.split("\\.");
        if (splitName.length < 2) {
            throw new DomsIngesterException("Cannot find datastream name in " + attributeName);
        }
        return splitName[splitName.length - 2].toUpperCase();
    }

    /**
     * Get an EnhancedFedora object for the repository in which ingest is required.
     *
     * @return the enhanced fedora.
     */
    protected abstract EnhancedFedora getEnhancedFedora();

    /**
     * Returns a list of collections all new objects must belong to. May be empty.
     *
     * @return
     */
    protected abstract List<String> getCollections();

    /**
     * The logic of this method it that it maintains two stacks in parallel to tell it exactly where it is in the
     * directory hierarchy. pathElementStack is simply a stack of directory names and pidStack is a stack of the
     * corresponding DOMS pids. These stacks are pushed for each NodeBegin event and popped for each NodeEnd event.
     * Thus Attributes (ie metadata files) are always ingested as datastreams to the object currently at the top of the
     * stack. The pidStack tells us which object to modify, the pathElementStack tells us how to label the
     * modifications.
     *
     * @param iterator the iterator to parse from
     *
     * @return the doms pid of the root object created
     * @throws DomsIngesterException if failing to read a file or any file is encountered without a checksum
     */
    @Override
    public String ingest(TreeIterator iterator) throws
                                                BackendInvalidCredsException,
                                                BackendMethodFailedException,
                                                PIDGeneratorException,
                                                BackendInvalidResourceException,
                                                DomsIngesterException {
        EnhancedFedora fedora = getEnhancedFedora();
        Deque<String> pidStack = new ArrayDeque<>();

        Map<String,Pair<NodeBeginsParsingEvent,List<String>>> childOf = new HashMap<>();

        String rootPid = null;
        while (iterator.hasNext()) {
            ParsingEvent event = iterator.next();
            switch (event.getType()) {
                case NodeBegin:
                    NodeBeginsParsingEvent nodeBeginsParsingEvent = (NodeBeginsParsingEvent) event;
                    rootPid = handleNodeBegin(fedora, pidStack, rootPid, nodeBeginsParsingEvent,childOf);
                    break;
                case NodeEnd:
                    NodeEndParsingEvent nodeEndParsingEvent = (NodeEndParsingEvent) event;
                    handleNodeEnd(fedora, pidStack, rootPid, nodeEndParsingEvent,childOf);
                    break;
                case Attribute:
                    AttributeParsingEvent attributeParsingEvent = (AttributeParsingEvent) event;
                    handleAttribute(fedora, pidStack, rootPid, attributeParsingEvent);
                    break;
            }
        }
        return rootPid;
    }

    private void handleAttribute(EnhancedFedora fedora,
                                 Deque<String> pidStack,
                                 String rootpid,
                                 AttributeParsingEvent event) throws
                                                              DomsIngesterException,
                                                              BackendInvalidCredsException,
                                                              BackendMethodFailedException,
                                                              BackendInvalidResourceException {
        if (event.getName().endsWith("/contents")) {
            //Possibly check that you are in a DataFileDir before ignoring the event?
            log.debug("Skipping contents attribute.");
        } else {
            String comment = "Adding datastream for " + event.getName() + " == " + pidStack.peekFirst();
            List<String> alternativeIdentifiers = new ArrayList<>();
            alternativeIdentifiers.add(event.getName());
            log.debug(comment);
            String datastreamName = getDatastreamName(event.getName());
            log.debug("Ingesting datastream '" + datastreamName + "'");
            String metadataText;
            try {
                metadataText = CharStreams.toString(new InputStreamReader(event.getData(), "UTF-8"));
            } catch (IOException e) {
                throw new DomsIngesterException(e);
            }
            String checksum = null;
            try {
                checksum = event.getChecksum().toLowerCase();
            } catch (IOException e) {
                throw new DomsIngesterException(e);
            }
            if (checksum != null) {
                fedora.modifyDatastreamByValue(pidStack.peekFirst(),
                                               datastreamName,
                                               metadataText,
                                               checksum,
                                               alternativeIdentifiers,
                                               "Added by ingester.");
            } else {
                fedora.modifyDatastreamByValue(pidStack.peekFirst(),
                                               datastreamName,
                                               metadataText,
                                               alternativeIdentifiers,
                                               "Added by ingester.");

            }
        }
    }

    private void handleNodeEnd(EnhancedFedora fedora,
                               Deque<String> pidStack,
                               String rootPid,
                               ParsingEvent event,
                               Map<String,Pair<NodeBeginsParsingEvent,List<String>>> childOf) throws
                                                                  BackendMethodFailedException,
                                                                  BackendInvalidResourceException,
                                                                  BackendInvalidCredsException {
        String currentNodePid = pidStack.removeFirst();
        if (currentNodePid != null) {
            Pair<NodeBeginsParsingEvent, List<String>> children = childOf.remove(currentNodePid);

            for (String childPid : children.getRight()) {

                String comment = "Added relationship " + currentNodePid + " hasPart " + childPid;
                fedora.addRelation(currentNodePid, null, hasPartRelation, childPid, false, comment);
                log.debug(comment);

                if (children.getLeft() instanceof DataFileNodeBeginsParsingEvent) {
                    comment = "Added relationship " + currentNodePid + " hasFile " + childPid;
                    fedora.addRelation(currentNodePid, null, hasFileRelation, childPid, false, comment);
                    log.debug(comment);
                }
            }
        }


        //Possible publish of object here?
    }

    private String handleNodeBegin(EnhancedFedora fedora,
                                   Deque<String> pidStack,
                                   String rootPid,
                                   NodeBeginsParsingEvent event,
                                   Map<String,Pair<NodeBeginsParsingEvent,List<String>>> childOf) throws
                                                       BackendInvalidCredsException,
                                                       BackendMethodFailedException,
                                                       PIDGeneratorException,
                                                       BackendInvalidResourceException {
        String dir = event.getName();
        String id = "path:" + dir;
        ArrayList<String> oldIds = new ArrayList<>();
        oldIds.add(id);
        String logMessage = "Created object with DC id " + id;
        String currentNodePid = fedora.newEmptyObject(oldIds, getCollections(), logMessage);
        log.debug(logMessage + " / " + currentNodePid);
        String parentPid = pidStack.peekFirst();
        if (rootPid == null) {
            rootPid = currentNodePid;
        }
        pidStack.addFirst(currentNodePid);
        childOf.put(currentNodePid,new Pair<NodeBeginsParsingEvent, List<String>>(event,new ArrayList<String>()));

        if (parentPid != null) {
            childOf.get(parentPid).getRight().add(currentNodePid);
        }

        return rootPid;
    }

}