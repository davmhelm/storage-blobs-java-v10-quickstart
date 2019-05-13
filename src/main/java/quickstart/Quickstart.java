package quickstart;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.naming.ServiceUnavailableException;

import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import com.microsoft.aad.adal4j.UserAssertion;
import com.microsoft.azure.storage.blob.AnonymousCredentials;
import com.microsoft.azure.storage.blob.BlobRange;
import com.microsoft.azure.storage.blob.BlobSASPermission;
import com.microsoft.azure.storage.blob.BlockBlobURL;
import com.microsoft.azure.storage.blob.ContainerURL;
import com.microsoft.azure.storage.blob.ListBlobsOptions;
import com.microsoft.azure.storage.blob.PipelineOptions;
import com.microsoft.azure.storage.blob.SASProtocol;
import com.microsoft.azure.storage.blob.ServiceSASSignatureValues;
import com.microsoft.azure.storage.blob.ServiceURL;
import com.microsoft.azure.storage.blob.StorageURL;
import com.microsoft.azure.storage.blob.TokenCredentials;
import com.microsoft.azure.storage.blob.TransferManager;
import com.microsoft.azure.storage.blob.models.BlobItem;
import com.microsoft.azure.storage.blob.models.ContainerCreateResponse;
import com.microsoft.azure.storage.blob.models.ContainerListBlobFlatSegmentResponse;
import com.microsoft.rest.v2.RestException;
import com.microsoft.rest.v2.util.FlowableUtil;

import io.reactivex.*;
import io.reactivex.Flowable;

public class Quickstart {

    static File createTempFile() throws IOException {

        // Here we are creating a temporary file to use for download and upload to Blob storage
        File sampleFile = null;
        sampleFile = File.createTempFile("sampleFile", ".txt");
        System.out.println(">> Creating a sample file at: " + sampleFile.toString());
        Writer output = new BufferedWriter(new FileWriter(sampleFile));
        output.write("Hello Azure!");
        output.close();

        return sampleFile;
    }

    static void uploadFile(BlockBlobURL blob, File sourceFile) throws IOException {

            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(sourceFile.toPath());

            // Uploading a file to the blobURL using the high-level methods available in TransferManager class
            // Alternatively call the PutBlob/PutBlock low-level methods from BlockBlobURL type
            TransferManager.uploadFileToBlockBlob(fileChannel, blob, 8*1024*1024, null, null)
            .subscribe(response-> {
                System.out.println("Completed upload request.");
                System.out.println(response.response().statusCode());
            });
    }

    static void listBlobs(ContainerURL containerURL) {
        // Each ContainerURL.listBlobsFlatSegment call return up to maxResults (maxResults=10 passed into ListBlobOptions below).
        // To list all Blobs, we are creating a helper static method called listAllBlobs,
    	// and calling it after the initial listBlobsFlatSegment call
        ListBlobsOptions options = new ListBlobsOptions();
        options.withMaxResults(10);

        containerURL.listBlobsFlatSegment(null, options, null).flatMap(containerListBlobFlatSegmentResponse ->
            listAllBlobs(containerURL, containerListBlobFlatSegmentResponse))
            .subscribe(response-> {
                System.out.println("Completed list blobs request.");
                System.out.println(response.statusCode());
            });
    }

    private static Single <ContainerListBlobFlatSegmentResponse> listAllBlobs(ContainerURL url, ContainerListBlobFlatSegmentResponse response) {
        // Process the blobs returned in this result segment (if the segment is empty, blobs() will be null.
        if (response.body().segment() != null) {
            for (BlobItem b : response.body().segment().blobItems()) {
                String output = "Blob name: " + b.name();
                if (b.snapshot() != null) {
                    output += ", Snapshot: " + b.snapshot();
                }
                System.out.println(output);
            }
        }
        else {
            System.out.println("There are no more blobs to list off.");
        }

        // If there is not another segment, return this response as the final response.
        if (response.body().nextMarker() == null) {
            return Single.just(response);
        } else {
            /*
            IMPORTANT: ListBlobsFlatSegment returns the start of the next segment; you MUST use this to get the next
            segment (after processing the current result segment
            */

            String nextMarker = response.body().nextMarker();

            /*
            The presence of the marker indicates that there are more blobs to list, so we make another call to
            listBlobsFlatSegment and pass the result through this helper function.
            */

            return url.listBlobsFlatSegment(nextMarker, new ListBlobsOptions().withMaxResults(10), null)
                    .flatMap(containersListBlobFlatSegmentResponse ->
                            listAllBlobs(url, containersListBlobFlatSegmentResponse));
        }
    }

