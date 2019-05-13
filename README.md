---
services: active-directory
platforms: Java
author: davmhelm
level: 300
client: Java console daemon
service: azure-storage
endpoint: AAD V1
---

# Quick Start with Azure Storage SDK V10 for Java
# Using Azure AD RBAC Authentication

## About this Sample

### Overview

This sample demonstrates a Java console application calling Azure storage using Azure Active Directory RBAC.
1. The Java web application uses the Active Directory Authentication Library for Java (ADAL4J) to obtain a JWT access token from Azure Active Directory (Azure AD)
2. The access token is used as a bearer token to authenticate the user when calling Azure Storage.

### Scenario

This sample shows how to build a Java daemon app (confidential client) that uses OpenID Connect to authenticate against a single Azure Active Directory (Azure AD) tenant using ADAL4J. For more information about how the protocols work in this scenario and other scenarios, see [Authentication Scenarios for Azure AD](https://docs.microsoft.com/en-us/azure/active-directory/develop/active-directory-authentication-scenarios).

## How to run this project

### Prerequisites

To run this sample, you'll need:

- Working installation of [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and [Maven](https://maven.apache.org/)
- An Internet connection
- An Azure Subscription. If you don't have an Azure subscription, create a [free account](https://azure.microsoft.com/free/?WT.mc_id=A261C142F) before you begin.
- An Azure Active Directory (Azure AD) tenant. For more information on how to get an Azure AD tenant, see [How to get an Azure AD tenant](https://azure.microsoft.com/en-us/documentation/articles/active-directory-howto-tenant/) 
- A service principal in your Azure AD tenant.

### Step 1: Download Java (7 and above) for your platform

To successfully use this sample, you need a working installation of [Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and [Maven](https://maven.apache.org/).

### Step 2:  Clone or download this repository

From your shell or command line:

`git clone https://github.com/davmhelm/storage-blobs-java-v10-quickstart.git`

### Step 3:  Register the sample with your Azure Active Directory tenant

You will be creating a `Service Principal` account for your app. To register the project, you can:

#### Step 3.1: choose the Azure AD tenant where you want to create your applications

As a first step you'll need to:

1. Sign in to the [Azure portal](https://portal.azure.com).
1. On the top bar, click on your account, and then on **Switch Directory**. 
1. Once the *Directory + subscription* pane opens, choose the Active Directory tenant where you wish to register your application, from the *Favorites* or *All Directories* list.
1. Click on **All services** in the left-hand nav, and choose **Azure Active Directory**.

> In the next steps, you might need the tenant name (or directory name) or the tenant ID (or directory ID). These are presented in the **Properties**
of the Azure Active Directory window respectively as *Name* and *Directory ID*

#### Step 3.2 Register the app app (Native-Headless-Application)

1. In the  **Azure Active Directory** pane, click on **App registrations** and choose **New registration**.
1. Enter a friendly name for the application, for example `Storage-Quickstart-Java-Application`.
1. For **Supported Account Types** leave at the default `Accounts in this organizational directory only`.
1. For the *Redirect URI*, select `Web` as the type, and enter `https://localhost`.
1. Click **Register** to create the application.
1. In the succeeding page, Find the values for *Application (client) ID* and *Directory (tenant) ID* and copy them down. You'll need them to configure the properties file for this project.
1. Configure an Application Key (Secret) for your application.
    1. Click on **Certificates & Secrets**.
    1. Under **Client secrets** click **+ New Client Secret**. Enter a description and leave the default expiration of 1 year, then click `Add`.
    1. Copy the *client secret value* you just created to the same location as the *Application (client) ID* and *Directory (tenant) ID*.
1. Configure Permissions for your application. 
    1. Click on **API permissions**, and click **+ Add a Permission**.
    1. Under **Select an API** click **APIs my organization uses**.
    1. Type `Azure Storage` in the search box. Then, click on  **Delegated Permissions** and select **user_impersonation**.
    1. Click on **Add Permissions**.

### Step 4: Create a storage account using the Azure portal

Create a new general-purpose storage account to use for this tutorial. 

*  Go to the [Azure portal](https://portal.azure.com) and log in using your Azure account. 
*  On the Hub menu, select **New** > **Storage** > **Storage account - blob, file, table, queue**. 
*  Enter a name for your storage account. The name must be between 3 and 24 characters in length and may contain numbers and lowercase letters only. It must also be unique. Make note of this name for later.
*  Set `Deployment model` to **Resource manager**.
*  Set `Account kind` to **General purpose (v2)**.
*  Set `Performance` to **Standard**. 
*  Set `Replication` to **Locally Redundant storage (LRS)**.
*  Leave `Secure transfer required` at the default value **Enabled**.
*  Select your subscription. 
*  For `resource group`, create a new one and give it a unique name. 
*  Select the `Location` to use for your storage account.
*  Click **Create** to create your storage account. 

### Step 5: Assign RBAC permissions for service srincipal to storage account using the Azure portal

In the storage account you just created:

* From the left-nav bar, click **Access control (IAM)**.
* Click **+ Add** > **Add role assignment**.
* In the **Role** field, type `Storage Blob Data Contributor`.
* Leave **Assign access to** set to `Azure AD user, group, or service principal`.
* In the **Select** field, type the name of the App Registration you created in **Step 3** above, ex `Storage-Quickstart-Java-Application` and select the member from the filtered list.
* Click **Save**. These settings changes can take up to 5 minutes to take effect.

### Step 6: Set credentials in a properties file

This Quickstart uses a Java properties key/value pair file in order to connect to Azure AD and the Azure Storage account at runtime. An example properties file is included at `secrets/azureauth.properties.template`. 

It is recommended to keep the populated file in a safe place, and ensure it does not get uploaded into source control (using `.gitignore` for example).

With the exception of authorityURL, fill in the rest of the fields in the file with values taken from the instructions above.

If anything you're filling in uses a [special character](https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html#store%28java.io.Writer,%20java.lang.String%29) like `:` or `=`, make sure to escape it first using the escape character `\` (ex. `:` becomes `\:`).

```
# your Azure AD Tenant ID 'aaaa0000-bbbb-1111-cccc-2222dddd3333'
tenantId=

# your Service principal client ID 'aaaa0000-bbbb-1111-cccc-2222dddd3333'
clientId=

# your Service principal secret (AKA client secret, auth key, etc.)
clientSecret=

# Leave this alone unless you're on a different cloud from Commercial
authorityUrl=https\://login.microsoftonline.com

# Storage account name (just the name, app takes care of the rest of the URI)
storageAccountName=
```

### Step 7: Set path to properties file in environment variable

From your shell or command line:

#### Linux
```
export STORAGE_QUICKSTART_AUTH_PROPERTIES="/path/to/azureauth.properties"
```

#### Windows
```
set STORAGE_QUICKSTART_AUTH_PROPERTIES="<driveletter>:\path\to\azureauth.properties"
```

### Step 8: Build and Run

From your shell or command line:

at this point you can build this application using maven: `mvn package`. Then, run the package using `java -jar <packageFile>`. It creates its own file to upload and download, and then cleans up after itself by deleting everything at the end. 

#### Linux
```
mvn package
java -jar target/Quickstart-jar-with-dependencies.jar
```

#### Windows
```
mvn package
java -jar target\Quickstart-jar-with-dependencies.jar
```

## Resources
* [Azure Storage SDK v10 for Java](https://github.com/azure/azure-storage-java/tree/vNext)
* [Azure Storage Java SDK Reference](https://docs.microsoft.com/en-us/java/api/storage/client?view=azure-java-preview)
* [Authenticate with Azure Active Directory to Azure Blob Storage](https://docs.microsoft.com/en-us/azure/storage/common/storage-auth-aad-app)
* [Manage access to Azure Resources using RBAC](https://docs.microsoft.com/en-us/azure/role-based-access-control/role-assignments-portal)

# Disclaimer

The information contained in this quickstart and any accompanying materials (including, but not limited to, scripts, sample code, etc.) are provided "AS-IS" and "WITH ALL FAULTS." Any estimated pricing information is provided solely for demonstration purposes and does not represent final pricing and Microsoft assumes no liability arising from your use of the information. Microsoft makes NO GUARANTEES OR WARRANTIES OF ANY KIND, WHETHER EXPRESSED OR IMPLIED, in providing this information, including any pricing information.
