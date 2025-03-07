package net.openhft.chronicle.engine.nfs;

import net.openhft.chronicle.engine.api.tree.AssetTree;
import org.dcache.nfs.ExportFile;
import org.dcache.nfs.v3.MountServer;
import org.dcache.nfs.v3.NfsServerV3;
import org.dcache.nfs.v4.DeviceManager;
import org.dcache.nfs.v4.MDSOperationFactory;
import org.dcache.nfs.v4.NFSServerV41;
import org.dcache.nfs.vfs.VirtualFileSystem;
import org.dcache.xdr.OncRpcProgram;
import org.dcache.xdr.OncRpcSvc;
import org.dcache.xdr.OncRpcSvcBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * @author Rob Austin.
 */
public class ChronicleNfsServer {

    static final Logger LOGGER = LoggerFactory.getLogger(ChronicleNfsServer.class);
    public static OncRpcSvc start(AssetTree tree) throws IOException, URISyntaxException {
        return start(tree, "default.exports", false);
    }

    public static OncRpcSvc start(AssetTree tree, String exportsFile, boolean debug) throws IOException, URISyntaxException {
        // create an instance of a filesystem to be exported
        VirtualFileSystem vfs = new ChronicleNfsVirtualFileSystem(tree);
        if (debug)
            vfs = new LoggingVirtualFileSystem(vfs, LOGGER::info);

        // create the RPC service which will handle NFS requests
        OncRpcSvc nfsSvc = new OncRpcSvcBuilder()
                .withPort(2049)
                .withTCP()
                .withAutoPublish()
                .withWorkerThreadIoStrategy()
                .build();

        // specify file with export entries
        URL exports = ClassLoader.getSystemResource("exports");
        if (exports == null)
            exports = ClassLoader.getSystemResource(exportsFile);
        if (exports == null)
            exports = new File(exportsFile).toURL();
        ExportFile exportFile = new ExportFile(exports);

        // create NFS v4.1 server
        NFSServerV41 nfs4 = new NFSServerV41(
                new MDSOperationFactory(),
                new DeviceManager(),
                vfs,
                exportFile);

        // create NFS v3 and mountd servers
        NfsServerV3 nfs3 = new NfsServerV3(exportFile, vfs);
        MountServer mountd = new MountServer(exportFile, vfs);

        // register NFS servers at portmap service
        nfsSvc.register(new OncRpcProgram(100003, 4), nfs4);
        nfsSvc.register(new OncRpcProgram(100003, 3), nfs3);
        nfsSvc.register(new OncRpcProgram(100005, 3), mountd);

        // start RPC service
        try {
            nfsSvc.start();
        } catch (BindException e) {
            LOGGER.error("Unable to bind to the NFS port 2049, is your server already acting as an NFS server?");
            throw e;
        }

        return nfsSvc;
    }

}