    static void deleteBlob(BlockBlobURL blobURL) {
        // Delete the blob
        blobURL.delete(null, null, null)
        .subscribe(
            response -> System.out.println(">> Blob deleted: " + blobURL),
            error -> System.out.println(">> An error encountered during deleteBlob: " + error.getMessage()));
    }

    static void getBlob(BlockBlobURL blobURL, File sourceFile) throws IOException {
        AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(sourceFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        TransferManager.downloadBlobToFile(fileChannel, blobURL, null, null)
        .subscribe(response-> {
            System.out.println("Completed download request.");
            System.out.println("The blob was downloaded to " + sourceFile.getAbsolutePath());
        });
    }

    private static AuthenticationResult getAccessTokenFromServicePrincipal(String authority,
            String resourceUri, String clientId, String clientSecret) throws Exception {
        AuthenticationContext context;
        AuthenticationResult result;
        ExecutorService service = null;
        try {
            service = Executors.newFixedThreadPool(1);
            context = new AuthenticationContext(authority, false, service);
            ClientCredential myClientCredential = new ClientCredential(clientId, clientSecret);
            Future<AuthenticationResult> future = context.acquireToken(resourceUri, myClientCredential, null);
            result = future.get();
        } finally {
            service.shutdown();
        }

        if (result == null) {
            throw new ServiceUnavailableException(
                    "authentication result was null");
        }
        return result;
    }


    public static void main(String[] args) throws java.lang.Exception{

        ContainerURL containerURL;

        // Creating a sample file to use in the sample
        File sampleFile = null;

        Properties prop = new Properties();
        String AUTHORITY_URI = "";
        String CLIENT_ID = "";
        String CLIENT_SECRET = "";
        String AZURE_STORAGE_ACCOUNT = "";
        final String RESOURCE_URI = "https://storage.azure.com";

        try {
            // Load configuration from properties file
            InputStream propfileInput = new FileInputStream(System.getenv("STORAGE_QUICKSTART_AUTH_PROPERTIES"));
            prop.load(propfileInput);
            //prop.forEach((k, v) -> System.out.println("Key : " + k + ", Value : " + v));
            AUTHORITY_URI = prop.getProperty("authorityUrl") + "/" + prop.getProperty("tenantId") + "/";
            CLIENT_ID = prop.getProperty("clientId");
            CLIENT_SECRET = prop.getProperty("clientSecret");
            AZURE_STORAGE_ACCOUNT = prop.getProperty("storageAccountName");
            propfileInput.close();
            //final String RESOURCE_URI = "https://storage.azure.com";
        } catch (Exception e) {
            System.out.println("Problem found with properties file. Check your path and format for \n" +
                    "environment variable \"STORAGE_QUICKSTART_AUTH_PROPERTIES\" and try again.");
            e.printStackTrace();
            System.exit(-1);
        }

        try {
            // Prepare demo file
            sampleFile = createTempFile();
            File downloadedFile = File.createTempFile("downloadedFile", ".txt");

            // Authenticate to Azure Storage via AAD Service Principal
            AuthenticationResult result = getAccessTokenFromServicePrincipal(AUTHORITY_URI, RESOURCE_URI, CLIENT_ID, CLIENT_SECRET);
            TokenCredentials creds = new TokenCredentials(result.getAccessToken());
                        
            // We are using a default pipeline here, you can learn more about it at https://github.com/Azure/azure-storage-java/wiki/Azure-Storage-Java-V10-Overview
            final ServiceURL serviceURL = new ServiceURL(new URL("https://" + AZURE_STORAGE_ACCOUNT + ".blob.core.windows.net"), StorageURL.createPipeline(creds, new PipelineOptions()));
            
            // Let's create a container using a blocking call to Azure Storage
            // If container exists, we'll catch and continue
            String containerName = "quickstart";
            containerURL = serviceURL.createContainerURL(containerName);

            try {
                ContainerCreateResponse response = containerURL.create(null, null, null).blockingGet();
                System.out.println("Container Create Response was " + response.statusCode());
            } catch (RestException e){
                if (e instanceof RestException && ((RestException)e).response().statusCode() != 409) {
                    throw e;
                } else {
                    System.out.println("quickstart container already exists, resuming...");
                }
            }

            // Create a BlockBlobURL to run operations on Blobs
            String blobName = "SampleBlob.txt";
            final BlockBlobURL blobURL = containerURL.createBlockBlobURL(blobName);

            // Listening for commands from the console
            System.out.println("Enter a command");
            System.out.println("(P)utBlob | (L)istBlobs | (G)etBlob | (D)eleteBlobs | (E)xitSample"); // + " | (C)reateSasSignature"
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {

                System.out.println("# Enter a command : ");
                String input = reader.readLine();

                switch(input.toUpperCase()){
                    case "P":
                        System.out.println("Uploading the sample file into the container: " + containerURL );
                        uploadFile(blobURL, sampleFile);
                        break;
                    case "L":
                        System.out.println("Listing blobs in the container: " + containerURL );
                        listBlobs(containerURL);
                        break;
                    case "G":
                        System.out.println("Get the blob: " + blobURL.toString() );
                        getBlob(blobURL, downloadedFile);
                        break;
                    case "D":
                        System.out.println("Delete the blob: " + blobURL.toString() );
                        deleteBlob(blobURL);
                        System.out.println();
                        break;
                    case "E":
                        System.out.println("Cleaning up the sample and exiting!");
                        containerURL.delete(null, null).blockingGet();
                        downloadedFile.delete();
                        System.exit(0);
                        break;
// This doesn't work because SAS signatures are keyed off the storage account shared keys
//                  case "C":
//                      System.out.println("Uploading the sample file into the container: " + containerURL );
//                      uploadFile(blobURL, sampleFile);
//
//                      // Based on Sample Code here:
//                      // https://docs.microsoft.com/en-us/java/api/com.microsoft.azure.storage.blob.servicesassignaturevalues?view=azure-java-stable#sample-code
//                      
//                      /*
//                       * Actions taken by this block are done already.
//                       * We're not using a snapshot ID here.
//
//                      // Use your Storage account's name and key to create a credential object; this is required to sign a SAS.
//                      credential = new SharedKeyCredentials(getAccountName(), getAccountKey());
//
//                      // This is the name of the container and blob that we're creating a SAS to.
//                      String containerName = "mycontainer"; // Container names require lowercase.
//                      String blobName = "HelloWorld.txt"; // Blob names can be mixed case.
//                      String snapshotId = "2018-01-01T00:00:00.0000000Z"; // SAS can be restricted to a specific snapshot
//                      
//                      */
//
//                      /*
//                      Set the desired SAS signature values and sign them with the shared key credentials to get the SAS query
//                      parameters.
//                      */
//                      ServiceSASSignatureValues values = new ServiceSASSignatureValues()
//                              .withProtocol(SASProtocol.HTTPS_ONLY) // Users MUST use HTTPS (not HTTP).
//                              .withExpiryTime(OffsetDateTime.now().plusDays(2)) // 2 days before expiration.
//                              .withContainerName(containerName)
//                              .withBlobName(blobName);
//                              //.withSnapshotId(snapshotId);
//
//                      /*
//                      To produce a container SAS (as opposed to a blob SAS), assign to Permissions using ContainerSASPermissions, and
//                      make sure the blobName and snapshotId fields are null (the default).
//                      */
//                      BlobSASPermission permission = new BlobSASPermission()
//                              .withRead(true)
//                              .withAdd(true)
//                              .withWrite(true);
//                      values.withPermissions(permission.toString());
//                      
//                      SASQueryParameters serviceParams = values.generateSASQueryParameters(creds);
//
//                      // Calling encode will generate the query string.
//                      String encodedParams = serviceParams.encode();
//                      // Colons are not safe characters in a URL; they must be properly encoded.
//                      //snapshotId = snapshotId.replace(":", "%3A");
//
//                      String urlToSendToSomeone = String.format(Locale.ROOT, "https://%s.blob.core.windows.net/%s/%s?%s&%s",
//                              getAccountName(), containerName, blobName, encodedParams);
//                      // At this point, you can send the urlToSendSomeone to someone via email or any other mechanism you choose.
//
//                      // ***************************************************************************************************
//
//                      // When someone receives the URL, the access the SAS-protected resource with code like this:
//                      URL u = new URL(urlToSendToSomeone);
//
//                      /*
//                      Create a BlobURL object that wraps the blobURL (and its SAS) and a pipeline. When using SAS URLs,
//                      AnonymousCredentials are required.
//                      */
//                      BlockBlobURL bURL = new BlockBlobURL(u,
//                              StorageURL.createPipeline(new AnonymousCredentials(), new PipelineOptions()));
//                      // Now, you can use this blobURL just like any other to make requests of the resource.
//                      break; 
                    default:
                        break;
                }
            }
        } catch (RestException e){
            System.out.println("Service error returned: " + e.response().statusCode() );
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
